-- ============================================================================
-- Bascule multi-tenant : rattachement des tables metier a leur entreprise.
--
-- Chaque table cloisonnee recoit un company_id en trois temps : ajout de la colonne
-- en nullable, backfill vers l'entreprise historique ('gescom', creee en V15_2), puis
-- passage en NOT NULL. Faire l'inverse echouerait sur toute base contenant des donnees.
--
-- Les tables filles `order_items` et `stock_return_items` ne portent pas de company_id :
-- elles n'ont pas de repository et ne sont jamais atteintes autrement que par leur parent
-- (Order / StockReturn), deja cloisonne. Ajouter la colonne y creerait une seconde source
-- de verite a maintenir cohérente pour aucun gain d'isolation.
--
-- `users.company_id` est volontairement NULLABLE : le SUPER_ADMIN, proprietaire de la
-- plateforme, n'appartient a aucune entreprise cliente. C'est precisement ce NULL qui
-- lui ouvre la vue globale (voir TenantContext).
-- ============================================================================

-- ── 1. Ajout des colonnes ───────────────────────────────────────────────────
ALTER TABLE users            ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE categories       ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE clients          ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE products         ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE orders           ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE deliveries       ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE invoices         ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE payments         ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE stock_movements  ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE stock_returns    ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE activity_logs    ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE settings         ADD COLUMN company_id BIGINT REFERENCES companies(id);

-- ── 2. Backfill vers l'entreprise historique ────────────────────────────────
UPDATE users           SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE categories      SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE clients         SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE products        SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE orders          SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE deliveries      SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE invoices        SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE payments        SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE stock_movements SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE stock_returns   SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE activity_logs   SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');
UPDATE settings        SET company_id = (SELECT id FROM companies WHERE slug = 'gescom');

-- ── 3. Verrouillage en NOT NULL ─────────────────────────────────────────────
-- Deux exclusions, pour la meme raison de fond : le proprietaire de la plateforme
-- n'appartient a aucune entreprise cliente.
--   * `users`         : voir l'en-tete.
--   * `activity_logs` : la connexion du SUPER_ADMIN ecrit une trace LOGIN qui, elle non
--                       plus, ne se rattache a aucune entreprise. Exiger la colonne ici
--                       ferait echouer chaque journalisation d'action plateforme.
ALTER TABLE categories       ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE clients          ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE products         ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE orders           ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE deliveries       ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE invoices         ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE payments         ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE stock_movements  ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE stock_returns    ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE settings         ALTER COLUMN company_id SET NOT NULL;

-- ── 4. Index de cloisonnement ───────────────────────────────────────────────
-- Toute requete metier filtre desormais sur company_id : sans ces index, chaque
-- lecture degenere en parcours complet des le second client.
CREATE INDEX idx_users_company           ON users(company_id);
CREATE INDEX idx_categories_company      ON categories(company_id);
CREATE INDEX idx_clients_company         ON clients(company_id);
CREATE INDEX idx_products_company        ON products(company_id);
CREATE INDEX idx_orders_company          ON orders(company_id);
CREATE INDEX idx_deliveries_company      ON deliveries(company_id);
CREATE INDEX idx_invoices_company        ON invoices(company_id);
CREATE INDEX idx_payments_company        ON payments(company_id);
CREATE INDEX idx_stock_movements_company ON stock_movements(company_id);
CREATE INDEX idx_stock_returns_company   ON stock_returns(company_id);
CREATE INDEX idx_activity_logs_company   ON activity_logs(company_id);

-- ── 5. Requalification des contraintes d'unicite ────────────────────────────
-- Point critique de la bascule. Ces colonnes etaient UNIQUE au niveau de toute la base,
-- ce qui n'avait de sens qu'avec une seule entreprise : en l'etat, une deuxieme cliente
-- ne pourrait pas creer une categorie « Boissons » ni un article de code « A001 » deja
-- pris ailleurs, et sa premiere commande entrerait en collision de numerotation.
-- L'unicite doit donc devenir relative a l'entreprise.
--
-- Les noms cibles sont ceux que PostgreSQL genere pour un UNIQUE declare en ligne
-- (<table>_<colonne>_key).
ALTER TABLE categories    DROP CONSTRAINT categories_name_key;
ALTER TABLE products      DROP CONSTRAINT products_code_key;
ALTER TABLE clients       DROP CONSTRAINT clients_email_key;
ALTER TABLE orders        DROP CONSTRAINT orders_order_number_key;
ALTER TABLE deliveries    DROP CONSTRAINT deliveries_delivery_number_key;
ALTER TABLE invoices      DROP CONSTRAINT invoices_invoice_number_key;
ALTER TABLE stock_returns DROP CONSTRAINT stock_returns_return_number_key;

ALTER TABLE categories    ADD CONSTRAINT uq_categories_company_name       UNIQUE (company_id, name);
ALTER TABLE products      ADD CONSTRAINT uq_products_company_code         UNIQUE (company_id, code);
ALTER TABLE orders        ADD CONSTRAINT uq_orders_company_number         UNIQUE (company_id, order_number);
ALTER TABLE deliveries    ADD CONSTRAINT uq_deliveries_company_number     UNIQUE (company_id, delivery_number);
ALTER TABLE invoices      ADD CONSTRAINT uq_invoices_company_number       UNIQUE (company_id, invoice_number);
ALTER TABLE stock_returns ADD CONSTRAINT uq_stock_returns_company_number  UNIQUE (company_id, return_number);

-- L'email client est facultatif ; un UNIQUE ordinaire laisserait passer plusieurs NULL
-- mais interdirait la meme adresse chez deux entreprises differentes. L'index partiel
-- restreint l'unicite aux adresses reellement renseignees, par entreprise.
CREATE UNIQUE INDEX uq_clients_company_email
    ON clients(company_id, email)
    WHERE email IS NOT NULL;

-- `settings` cesse d'etre un singleton global pour devenir un singleton par entreprise :
-- SettingsService cree la ligne a la volee au premier acces de chaque cliente.
ALTER TABLE settings ADD CONSTRAINT uq_settings_company UNIQUE (company_id);

-- users.username / users.email restent uniques globalement : la connexion se fait sur le
-- seul nom d'utilisateur, sans indiquer l'entreprise. Les rendre uniques par entreprise
-- rendrait le login ambigu.

-- ── 6. Nouveau role : le proprietaire de la plateforme ──────────────────────
-- SUPER_ADMIN n'est pas un ADMIN plus puissant, c'est un role d'une autre nature :
-- l'ADMIN administre SON entreprise, le SUPER_ADMIN exploite GESCOM et n'a acces a
-- aucun ecran metier. Le CHECK doit accepter la nouvelle valeur avant qu'un tel
-- compte puisse exister.
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('ADMIN', 'CAISSIER', 'SUPER_ADMIN'));

-- Un compte sans entreprise est necessairement un SUPER_ADMIN, et reciproquement.
-- Cette contrainte est le garde-fou du modele : elle interdit qu'un ADMIN d'entreprise
-- se retrouve avec company_id NULL, cas qui lui ouvrirait la vue globale.
ALTER TABLE users ADD CONSTRAINT chk_users_company_scope
    CHECK ((role = 'SUPER_ADMIN' AND company_id IS NULL)
        OR (role <> 'SUPER_ADMIN' AND company_id IS NOT NULL));
