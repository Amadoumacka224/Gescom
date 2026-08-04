-- =====================================================================================
--  GESCOM — Dump SQL complet (structure + données d'initialisation)
-- =====================================================================================
--
--  SGBD cible        : PostgreSQL 13 ou supérieur (testé sur PostgreSQL 17)
--  Encodage attendu  : UTF8
--  Schéma            : public
--  Application       : com.gescom.backend (Spring Boot 3.2 / Hibernate 6)
--
--  ── Restauration ──────────────────────────────────────────────────────────────────
--
--    createdb -U postgres -E UTF8 GESCOM_2
--    psql -U postgres -d GESCOM_2 -v ON_ERROR_STOP=1 -f db/gescom_dump.sql
--
--  Le fichier est intégralement encadré par BEGIN / COMMIT : en cas d'erreur, rien
--  n'est écrit. Il ne contient volontairement AUCUN `DROP` actif — exécuté sur une base
--  déjà peuplée, il échoue sur le premier `CREATE TABLE` et laisse les données intactes.
--  Pour repartir de zéro, décommenter la section « RÉINITIALISATION » ci-dessous.
--
--  ── Cohérence avec Hibernate ─────────────────────────────────────────────────────
--
--  Le schéma est piloté par `spring.jpa.hibernate.ddl-auto=update` (aucune migration
--  Flyway/Liquibase dans le projet). Ce dump reproduit donc au caractère près ce que
--  Hibernate génère à partir des entités : mêmes types, mêmes longueurs, mêmes
--  contraintes CHECK d'énumération, et surtout **mêmes noms de contraintes de clé
--  étrangère** (les identifiants `fk<hash>` ci-dessous sont ceux calculés par Hibernate).
--  Ne pas les renommer : au démarrage suivant, `ddl-auto=update` ne les retrouverait
--  plus et créerait des contraintes en double.
--
--  Les index `idx_*` de la section 5, en revanche, sont des ajouts de ce dump. Hibernate
--  ne les crée pas et ne les supprime pas : ils sont sans effet de bord.
--
--  ── Vues / déclencheurs / procédures stockées ────────────────────────────────────
--
--  Il n'y en a aucun, et c'est intentionnel : toute la logique métier (machine à états
--  des commandes, calcul des totaux et de la TVA, décrément de stock sous verrou
--  pessimiste, piste d'audit) vit dans la couche service Java. Créer des objets SQL
--  parallèles dupliquerait ces règles hors du périmètre géré par Hibernate. Les seuls
--  objets non-table du schéma sont les séquences d'identité (une par table, créées via
--  `bigserial`), remises à niveau en section 7.
--
-- =====================================================================================


-- =====================================================================================
--  0. RÉINITIALISATION (DÉSACTIVÉE — DESTRUCTIF)
-- =====================================================================================
--  À décommenter uniquement pour réinstaller une base de développement existante.
--  Supprime toutes les données de l'application sans confirmation.
--
-- DROP TABLE IF EXISTS activity_logs   CASCADE;
-- DROP TABLE IF EXISTS settings        CASCADE;
-- DROP TABLE IF EXISTS stock_movements CASCADE;
-- DROP TABLE IF EXISTS payments        CASCADE;
-- DROP TABLE IF EXISTS invoices        CASCADE;
-- DROP TABLE IF EXISTS deliveries      CASCADE;
-- DROP TABLE IF EXISTS order_items     CASCADE;
-- DROP TABLE IF EXISTS orders          CASCADE;
-- DROP TABLE IF EXISTS products        CASCADE;
-- DROP TABLE IF EXISTS clients         CASCADE;
-- DROP TABLE IF EXISTS categories      CASCADE;
-- DROP TABLE IF EXISTS users           CASCADE;


SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET default_table_access_method = heap;

BEGIN;

SET search_path = public;


-- =====================================================================================
--  1. TABLES — RÉFÉRENTIELS
-- =====================================================================================

-- Utilisateurs. Sert aussi de principal Spring Security (User implements UserDetails).
-- `password` contient un hash BCrypt ; `active = false` interdit la connexion.
CREATE TABLE users (
    id          bigserial     NOT NULL,
    username    varchar(50)   NOT NULL,
    email       varchar(100)  NOT NULL,
    password    varchar(255)  NOT NULL,
    first_name  varchar(100)  NOT NULL,
    last_name   varchar(100)  NOT NULL,
    phone       varchar(20),
    role        varchar(255)  NOT NULL,
    active      boolean       NOT NULL,
    created_at  timestamp(6) without time zone NOT NULL,
    updated_at  timestamp(6) without time zone NOT NULL,
    CONSTRAINT users_pkey        PRIMARY KEY (id),
    CONSTRAINT users_username_key UNIQUE (username),
    CONSTRAINT users_email_key    UNIQUE (email),
    CONSTRAINT users_role_check   CHECK (role IN ('ADMIN', 'CAISSIER'))
);

COMMENT ON TABLE  users          IS 'Comptes applicatifs (authentification JWT). Rôles : ADMIN, CAISSIER.';
COMMENT ON COLUMN users.password IS 'Hash BCrypt — jamais de mot de passe en clair.';
COMMENT ON COLUMN users.active   IS 'Faux = compte désactivé, connexion refusée (User.isEnabled()).';


-- Catégories de produits. `active = false` retire la catégorie de la sélection sans
-- casser les produits déjà rattachés.
CREATE TABLE categories (
    id          bigserial     NOT NULL,
    name        varchar(100)  NOT NULL,
    description varchar(500),
    code        varchar(50),
    active      boolean       NOT NULL,
    created_at  timestamp(6) without time zone NOT NULL,
    updated_at  timestamp(6) without time zone NOT NULL,
    CONSTRAINT categories_pkey     PRIMARY KEY (id),
    CONSTRAINT categories_name_key UNIQUE (name)
);

COMMENT ON TABLE categories IS 'Classement du catalogue produits.';


-- Clients. `type` distingue les particuliers des entreprises.
CREATE TABLE clients (
    id          bigserial     NOT NULL,
    first_name  varchar(100)  NOT NULL,
    last_name   varchar(100)  NOT NULL,
    email       varchar(100),
    phone       varchar(20)   NOT NULL,
    address     varchar(255),
    city        varchar(100),
    postal_code varchar(20),
    country     varchar(100),
    company     varchar(50),
    type        varchar(255)  NOT NULL,
    active      boolean       NOT NULL,
    created_at  timestamp(6) without time zone NOT NULL,
    updated_at  timestamp(6) without time zone NOT NULL,
    CONSTRAINT clients_pkey      PRIMARY KEY (id),
    CONSTRAINT clients_email_key UNIQUE (email),
    CONSTRAINT clients_type_check CHECK (type IN ('PARTICULIER', 'ENTREPRISE'))
);

COMMENT ON TABLE  clients       IS 'Destinataires des commandes. Une commande sans client est une vente de passage.';
COMMENT ON COLUMN clients.email IS 'Unique lorsqu''il est renseigné ; NULL autorisé (plusieurs clients sans email).';


-- Produits du catalogue. `stock_quantity` est la quantité en stock courante ; elle est
-- toujours le reflet du dernier mouvement enregistré dans stock_movements.
CREATE TABLE products (
    id              bigserial      NOT NULL,
    code            varchar(50)    NOT NULL,
    name            varchar(200)   NOT NULL,
    description     text,
    purchase_price  numeric(10,2)  NOT NULL,
    selling_price   numeric(10,2)  NOT NULL,
    category_id     bigint,
    unit            varchar(50),
    stock_quantity  integer        NOT NULL,
    min_stock_alert integer        NOT NULL,
    barcode         varchar(50),
    image_url       text,
    active          boolean        NOT NULL,
    created_at      timestamp(6) without time zone NOT NULL,
    updated_at      timestamp(6) without time zone NOT NULL,
    CONSTRAINT products_pkey     PRIMARY KEY (id),
    CONSTRAINT products_code_key UNIQUE (code)
);

COMMENT ON COLUMN products.stock_quantity  IS 'Stock courant. Décrémenté à la confirmation de commande, sous verrou pessimiste.';
COMMENT ON COLUMN products.min_stock_alert IS 'Seuil déclenchant l''alerte de stock bas.';


-- Paramètres de l'application : singleton persistant (une seule ligne, créée par
-- SettingsService au premier accès si la table est vide).
CREATE TABLE settings (
    id                   bigserial     NOT NULL,
    language             varchar(255)  NOT NULL,
    currency             varchar(255)  NOT NULL,
    timezone             varchar(255)  NOT NULL,
    date_format          varchar(255)  NOT NULL,
    company_name         varchar(255)  NOT NULL,
    company_email        varchar(255),
    company_phone        varchar(255),
    company_address      varchar(255),
    company_city         varchar(255),
    company_postal_code  varchar(255),
    company_country      varchar(255),
    company_tax_id       varchar(255),
    company_iban         varchar(255),
    company_bic          varchar(255),
    tax_rate             double precision NOT NULL,
    invoice_prefix       varchar(255)  NOT NULL,
    invoice_number_start integer       NOT NULL,
    payment_terms        integer       NOT NULL,
    footer_text          text,
    notifications        boolean       NOT NULL,
    email_notifications  boolean       NOT NULL,
    order_notifications  boolean       NOT NULL,
    stock_alerts         boolean       NOT NULL,
    low_stock_threshold  integer       NOT NULL,
    theme                varchar(255)  NOT NULL,
    created_at           timestamp(6) without time zone NOT NULL,
    updated_at           timestamp(6) without time zone NOT NULL,
    CONSTRAINT settings_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE  settings                IS 'Singleton de configuration — une seule ligne attendue (SettingsService lit la plus petite id).';
COMMENT ON COLUMN settings.company_tax_id IS 'Numéro d''entreprise / TVA, format belge BE0XXX.XXX.XXX.';


-- =====================================================================================
--  2. TABLES — CYCLE DE VIE COMMERCIAL
-- =====================================================================================
--  Flux linéaire strict, imposé par Order.OrderStatus.ALLOWED_TRANSITIONS :
--
--      PENDING → CONFIRMED → INVOICED → DELIVERED
--         ↓          ↓           ↓
--              CANCELED (terminal ; DELIVERED est également terminal)
--
--  Une livraison ne peut donc pas exister avant la facturation.
-- =====================================================================================

-- Commandes. `total_amount` est le total HT des lignes (remises de ligne déjà déduites),
-- `discount` la remise commerciale globale en euros, `final_amount` le net à facturer.
CREATE TABLE orders (
    id           bigserial      NOT NULL,
    order_number varchar(50)    NOT NULL,
    client_id    bigint,
    user_id      bigint         NOT NULL,
    total_amount numeric(10,2)  NOT NULL,
    discount     numeric(10,2)  NOT NULL,
    final_amount numeric(10,2)  NOT NULL,
    status       varchar(255)   NOT NULL,
    notes        varchar(500),
    created_at   timestamp(6) without time zone NOT NULL,
    updated_at   timestamp(6) without time zone NOT NULL,
    CONSTRAINT orders_pkey             PRIMARY KEY (id),
    CONSTRAINT orders_order_number_key UNIQUE (order_number),
    CONSTRAINT orders_status_check     CHECK (status IN ('PENDING', 'CONFIRMED', 'INVOICED', 'DELIVERED', 'CANCELED'))
);

COMMENT ON COLUMN orders.client_id    IS 'NULL pour une vente de passage (client non enregistré).';
COMMENT ON COLUMN orders.user_id      IS 'Auteur de la commande (Order.createdBy).';
COMMENT ON COLUMN orders.total_amount IS 'Total HT des lignes, remises de ligne déduites. Base de calcul du sous-total de la facture.';
COMMENT ON COLUMN orders.discount     IS 'Remise globale en euros (les remises de ligne, elles, sont en %).';
COMMENT ON COLUMN orders.final_amount IS 'total_amount − discount. Assiette exacte de la TVA appliquée par InvoiceService.';


-- Lignes de commande. Le prix unitaire est figé à la création pour que la commande ne
-- soit pas affectée par les changements de tarif ultérieurs.
CREATE TABLE order_items (
    id          bigserial      NOT NULL,
    order_id    bigint         NOT NULL,
    product_id  bigint         NOT NULL,
    quantity    integer        NOT NULL,
    unit_price  numeric(10,2)  NOT NULL,
    total_price numeric(10,2)  NOT NULL,
    discount    numeric(10,2),
    CONSTRAINT order_items_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN order_items.unit_price  IS 'Prix de vente copié du produit au moment de la vente.';
COMMENT ON COLUMN order_items.discount    IS 'Remise de ligne, en pourcentage, bornée à [0, 100].';
COMMENT ON COLUMN order_items.total_price IS 'round(unit_price × quantity × (100 − discount) / 100, 2).';


-- Bons de livraison. Une seule livraison par commande (unicité sur order_id), créée
-- uniquement à partir d'une commande INVOICED. Machine à états PENDING → DELIVERED.
CREATE TABLE deliveries (
    id                   bigserial     NOT NULL,
    delivery_number      varchar(50)   NOT NULL,
    order_id             bigint        NOT NULL,
    delivery_address     varchar(255)  NOT NULL,
    delivery_city        varchar(100),
    delivery_postal_code varchar(20),
    delivery_country     varchar(100),
    contact_name         varchar(100),
    contact_phone        varchar(20),
    status               varchar(255)  NOT NULL,
    scheduled_date       timestamp(6) without time zone NOT NULL,
    delivered_date       timestamp(6) without time zone,
    delivered_by         varchar(100),
    notes                varchar(500),
    created_at           timestamp(6) without time zone NOT NULL,
    updated_at           timestamp(6) without time zone NOT NULL,
    CONSTRAINT deliveries_pkey                PRIMARY KEY (id),
    CONSTRAINT deliveries_delivery_number_key UNIQUE (delivery_number),
    CONSTRAINT deliveries_order_id_key        UNIQUE (order_id),
    CONSTRAINT deliveries_status_check        CHECK (status IN ('PENDING', 'DELIVERED'))
);

COMMENT ON COLUMN deliveries.order_id IS 'Unique : une commande ne porte qu''une livraison.';
COMMENT ON COLUMN deliveries.status   IS 'Passer à DELIVERED fait basculer la commande INVOICED → DELIVERED.';


-- Factures. Une par commande (unicité sur order_id). Décomposition du montant :
-- subtotal → discount → TVA sur le net après remise → total TTC.
CREATE TABLE invoices (
    id               bigserial      NOT NULL,
    invoice_number   varchar(50)    NOT NULL,
    order_id         bigint         NOT NULL,
    delivery_id      bigint,
    invoice_date     date           NOT NULL,
    due_date         date           NOT NULL,
    subtotal         numeric(10,2)  NOT NULL,
    discount         numeric(10,2),
    tax_rate         numeric(5,2),
    tax_amount       numeric(10,2),
    total_amount     numeric(10,2)  NOT NULL,
    paid_amount      numeric(10,2),
    remaining_amount numeric(10,2),
    status           varchar(255)   NOT NULL,
    payment_method   varchar(255)   NOT NULL,
    payment_date     date,
    notes            varchar(500),
    created_at       timestamp(6) without time zone NOT NULL,
    updated_at       timestamp(6) without time zone NOT NULL,
    CONSTRAINT invoices_pkey               PRIMARY KEY (id),
    CONSTRAINT invoices_invoice_number_key UNIQUE (invoice_number),
    CONSTRAINT invoices_order_id_key       UNIQUE (order_id),
    CONSTRAINT invoices_delivery_id_key    UNIQUE (delivery_id),
    CONSTRAINT invoices_status_check        CHECK (status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'CANCELED')),
    CONSTRAINT invoices_payment_method_check CHECK (payment_method IN ('CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'CHECK', 'MOBILE_PAYMENT'))
);

COMMENT ON COLUMN invoices.subtotal         IS 'Reprise de orders.total_amount (montant brut, avant remise globale).';
COMMENT ON COLUMN invoices.discount         IS 'Remise de la commande, cumulée à une éventuelle remise consentie à la facturation.';
COMMENT ON COLUMN invoices.tax_amount       IS 'round((subtotal − discount) × tax_rate / 100, 2).';
COMMENT ON COLUMN invoices.total_amount     IS 'subtotal − discount + tax_amount.';
COMMENT ON COLUMN invoices.remaining_amount IS 'total_amount − paid_amount, recalculé par les callbacks JPA.';
COMMENT ON COLUMN invoices.payment_date     IS 'Renseignée au règlement complet uniquement ; NULL sur un paiement partiel.';
COMMENT ON COLUMN invoices.delivery_id      IS 'Réservé ; non alimenté par le flux applicatif actuel (le lien passe par order_id).';


-- Transactions carte du terminal de paiement (Stripe, mode test ou simulé).
-- Journal de ce qu'a répondu le prestataire ; l'encaissement lui-même est porté par
-- invoices.paid_amount. Aucun secret n'est persisté (le client_secret est @Transient).
CREATE TABLE payments (
    id              bigserial      NOT NULL,
    invoice_id      bigint         NOT NULL,
    provider        varchar(20)    NOT NULL,
    intent_id       varchar(100)   NOT NULL,
    amount          numeric(10,2)  NOT NULL,
    currency        varchar(3)     NOT NULL,
    status          varchar(30)    NOT NULL,
    card_brand      varchar(30),
    card_last4      varchar(4),
    failure_message varchar(255),
    simulated       boolean        NOT NULL,
    created_at      timestamp(6) without time zone NOT NULL,
    confirmed_at    timestamp(6) without time zone,
    CONSTRAINT payments_pkey          PRIMARY KEY (id),
    CONSTRAINT payments_intent_id_key UNIQUE (intent_id),
    CONSTRAINT payments_status_check  CHECK (status IN ('REQUIRES_CONFIRMATION', 'SUCCEEDED', 'FAILED', 'CANCELED'))
);

COMMENT ON COLUMN payments.intent_id IS 'Identifiant d''intention de paiement Stripe (pi_...).';
COMMENT ON COLUMN payments.simulated IS 'Vrai si la transaction a été jouée par la passerelle simulée (aucun appel réseau).';


-- =====================================================================================
--  3. TABLES — TRAÇABILITÉ
-- =====================================================================================
--  Registres à croissance non bornée : ce sont les deux seules listes paginées côté API.
-- =====================================================================================

-- Historique des mouvements de stock. Chaque ligne fige le stock avant et après, ce qui
-- permet d'auditer et d'annuler un mouvement sans recalculer toute la chaîne.
CREATE TABLE stock_movements (
    id             bigserial      NOT NULL,
    product_id     bigint         NOT NULL,
    type           varchar(255)   NOT NULL,
    quantity       integer        NOT NULL,
    previous_stock integer        NOT NULL,
    new_stock      integer        NOT NULL,
    unit_cost      numeric(10,2),
    reason         varchar(500),
    reference      varchar(100),
    user_id        bigint,
    created_at     timestamp(6) without time zone NOT NULL,
    CONSTRAINT stock_movements_pkey       PRIMARY KEY (id),
    CONSTRAINT stock_movements_type_check CHECK (type IN ('STOCK_IN', 'STOCK_OUT', 'ADJUSTMENT', 'RETURN', 'DAMAGE', 'TRANSFER'))
);

COMMENT ON COLUMN stock_movements.quantity  IS 'Valeur absolue de la variation ; le sens est porté par `type`.';
COMMENT ON COLUMN stock_movements.reference IS 'Pièce d''origine (numéro de commande, référence d''inventaire...).';
COMMENT ON COLUMN stock_movements.user_id   IS 'NULL si le mouvement a été enregistré hors contexte authentifié.';


-- Piste d'audit : une ligne par action significative. Le couple entity/entity_id permet
-- de tracer n'importe quel objet sans relation directe.
CREATE TABLE activity_logs (
    id          bigserial     NOT NULL,
    user_id     bigint        NOT NULL,
    action_type varchar(255)  NOT NULL,
    entity      varchar(100)  NOT NULL,
    entity_id   bigint,
    description varchar(500),
    ip_address  varchar(50),
    details     text,
    created_at  timestamp(6) without time zone NOT NULL,
    CONSTRAINT activity_logs_pkey             PRIMARY KEY (id),
    CONSTRAINT activity_logs_action_type_check CHECK (action_type IN ('CREATE', 'UPDATE', 'DELETE', 'VIEW', 'LOGIN', 'LOGOUT', 'SALE', 'PAYMENT', 'STOCK_IN', 'STOCK_OUT', 'EXPORT', 'IMPORT'))
);

COMMENT ON TABLE activity_logs IS 'Journal d''audit. L''échec d''une écriture ici ne doit jamais interrompre une opération métier.';


-- =====================================================================================
--  4. CLÉS ÉTRANGÈRES
-- =====================================================================================
--  Noms générés par Hibernate — À NE PAS RENOMMER (cf. en-tête du fichier).
--  Aucune action ON DELETE : les suppressions en cascade sont gérées côté JPA
--  (cascade = ALL + orphanRemoval sur Order.items).
-- =====================================================================================

ALTER TABLE products
    ADD CONSTRAINT fkog2rp4qthbtt2lfyhfo32lsw9 FOREIGN KEY (category_id) REFERENCES categories (id);

ALTER TABLE orders
    ADD CONSTRAINT fkm2dep9derpoaehshbkkatam3v FOREIGN KEY (client_id) REFERENCES clients (id);
ALTER TABLE orders
    ADD CONSTRAINT fk32ql8ubntj5uh44ph9659tiih FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE order_items
    ADD CONSTRAINT fkbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES orders (id);
ALTER TABLE order_items
    ADD CONSTRAINT fkocimc7dtr037rh4ls4l95nlfi FOREIGN KEY (product_id) REFERENCES products (id);

ALTER TABLE deliveries
    ADD CONSTRAINT fk7isx0rnbgqr1dcofd5putl6jw FOREIGN KEY (order_id) REFERENCES orders (id);

ALTER TABLE invoices
    ADD CONSTRAINT fk4ko3y00tkkk2ya3p6wnefjj2f FOREIGN KEY (order_id) REFERENCES orders (id);
ALTER TABLE invoices
    ADD CONSTRAINT fkej0v3g5mdeqisk7vps25662s4 FOREIGN KEY (delivery_id) REFERENCES deliveries (id);

ALTER TABLE payments
    ADD CONSTRAINT fkrbqec6be74wab8iifh8g3i50i FOREIGN KEY (invoice_id) REFERENCES invoices (id);

ALTER TABLE stock_movements
    ADD CONSTRAINT fkjcaag8ogfjxpwmqypi1wfdaog FOREIGN KEY (product_id) REFERENCES products (id);
ALTER TABLE stock_movements
    ADD CONSTRAINT fkfqq1iu0gt0la6ruk2o62bry5v FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE activity_logs
    ADD CONSTRAINT fk5bm1lt4f4eevt8lv2517soakd FOREIGN KEY (user_id) REFERENCES users (id);


-- =====================================================================================
--  5. INDEX
-- =====================================================================================
--  Ajouts propres à ce dump (Hibernate n'indexe que les PK et les contraintes UNIQUE).
--  Couvrent les colonnes de clé étrangère et les critères de filtre réellement utilisés
--  par les méthodes de repository.
-- =====================================================================================

CREATE INDEX idx_users_role                  ON users (role);
CREATE INDEX idx_users_active                ON users (active);

CREATE INDEX idx_categories_active           ON categories (active);

CREATE INDEX idx_clients_active              ON clients (active);
CREATE INDEX idx_clients_last_name           ON clients (last_name);

CREATE INDEX idx_products_category_id        ON products (category_id);
CREATE INDEX idx_products_active             ON products (active);
CREATE INDEX idx_products_barcode            ON products (barcode);
CREATE INDEX idx_products_stock_quantity     ON products (stock_quantity);

CREATE INDEX idx_orders_client_id            ON orders (client_id);
CREATE INDEX idx_orders_user_id              ON orders (user_id);
CREATE INDEX idx_orders_status               ON orders (status);
CREATE INDEX idx_orders_created_at           ON orders (created_at);

CREATE INDEX idx_order_items_order_id        ON order_items (order_id);
CREATE INDEX idx_order_items_product_id      ON order_items (product_id);

CREATE INDEX idx_deliveries_status           ON deliveries (status);
CREATE INDEX idx_deliveries_scheduled_date   ON deliveries (scheduled_date);

CREATE INDEX idx_invoices_status             ON invoices (status);
CREATE INDEX idx_invoices_invoice_date       ON invoices (invoice_date);
CREATE INDEX idx_invoices_due_date           ON invoices (due_date);

CREATE INDEX idx_payments_invoice_id         ON payments (invoice_id);
CREATE INDEX idx_payments_status             ON payments (status);

CREATE INDEX idx_stock_movements_product_id  ON stock_movements (product_id);
CREATE INDEX idx_stock_movements_user_id     ON stock_movements (user_id);
CREATE INDEX idx_stock_movements_type        ON stock_movements (type);
CREATE INDEX idx_stock_movements_created_at  ON stock_movements (created_at);

CREATE INDEX idx_activity_logs_user_id       ON activity_logs (user_id);
CREATE INDEX idx_activity_logs_action_type   ON activity_logs (action_type);
CREATE INDEX idx_activity_logs_entity        ON activity_logs (entity);
CREATE INDEX idx_activity_logs_created_at    ON activity_logs (created_at);


-- =====================================================================================
--  6. DONNÉES
-- =====================================================================================


-- ─────────────────────────────────────────────────────────────────────────────────────
--  6.1 Utilisateurs — INDISPENSABLE
-- ─────────────────────────────────────────────────────────────────────────────────────
--  Sans au moins un compte, aucune connexion n'est possible. La présence de ces lignes
--  neutralise DataInitializer (qui ne crée le super admin que si la table est vide).
--
--  Identifiants livrés (hashs BCrypt, coût 10, vérifiés) :
--     admin    / Admin@2024     → ADMIN
--     caissier / Caissier@2024  → CAISSIER
--
--  >>> CHANGER CES DEUX MOTS DE PASSE AVANT TOUTE MISE EN PRODUCTION. <<<
-- ─────────────────────────────────────────────────────────────────────────────────────

INSERT INTO users (id, username, email, password, first_name, last_name, phone, role, active, created_at, updated_at) VALUES
 (1, 'admin',    'admin@gescom.com',    '$2a$10$GdUv0e99DvChXf9YX4dM.e4IO3qLnwoA3OTNzFBc9Rb3ZRnGgn/Gm', 'Super',  'Admin',  '+32 467 61 34 61', 'ADMIN',    true, '2026-01-05 08:30:00', '2026-01-05 08:30:00'),
 (2, 'caissier', 'caissier@gescom.com', '$2a$10$IpgiZeNWsgY6LcI3O6er5Of/zzKYZ3JqIvhbcj.GZ86clsTRW3r1S', 'Sophie', 'Dubois', '+32 470 12 34 56', 'CAISSIER', true, '2026-01-05 08:45:00', '2026-01-05 08:45:00');


-- ─────────────────────────────────────────────────────────────────────────────────────
--  6.2 Paramètres — INDISPENSABLE (singleton)
-- ─────────────────────────────────────────────────────────────────────────────────────
--  Valeurs par défaut de SettingsService, complétées des mentions légales belges.
--  Adapter les champs `company_*` à l'entreprise réelle avant mise en production.
-- ─────────────────────────────────────────────────────────────────────────────────────

INSERT INTO settings (
    id, language, currency, timezone, date_format,
    company_name, company_email, company_phone, company_address, company_city,
    company_postal_code, company_country, company_tax_id, company_iban, company_bic,
    tax_rate, invoice_prefix, invoice_number_start, payment_terms, footer_text,
    notifications, email_notifications, order_notifications, stock_alerts, low_stock_threshold,
    theme, created_at, updated_at
) VALUES (
    1, 'fr', 'EUR', 'Europe/Brussels', 'DD/MM/YYYY',
    'GESCOM', 'contact@gescom.be', '+32 2 555 12 34', 'Rue du Commerce 45', 'Bruxelles',
    '1000', 'Belgique', 'BE0123.456.789', 'BE68539007547034', 'GKCCBEBB',
    21.0, 'INV', 1000, 30, 'Merci pour votre confiance',
    true, true, true, true, 10,
    'light', '2026-01-05 09:00:00', '2026-01-05 09:00:00'
);


-- ─────────────────────────────────────────────────────────────────────────────────────
--  6.3 Catégories — référentiel
-- ─────────────────────────────────────────────────────────────────────────────────────

INSERT INTO categories (id, name, description, code, active, created_at, updated_at) VALUES
 (1, 'Boissons',              'Eaux, jus, café, bières',                       'BOI', true, '2026-01-05 09:05:00', '2026-01-05 09:05:00'),
 (2, 'Épicerie',              'Produits secs et conserves',                    'EPI', true, '2026-01-05 09:05:00', '2026-01-05 09:05:00'),
 (3, 'Produits frais',        'Crèmerie et œufs — chaîne du froid',            'FRA', true, '2026-01-05 09:05:00', '2026-01-05 09:05:00'),
 (4, 'Hygiène & entretien',   'Droguerie, papier, soins du corps',             'HYG', true, '2026-01-05 09:05:00', '2026-01-05 09:05:00'),
 (5, 'Papeterie',             'Fournitures de bureau et scolaires',            'PAP', true, '2026-01-05 09:05:00', '2026-01-05 09:05:00'),
 (6, 'Petit électroménager',  'Appareils de comptoir',                         'ELE', true, '2026-01-05 09:05:00', '2026-01-05 09:05:00');


-- ─────────────────────────────────────────────────────────────────────────────────────
--  6.4 Produits — référentiel
-- ─────────────────────────────────────────────────────────────────────────────────────
--  `stock_quantity` correspond exactement au `new_stock` du dernier mouvement de la
--  section 6.9 pour chaque produit (contrôle automatisé en section 8).
--  Le produit P-6002 est volontairement sous son seuil d'alerte (3 < 5) pour que le
--  tableau de bord affiche une alerte de stock bas dès la première ouverture.
-- ─────────────────────────────────────────────────────────────────────────────────────

INSERT INTO products (id, code, name, description, purchase_price, selling_price, category_id, unit, stock_quantity, min_stock_alert, barcode, image_url, active, created_at, updated_at) VALUES
 ( 1, 'P-1001', 'Eau minérale plate 1,5 L (pack de 6)', 'Pack de 6 bouteilles d''eau de source.',            2.40,  3.60, 1, 'pack',  120, 24, '5410001000015', NULL, true, '2026-01-05 09:10:00', '2026-07-06 10:18:00'),
 ( 2, 'P-1002', 'Café moulu arabica 250 g',             'Mouture filtre, torréfaction moyenne.',             3.10,  5.50, 1, 'pièce',  80, 15, '5410001000022', NULL, true, '2026-01-05 09:10:00', '2026-07-27 09:22:00'),
 ( 3, 'P-1003', 'Jus d''orange 1 L',                    'Pur jus sans sucre ajouté.',                        1.20,  2.20, 1, 'pièce',  96, 20, '5410001000039', NULL, true, '2026-01-05 09:10:00', '2026-01-05 10:00:00'),
 ( 4, 'P-1004', 'Bière blonde 33 cl (pack de 24)',      'Casier de 24 bouteilles, 5,2 % vol.',              14.00, 21.00, 1, 'pack',   40, 10, '5410001000046', NULL, true, '2026-01-05 09:10:00', '2026-07-06 10:18:00'),
 ( 5, 'P-2001', 'Riz long grain 1 kg',                  'Riz étuvé, cuisson 12 minutes.',                    1.15,  2.10, 2, 'kg',    150, 30, '5410002000014', NULL, true, '2026-01-05 09:10:00', '2026-07-06 10:18:00'),
 ( 6, 'P-2002', 'Pâtes penne 500 g',                    'Semoule de blé dur.',                               0.70,  1.40, 2, 'pièce', 200, 40, '5410002000021', NULL, true, '2026-01-05 09:10:00', '2026-01-05 10:00:00'),
 ( 7, 'P-2003', 'Huile d''olive extra vierge 75 cl',    'Première pression à froid.',                        5.80,  9.90, 2, 'pièce',  60, 12, '5410002000038', NULL, true, '2026-01-05 09:10:00', '2026-07-27 09:22:00'),
 ( 8, 'P-2004', 'Farine de froment 1 kg',               'Type 55, usage pâtisserie et boulangerie.',         0.65,  1.25, 2, 'kg',    140, 30, '5410002000045', NULL, true, '2026-01-05 09:10:00', '2026-07-15 11:35:00'),
 ( 9, 'P-3001', 'Lait demi-écrémé 1 L',                 'UHT, brique de 1 litre.',                           0.75,  1.30, 3, 'pièce', 180, 36, '5410003000013', NULL, true, '2026-01-05 09:10:00', '2026-01-05 10:00:00'),
 (10, 'P-3002', 'Beurre doux 250 g',                    'Beurre de laiterie, 82 % de matière grasse.',       1.90,  3.20, 3, 'pièce',  70, 15, '5410003000020', NULL, true, '2026-01-05 09:10:00', '2026-07-15 11:35:00'),
 (11, 'P-3003', 'Œufs frais (boîte de 12)',             'Calibre moyen, élevage au sol.',                    2.30,  3.90, 3, 'boîte',  55, 12, '5410003000037', NULL, true, '2026-01-05 09:10:00', '2026-07-15 11:35:00'),
 (12, 'P-4001', 'Liquide vaisselle 1 L',                'Dégraissant, parfum citron.',                       1.45,  2.60, 4, 'pièce',  90, 18, '5410004000012', NULL, true, '2026-01-05 09:10:00', '2026-02-10 09:30:00'),
 (13, 'P-4002', 'Papier toilette (12 rouleaux)',        'Double épaisseur.',                                 3.90,  6.50, 4, 'pack',   65, 15, '5410004000029', NULL, true, '2026-01-05 09:10:00', '2026-07-27 09:22:00'),
 (14, 'P-4003', 'Gel douche 250 ml',                    'pH neutre, toutes peaux.',                          1.10,  2.30, 4, 'pièce', 110, 20, '5410004000036', NULL, true, '2026-01-05 09:10:00', '2026-01-05 10:00:00'),
 (15, 'P-5001', 'Cahier A4 96 pages',                   'Grands carreaux, couverture polypropylène.',        0.90,  1.80, 5, 'pièce', 130, 25, '5410005000011', NULL, true, '2026-01-05 09:10:00', '2026-07-22 16:46:00'),
 (16, 'P-5002', 'Stylo bille bleu (lot de 10)',         'Pointe moyenne 1 mm.',                              1.60,  3.20, 5, 'lot',    75, 15, '5410005000028', NULL, true, '2026-01-05 09:10:00', '2026-07-22 16:46:00'),
 (17, 'P-6001', 'Bouilloire électrique 1,7 L',          'Résistance couverte, arrêt automatique. 2 ans de garantie.', 15.50, 27.90, 6, 'pièce', 18, 5, '5410006000010', NULL, true, '2026-01-05 09:10:00', '2026-01-05 10:00:00'),
 (18, 'P-6002', 'Grille-pain 2 fentes',                 'Six niveaux de brunissage, tiroir ramasse-miettes.',18.00, 32.50, 6, 'pièce',   3,  5, '5410006000027', NULL, true, '2026-01-05 09:10:00', '2026-03-18 15:10:00');


-- ─────────────────────────────────────────────────────────────────────────────────────
--  6.5 Clients — jeu de démonstration
-- ─────────────────────────────────────────────────────────────────────────────────────

INSERT INTO clients (id, first_name, last_name, email, phone, address, city, postal_code, country, company, type, active, created_at, updated_at) VALUES
 (1, 'Marie',  'Lambert',  'marie.lambert@example.be',            '+32 475 11 22 33', 'Avenue Louise 120',          'Bruxelles', '1050', 'Belgique', NULL,                  'PARTICULIER', true, '2026-01-12 10:00:00', '2026-01-12 10:00:00'),
 (2, 'Thomas', 'Peeters',  'thomas.peeters@example.be',           '+32 486 44 55 66', 'Kerkstraat 7',               'Antwerpen', '2000', 'Belgique', NULL,                  'PARTICULIER', true, '2026-01-19 14:30:00', '2026-01-19 14:30:00'),
 (3, 'Amina',  'Haddad',   'contact@boulangerie-haddad.be',       '+32 2 555 78 90',  'Chaussée de Waterloo 210',   'Bruxelles', '1060', 'Belgique', 'Boulangerie Haddad',  'ENTREPRISE',  true, '2026-02-03 09:15:00', '2026-02-03 09:15:00'),
 (4, 'Luc',    'Moreau',   'achats@horeca-moreau.be',             '+32 4 222 33 44',  'Rue Saint-Gilles 15',        'Liège',     '4000', 'Belgique', 'Horeca Moreau',       'ENTREPRISE',  true, '2026-02-17 11:45:00', '2026-02-17 11:45:00'),
 (5, 'Sofie',  'Janssens', 'sofie.janssens@example.be',           '+32 493 77 88 99', 'Grote Markt 3',              'Gent',      '9000', 'Belgique', NULL,                  'PARTICULIER', true, '2026-03-09 16:20:00', '2026-03-09 16:20:00');


-- ─────────────────────────────────────────────────────────────────────────────────────
--  6.6 Commandes — jeu de démonstration du cycle de vie
-- ─────────────────────────────────────────────────────────────────────────────────────
--  Les cinq états de Order.OrderStatus sont représentés :
--
--    CMD-2026-0001  DELIVERED  facturée, payée par carte (Stripe simulé), livrée
--    CMD-2026-0002  INVOICED   facturée avec remise pro, paiement partiel par virement
--    CMD-2026-0003  INVOICED   vente de passage (client_id NULL), payée en espèces
--    CMD-2026-0004  CANCELED   annulée depuis PENDING → aucun stock consommé
--    CMD-2026-0005  CONFIRMED  stock déjà sorti, en attente de facturation
--    CMD-2026-0006  PENDING    brouillon → aucun stock consommé
-- ─────────────────────────────────────────────────────────────────────────────────────

INSERT INTO orders (id, order_number, client_id, user_id, total_amount, discount, final_amount, status, notes, created_at, updated_at) VALUES
 (1, 'CMD-2026-0001', 4,    1, 183.00, 3.00, 180.00, 'DELIVERED', 'Livraison en soirée, accès par le quai arrière.',              '2026-07-06 10:15:00', '2026-07-09 17:35:00'),
 (2, 'CMD-2026-0002', 3,    1, 122.40, 2.40, 120.00, 'INVOICED',  'Remise professionnelle accordée sur la farine.',               '2026-07-15 11:30:00', '2026-07-15 11:40:00'),
 (3, 'CMD-2026-0003', NULL, 2,  15.40, 0.00,  15.40, 'INVOICED',  'Vente de passage — comptoir.',                                 '2026-07-22 16:45:00', '2026-07-22 16:47:00'),
 (4, 'CMD-2026-0004', 5,    2,  27.90, 0.00,  27.90, 'CANCELED',  'Annulée à la demande du client : article non conforme au besoin.', '2026-07-24 13:00:00', '2026-07-24 13:20:00'),
 (5, 'CMD-2026-0005', 2,    2,  27.40, 0.00,  27.40, 'CONFIRMED', NULL,                                                           '2026-07-27 09:20:00', '2026-07-27 09:22:00'),
 (6, 'CMD-2026-0006', 1,    2,  16.60, 0.00,  16.60, 'PENDING',   'À valider avec le client (quantité de lait à confirmer).',     '2026-07-29 14:05:00', '2026-07-29 14:05:00');


-- ─────────────────────────────────────────────────────────────────────────────────────
--  6.7 Lignes de commande
-- ─────────────────────────────────────────────────────────────────────────────────────
--  total_price = round(unit_price × quantity × (100 − discount) / 100, 2).
--  La ligne 4 illustre une remise de ligne : 1,25 × 40 = 50,00 − 10 % = 45,00.
-- ─────────────────────────────────────────────────────────────────────────────────────

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, total_price, discount) VALUES
 ( 1, 1,  1, 10,  3.60,  36.00,  0.00),
 ( 2, 1,  4,  5, 21.00, 105.00,  0.00),
 ( 3, 1,  5, 20,  2.10,  42.00,  0.00),
 ( 4, 2,  8, 40,  1.25,  45.00, 10.00),
 ( 5, 2, 10, 12,  3.20,  38.40,  0.00),
 ( 6, 2, 11, 10,  3.90,  39.00,  0.00),
 ( 7, 3, 15,  5,  1.80,   9.00,  0.00),
 ( 8, 3, 16,  2,  3.20,   6.40,  0.00),
 ( 9, 4, 17,  1, 27.90,  27.90,  0.00),
 (10, 5,  2,  2,  5.50,  11.00,  0.00),
 (11, 5,  7,  1,  9.90,   9.90,  0.00),
 (12, 5, 13,  1,  6.50,   6.50,  0.00),
 (13, 6,  3,  4,  2.20,   8.80,  0.00),
 (14, 6,  9,  6,  1.30,   7.80,  0.00);


-- ─────────────────────────────────────────────────────────────────────────────────────
--  6.8 Livraisons, factures et transactions
-- ─────────────────────────────────────────────────────────────────────────────────────

-- La livraison est créée après la facturation (commande INVOICED) puis marquée DELIVERED,
-- ce qui fait basculer la commande en DELIVERED.
INSERT INTO deliveries (id, delivery_number, order_id, delivery_address, delivery_city, delivery_postal_code, delivery_country, contact_name, contact_phone, status, scheduled_date, delivered_date, delivered_by, notes, created_at, updated_at) VALUES
 (1, 'LIV-2026-0001', 1, 'Rue Saint-Gilles 15', 'Liège', '4000', 'Belgique', 'Luc Moreau', '+32 4 222 33 44', 'DELIVERED', '2026-07-09 08:00:00', '2026-07-09 17:35:00', 'Transport Vandenberghe', 'Colis remis au responsable de salle.', '2026-07-06 10:30:00', '2026-07-09 17:35:00');

-- Factures.
--   n° 1 : 183,00 − 3,00 = 180,00 × 21 % = 37,80 → 217,80 TTC, soldée par carte.
--   n° 2 : 122,40 − 2,40 = 120,00 × 21 % = 25,20 → 145,20 TTC, 60,00 encaissés → reste 85,20.
--          payment_date reste NULL : elle n'est renseignée qu'au règlement complet.
--   n° 3 : 15,40 × 21 % = 3,234 → 3,23 (HALF_UP) → 18,63 TTC, soldée en espèces.
INSERT INTO invoices (id, invoice_number, order_id, delivery_id, invoice_date, due_date, subtotal, discount, tax_rate, tax_amount, total_amount, paid_amount, remaining_amount, status, payment_method, payment_date, notes, created_at, updated_at) VALUES
 (1, 'FACT-2026-0001', 1, NULL, '2026-07-06', '2026-08-05', 183.00, 3.00, 21.00, 37.80, 217.80, 217.80,  0.00, 'PAID',           'CREDIT_CARD',   '2026-07-06', NULL,                                            '2026-07-06 10:20:00', '2026-07-06 10:23:00'),
 (2, 'FACT-2026-0002', 2, NULL, '2026-07-15', '2026-08-14', 122.40, 2.40, 21.00, 25.20, 145.20,  60.00, 85.20, 'PARTIALLY_PAID', 'BANK_TRANSFER', NULL,         'Acompte de 60,00 € reçu le 20/07 ; solde à 30 jours.', '2026-07-15 11:40:00', '2026-07-20 09:15:00'),
 (3, 'FACT-2026-0003', 3, NULL, '2026-07-22', '2026-07-22',  15.40, 0.00, 21.00,  3.23,  18.63,  18.63,  0.00, 'PAID',           'CASH',          '2026-07-22', 'Payée comptant au comptoir.',                   '2026-07-22 16:46:00', '2026-07-22 16:47:00');

-- Transaction carte de la facture n° 1, jouée par la passerelle simulée (stripe.mode=simulated).
-- Aucun secret n'est stocké : le client_secret est @Transient côté entité.
INSERT INTO payments (id, invoice_id, provider, intent_id, amount, currency, status, card_brand, card_last4, failure_message, simulated, created_at, confirmed_at) VALUES
 (1, 1, 'STRIPE', 'pi_sim_2026070610220001', 217.80, 'eur', 'SUCCEEDED', 'visa', '4242', NULL, true, '2026-07-06 10:22:00', '2026-07-06 10:23:00');


-- ─────────────────────────────────────────────────────────────────────────────────────
--  6.9 Mouvements de stock
-- ─────────────────────────────────────────────────────────────────────────────────────
--  Chaîne complète et continue par produit : previous_stock du mouvement n =
--  new_stock du mouvement n−1, et le dernier new_stock = products.stock_quantity.
--
--    ids  1 → 18 : inventaire d'ouverture (STOCK_IN, previous_stock = 0)
--    id      19  : ajustement d'inventaire (+2 sur P-4001)
--    id      20  : casse en réserve (−9 sur P-6002 → passe sous son seuil d'alerte)
--    ids 21 → 31 : sorties de stock des commandes confirmées
--
--  Rappel : seules les commandes confirmées consomment du stock. CMD-2026-0004
--  (annulée depuis PENDING) et CMD-2026-0006 (brouillon) n'ont donc aucun mouvement.
-- ─────────────────────────────────────────────────────────────────────────────────────

INSERT INTO stock_movements (id, product_id, type, quantity, previous_stock, new_stock, unit_cost, reason, reference, user_id, created_at) VALUES
 ( 1,  1, 'STOCK_IN',   130,   0, 130,  2.40, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 ( 2,  2, 'STOCK_IN',    82,   0,  82,  3.10, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 ( 3,  3, 'STOCK_IN',    96,   0,  96,  1.20, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 ( 4,  4, 'STOCK_IN',    45,   0,  45, 14.00, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 ( 5,  5, 'STOCK_IN',   170,   0, 170,  1.15, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 ( 6,  6, 'STOCK_IN',   200,   0, 200,  0.70, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 ( 7,  7, 'STOCK_IN',    61,   0,  61,  5.80, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 ( 8,  8, 'STOCK_IN',   180,   0, 180,  0.65, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 ( 9,  9, 'STOCK_IN',   180,   0, 180,  0.75, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 (10, 10, 'STOCK_IN',    82,   0,  82,  1.90, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 (11, 11, 'STOCK_IN',    65,   0,  65,  2.30, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 (12, 12, 'STOCK_IN',    88,   0,  88,  1.45, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 (13, 13, 'STOCK_IN',    66,   0,  66,  3.90, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 (14, 14, 'STOCK_IN',   110,   0, 110,  1.10, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 (15, 15, 'STOCK_IN',   135,   0, 135,  0.90, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 (16, 16, 'STOCK_IN',    77,   0,  77,  1.60, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 (17, 17, 'STOCK_IN',    18,   0,  18, 15.50, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 (18, 18, 'STOCK_IN',    12,   0,  12, 18.00, 'Stock initial — inventaire d''ouverture', 'INV-OUV-2026', 1, '2026-01-05 10:00:00'),
 (19, 12, 'ADJUSTMENT',   2,  88,  90,  NULL, 'Ajustement d''inventaire — écart constaté au comptage', 'AJ-2026-02',    1, '2026-02-10 09:30:00'),
 (20, 18, 'DAMAGE',       9,  12,   3,  NULL, 'Casse en réserve (palette renversée)',                 'CASSE-2026-03', 1, '2026-03-18 15:10:00'),
 (21,  1, 'STOCK_OUT',   10, 130, 120,  NULL, 'Vente — commande CMD-2026-0001', 'CMD-2026-0001', 1, '2026-07-06 10:18:00'),
 (22,  4, 'STOCK_OUT',    5,  45,  40,  NULL, 'Vente — commande CMD-2026-0001', 'CMD-2026-0001', 1, '2026-07-06 10:18:00'),
 (23,  5, 'STOCK_OUT',   20, 170, 150,  NULL, 'Vente — commande CMD-2026-0001', 'CMD-2026-0001', 1, '2026-07-06 10:18:00'),
 (24,  8, 'STOCK_OUT',   40, 180, 140,  NULL, 'Vente — commande CMD-2026-0002', 'CMD-2026-0002', 1, '2026-07-15 11:35:00'),
 (25, 10, 'STOCK_OUT',   12,  82,  70,  NULL, 'Vente — commande CMD-2026-0002', 'CMD-2026-0002', 1, '2026-07-15 11:35:00'),
 (26, 11, 'STOCK_OUT',   10,  65,  55,  NULL, 'Vente — commande CMD-2026-0002', 'CMD-2026-0002', 1, '2026-07-15 11:35:00'),
 (27, 15, 'STOCK_OUT',    5, 135, 130,  NULL, 'Vente — commande CMD-2026-0003', 'CMD-2026-0003', 2, '2026-07-22 16:46:00'),
 (28, 16, 'STOCK_OUT',    2,  77,  75,  NULL, 'Vente — commande CMD-2026-0003', 'CMD-2026-0003', 2, '2026-07-22 16:46:00'),
 (29,  2, 'STOCK_OUT',    2,  82,  80,  NULL, 'Vente — commande CMD-2026-0005', 'CMD-2026-0005', 2, '2026-07-27 09:22:00'),
 (30,  7, 'STOCK_OUT',    1,  61,  60,  NULL, 'Vente — commande CMD-2026-0005', 'CMD-2026-0005', 2, '2026-07-27 09:22:00'),
 (31, 13, 'STOCK_OUT',    1,  66,  65,  NULL, 'Vente — commande CMD-2026-0005', 'CMD-2026-0005', 2, '2026-07-27 09:22:00');


-- ─────────────────────────────────────────────────────────────────────────────────────
--  6.10 Journal d'activité
-- ─────────────────────────────────────────────────────────────────────────────────────
--  Échantillon retraçant les opérations ci-dessus, pour que la piste d'audit et les
--  écrans de suivi ne soient pas vides à la première ouverture.
-- ─────────────────────────────────────────────────────────────────────────────────────

INSERT INTO activity_logs (id, user_id, action_type, entity, entity_id, description, ip_address, details, created_at) VALUES
 ( 1, 1, 'LOGIN',     'User',     1,    'Connexion de admin',                                                   '127.0.0.1', NULL, '2026-01-05 08:55:00'),
 ( 2, 1, 'CREATE',    'Category', 1,    'Création de la catégorie Boissons',                                    '127.0.0.1', NULL, '2026-01-05 09:05:00'),
 ( 3, 1, 'STOCK_IN',  'Product',  NULL, 'Inventaire d''ouverture — 18 produits enregistrés',                    '127.0.0.1', NULL, '2026-01-05 10:00:00'),
 ( 4, 1, 'STOCK_OUT', 'Product',  18,   'Casse en réserve — 9 unités de P-6002 sorties du stock',               '127.0.0.1', NULL, '2026-03-18 15:10:00'),
 ( 5, 1, 'SALE',      'Order',    1,    'Création de la commande CMD-2026-0001 - Montant: 180.00',               '127.0.0.1', NULL, '2026-07-06 10:15:00'),
 ( 6, 1, 'CREATE',    'Invoice',  1,    'Création de la facture FACT-2026-0001 - Montant: 217.80',               '127.0.0.1', NULL, '2026-07-06 10:20:00'),
 ( 7, 1, 'PAYMENT',   'Invoice',  1,    'Paiement de 217.80 sur la facture FACT-2026-0001 (CREDIT_CARD)',        '127.0.0.1', NULL, '2026-07-06 10:23:00'),
 ( 8, 1, 'CREATE',    'Delivery', 1,    'Création de la livraison LIV-2026-0001 pour la commande CMD-2026-0001', '127.0.0.1', NULL, '2026-07-06 10:30:00'),
 ( 9, 1, 'UPDATE',    'Delivery', 1,    'Livraison LIV-2026-0001 marquée comme livrée',                         '127.0.0.1', NULL, '2026-07-09 17:35:00'),
 (10, 1, 'CREATE',    'Invoice',  2,    'Création de la facture FACT-2026-0002 - Montant: 145.20',               '127.0.0.1', NULL, '2026-07-15 11:40:00'),
 (11, 1, 'PAYMENT',   'Invoice',  2,    'Paiement de 60.00 sur la facture FACT-2026-0002 (BANK_TRANSFER)',       '127.0.0.1', NULL, '2026-07-20 09:15:00'),
 (12, 2, 'LOGIN',     'User',     2,    'Connexion de caissier',                                                '127.0.0.1', NULL, '2026-07-22 16:40:00'),
 (13, 2, 'SALE',      'Order',    3,    'Création de la commande CMD-2026-0003 - Montant: 15.40',                '127.0.0.1', NULL, '2026-07-22 16:45:00'),
 (14, 2, 'PAYMENT',   'Invoice',  3,    'Paiement de 18.63 sur la facture FACT-2026-0003 (CASH)',                '127.0.0.1', NULL, '2026-07-22 16:47:00'),
 (15, 2, 'UPDATE',    'Order',    4,    'Annulation de la commande CMD-2026-0004',                               '127.0.0.1', NULL, '2026-07-24 13:20:00'),
 (16, 2, 'UPDATE',    'Order',    5,    'Confirmation de la commande CMD-2026-0005',                             '127.0.0.1', NULL, '2026-07-27 09:22:00'),
 (17, 2, 'SALE',      'Order',    6,    'Création de la commande CMD-2026-0006 - Montant: 16.60',                '127.0.0.1', NULL, '2026-07-29 14:05:00');


-- =====================================================================================
--  7. SÉQUENCES
-- =====================================================================================
--  Les identifiants ci-dessus sont explicites ; sans ce recalage, la première insertion
--  applicative repartirait de 1 et violerait les clés primaires.
--  `setval(..., max(id))` positionne la séquence pour que nextval renvoie max(id) + 1.
--  Les tables sans données reçoivent `setval(..., 1, false)` → nextval renverra 1.
-- =====================================================================================

SELECT setval('users_id_seq',           (SELECT COALESCE(MAX(id), 1) FROM users),           (SELECT COUNT(*) > 0 FROM users));
SELECT setval('categories_id_seq',      (SELECT COALESCE(MAX(id), 1) FROM categories),      (SELECT COUNT(*) > 0 FROM categories));
SELECT setval('clients_id_seq',         (SELECT COALESCE(MAX(id), 1) FROM clients),         (SELECT COUNT(*) > 0 FROM clients));
SELECT setval('products_id_seq',        (SELECT COALESCE(MAX(id), 1) FROM products),        (SELECT COUNT(*) > 0 FROM products));
SELECT setval('settings_id_seq',        (SELECT COALESCE(MAX(id), 1) FROM settings),        (SELECT COUNT(*) > 0 FROM settings));
SELECT setval('orders_id_seq',          (SELECT COALESCE(MAX(id), 1) FROM orders),          (SELECT COUNT(*) > 0 FROM orders));
SELECT setval('order_items_id_seq',     (SELECT COALESCE(MAX(id), 1) FROM order_items),     (SELECT COUNT(*) > 0 FROM order_items));
SELECT setval('deliveries_id_seq',      (SELECT COALESCE(MAX(id), 1) FROM deliveries),      (SELECT COUNT(*) > 0 FROM deliveries));
SELECT setval('invoices_id_seq',        (SELECT COALESCE(MAX(id), 1) FROM invoices),        (SELECT COUNT(*) > 0 FROM invoices));
SELECT setval('payments_id_seq',        (SELECT COALESCE(MAX(id), 1) FROM payments),        (SELECT COUNT(*) > 0 FROM payments));
SELECT setval('stock_movements_id_seq', (SELECT COALESCE(MAX(id), 1) FROM stock_movements), (SELECT COUNT(*) > 0 FROM stock_movements));
SELECT setval('activity_logs_id_seq',   (SELECT COALESCE(MAX(id), 1) FROM activity_logs),   (SELECT COUNT(*) > 0 FROM activity_logs));


-- =====================================================================================
--  8. CONTRÔLES DE COHÉRENCE
-- =====================================================================================
--  Rejoue les invariants métier sur les données qui viennent d'être insérées. Une seule
--  divergence lève une exception : la transaction est annulée et la base reste vide,
--  plutôt que de laisser passer un jeu de données silencieusement faux.
--
--  Ce bloc ne crée aucun objet en base — il ne s'exécute qu'ici, à la restauration.
-- =====================================================================================

DO $$
DECLARE
    v_n bigint;
BEGIN
    -- 8.1 Total de ligne = prix unitaire × quantité, remise de ligne (en %) déduite.
    SELECT count(*) INTO v_n FROM order_items
     WHERE total_price <> round(unit_price * quantity * (100 - COALESCE(discount, 0)) / 100, 2);
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.1 : % ligne(s) de commande dont total_price ne correspond pas au calcul prix × quantité − remise', v_n;
    END IF;

    -- 8.2 Total de commande = somme des lignes.
    SELECT count(*) INTO v_n
      FROM orders o
      LEFT JOIN (SELECT order_id, sum(total_price) AS s FROM order_items GROUP BY order_id) i
             ON i.order_id = o.id
     WHERE o.total_amount <> COALESCE(i.s, 0);
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.2 : % commande(s) dont total_amount diffère de la somme de leurs lignes', v_n;
    END IF;

    -- 8.3 Net à facturer = total − remise globale, et remise jamais supérieure au total.
    SELECT count(*) INTO v_n FROM orders
     WHERE final_amount <> total_amount - discount OR discount > total_amount OR discount < 0;
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.3 : % commande(s) dont final_amount ou discount est incohérent', v_n;
    END IF;

    -- 8.4 Décomposition de la facture : TVA sur le net après remise, puis total TTC.
    SELECT count(*) INTO v_n FROM invoices
     WHERE tax_amount <> round((subtotal - COALESCE(discount, 0)) * COALESCE(tax_rate, 0) / 100, 2)
        OR total_amount <> subtotal - COALESCE(discount, 0) + tax_amount
        OR remaining_amount <> total_amount - COALESCE(paid_amount, 0)
        OR COALESCE(discount, 0) > subtotal;
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.4 : % facture(s) dont la décomposition des montants est incohérente', v_n;
    END IF;

    -- 8.5 La facture reprend le brut de la commande et cumule au moins sa remise.
    SELECT count(*) INTO v_n
      FROM invoices i JOIN orders o ON o.id = i.order_id
     WHERE i.subtotal <> o.total_amount OR COALESCE(i.discount, 0) < o.discount;
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.5 : % facture(s) désalignée(s) du montant de leur commande', v_n;
    END IF;

    -- 8.6 Statut de règlement déduit du rapport payé / total.
    SELECT count(*) INTO v_n FROM invoices
     WHERE status <> 'CANCELED'
       AND status <> CASE
                        WHEN COALESCE(paid_amount, 0) = 0                 THEN 'UNPAID'
                        WHEN COALESCE(paid_amount, 0) >= total_amount     THEN 'PAID'
                        ELSE 'PARTIALLY_PAID'
                     END;
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.6 : % facture(s) dont le statut ne correspond pas au montant encaissé', v_n;
    END IF;

    -- 8.7 payment_date renseignée si et seulement si la facture est soldée.
    SELECT count(*) INTO v_n FROM invoices
     WHERE (status = 'PAID' AND payment_date IS NULL)
        OR (status = 'PARTIALLY_PAID' AND payment_date IS NOT NULL);
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.7 : % facture(s) dont payment_date contredit le statut de règlement', v_n;
    END IF;

    -- 8.8 Une transaction ne peut pas dépasser le total de la facture qu'elle règle.
    SELECT count(*) INTO v_n
      FROM payments p JOIN invoices i ON i.id = p.invoice_id
     WHERE p.amount > i.total_amount;
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.8 : % transaction(s) d''un montant supérieur au total de la facture', v_n;
    END IF;

    -- 8.9 Chaîne des mouvements de stock : quantité = |variation|, et stock jamais négatif.
    SELECT count(*) INTO v_n FROM stock_movements
     WHERE quantity <> abs(new_stock - previous_stock) OR new_stock < 0 OR previous_stock < 0;
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.9 : % mouvement(s) de stock dont la variation est incohérente', v_n;
    END IF;

    -- 8.10 Continuité de la chaîne : previous_stock reprend le new_stock du mouvement précédent.
    SELECT count(*) INTO v_n
      FROM (SELECT previous_stock,
                   lag(new_stock) OVER (PARTITION BY product_id ORDER BY created_at, id) AS prec
              FROM stock_movements) t
     WHERE COALESCE(t.prec, t.previous_stock) <> t.previous_stock;
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.10 : % rupture(s) dans la chaîne des mouvements de stock', v_n;
    END IF;

    -- 8.11 Le stock courant du produit = new_stock de son dernier mouvement.
    SELECT count(*) INTO v_n
      FROM products p
      LEFT JOIN (SELECT DISTINCT ON (product_id) product_id, new_stock
                   FROM stock_movements
                  ORDER BY product_id, created_at DESC, id DESC) m
             ON m.product_id = p.id
     WHERE p.stock_quantity <> COALESCE(m.new_stock, 0);
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.11 : % produit(s) dont stock_quantity diffère du dernier mouvement enregistré', v_n;
    END IF;

    -- 8.12 Flux linéaire : toute commande facturée ou livrée porte une facture.
    SELECT count(*) INTO v_n
      FROM orders o LEFT JOIN invoices i ON i.order_id = o.id
     WHERE o.status IN ('INVOICED', 'DELIVERED') AND i.id IS NULL;
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.12 : % commande(s) INVOICED/DELIVERED sans facture', v_n;
    END IF;

    -- 8.13 Réciproque : aucune facture sur une commande non encore facturée.
    SELECT count(*) INTO v_n
      FROM invoices i JOIN orders o ON o.id = i.order_id
     WHERE o.status NOT IN ('INVOICED', 'DELIVERED', 'CANCELED');
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.13 : % facture(s) rattachée(s) à une commande non facturée', v_n;
    END IF;

    -- 8.14 La livraison ne précède jamais la facturation.
    SELECT count(*) INTO v_n
      FROM deliveries d JOIN orders o ON o.id = d.order_id
     WHERE o.status NOT IN ('INVOICED', 'DELIVERED');
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.14 : % livraison(s) sur une commande non facturée', v_n;
    END IF;

    -- 8.15 Une commande DELIVERED a une livraison effectivement livrée, et inversement.
    SELECT count(*) INTO v_n
      FROM orders o LEFT JOIN deliveries d ON d.order_id = o.id
     WHERE (o.status = 'DELIVERED' AND (d.id IS NULL OR d.status <> 'DELIVERED'))
        OR (o.status <> 'DELIVERED' AND d.status = 'DELIVERED');
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.15 : % commande(s) dont le statut contredit l''état de sa livraison', v_n;
    END IF;

    -- 8.16 Cohérence des dates de livraison.
    SELECT count(*) INTO v_n FROM deliveries
     WHERE (status = 'DELIVERED' AND delivered_date IS NULL)
        OR (status = 'PENDING'   AND delivered_date IS NOT NULL);
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.16 : % livraison(s) dont delivered_date contredit le statut', v_n;
    END IF;

    -- 8.17 Seules les commandes confirmées ont consommé du stock.
    SELECT count(*) INTO v_n
      FROM stock_movements m JOIN orders o ON o.order_number = m.reference
     WHERE m.type = 'STOCK_OUT' AND o.status = 'PENDING';
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.17 : % sortie(s) de stock rattachée(s) à une commande encore en attente', v_n;
    END IF;

    -- 8.18 Échéance de facture postérieure ou égale à la date de facture.
    SELECT count(*) INTO v_n FROM invoices WHERE due_date < invoice_date;
    IF v_n > 0 THEN
        RAISE EXCEPTION 'Cohérence 8.18 : % facture(s) dont l''échéance précède la date de facture', v_n;
    END IF;

    -- 8.19 Paramètres : singleton, une seule ligne.
    SELECT count(*) INTO v_n FROM settings;
    IF v_n <> 1 THEN
        RAISE EXCEPTION 'Cohérence 8.19 : la table settings contient % ligne(s), une seule est attendue', v_n;
    END IF;

    -- 8.20 Au moins un administrateur actif, sinon l'application est inaccessible.
    SELECT count(*) INTO v_n FROM users WHERE role = 'ADMIN' AND active;
    IF v_n = 0 THEN
        RAISE EXCEPTION 'Cohérence 8.20 : aucun administrateur actif — la connexion serait impossible';
    END IF;

    RAISE NOTICE 'GESCOM : 20 contrôles de cohérence passés avec succès.';
END $$;

COMMIT;

-- =====================================================================================
--  FIN DU DUMP
--
--  Après restauration :
--    1. Renseigner les variables d'environnement DB_URL, DB_USERNAME, DB_PASSWORD,
--       JWT_SECRET (64 caractères aléatoires minimum), CORS_ORIGINS.
--    2. Démarrer l'API : mvn spring-boot:run  (http://localhost:8085)
--       Hibernate (ddl-auto=update) ne doit émettre aucun ALTER TABLE sur ce schéma.
--    3. Se connecter avec admin / Admin@2024, puis changer immédiatement les deux
--       mots de passe livrés en section 6.1.
-- =====================================================================================
