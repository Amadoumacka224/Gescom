- =====================================================
-- DUMP SQL - APPLICATION GES
-- Base de données: PostgreSQL
-- Version: 17.5
-- Date: 27 juin 2025
-- =====================================================

-- Suppression des tables existantes (ordre inverse des dépendances)
DROP TABLE IF EXISTS invoice_items CASCADE;
DROP TABLE IF EXISTS invoices CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS clients CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Suppression des types ENUM existants
DROP TYPE IF EXISTS user_role CASCADE;
DROP TYPE IF EXISTS order_status CASCADE;
DROP TYPE IF EXISTS invoice_status CASCADE;

-- =====================================================
-- CRÉATION DES TYPES ENUM
-- =====================================================

-- Type pour les rôles utilisateur
CREATE TYPE user_role AS ENUM ('ADMIN', 'MANAGER', 'VENDEUR');

-- Type pour les statuts de commande
CREATE TYPE order_status AS ENUM ('DRAFT', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED');

-- Type pour les statuts de facture
CREATE TYPE invoice_status AS ENUM ('DRAFT', 'SENT', 'PAID', 'OVERDUE', 'CANCELLED');

-- =====================================================
-- CRÉATION DES TABLES
-- =====================================================

-- Table des utilisateurs
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       username VARCHAR(50) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       email VARCHAR(100) NOT NULL UNIQUE,
                       first_name VARCHAR(50) NOT NULL,
                       last_name VARCHAR(50) NOT NULL,
                       role user_role NOT NULL DEFAULT 'VENDEUR',
                       enabled BOOLEAN NOT NULL DEFAULT true,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table des clients
CREATE TABLE clients (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(100) NOT NULL,
                         email VARCHAR(100) UNIQUE,
                         phone VARCHAR(20),
                         address TEXT,
                         city VARCHAR(50),
                         postal_code VARCHAR(10),
                         country VARCHAR(50) DEFAULT 'France',
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table des produits
CREATE TABLE products (
                          id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(100) NOT NULL,
                          description TEXT,
                          category VARCHAR(50),
                          price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
                          stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
                          image_url VARCHAR(255),
                          active BOOLEAN NOT NULL DEFAULT true,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table des commandes
CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,
                        order_number VARCHAR(20) NOT NULL UNIQUE,
                        order_date DATE NOT NULL DEFAULT CURRENT_DATE,
                        status order_status NOT NULL DEFAULT 'DRAFT',
                        total_amount DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
                        discount_amount DECIMAL(10,2) DEFAULT 0 CHECK (discount_amount >= 0),
                        notes TEXT,
                        user_id BIGINT NOT NULL,
                        client_id BIGINT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
                        CONSTRAINT fk_orders_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE RESTRICT
);

-- Table des lignes de commande
CREATE TABLE order_items (
                             id BIGSERIAL PRIMARY KEY,
                             quantity INTEGER NOT NULL CHECK (quantity > 0),
                             unit_price DECIMAL(10,2) NOT NULL CHECK (unit_price >= 0),
                             total_price DECIMAL(12,2) NOT NULL CHECK (total_price >= 0),
                             order_id BIGINT NOT NULL,
                             product_id BIGINT NOT NULL,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
                             CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

-- Table des factures
CREATE TABLE invoices (
                          id BIGSERIAL PRIMARY KEY,
                          invoice_number VARCHAR(20) NOT NULL UNIQUE,
                          invoice_date DATE NOT NULL DEFAULT CURRENT_DATE,
                          due_date DATE NOT NULL,
                          status invoice_status NOT NULL DEFAULT 'DRAFT',
                          total_amount DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
                          paid_amount DECIMAL(12,2) DEFAULT 0 CHECK (paid_amount >= 0),
                          notes TEXT,
                          email_sent BOOLEAN NOT NULL DEFAULT false,
                          pdf_generated BOOLEAN NOT NULL DEFAULT false,
                          order_id BIGINT NOT NULL UNIQUE,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_invoices_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
                          CONSTRAINT check_paid_amount CHECK (paid_amount <= total_amount)
);

-- Table des lignes de facture
CREATE TABLE invoice_items (
                               id BIGSERIAL PRIMARY KEY,
                               description VARCHAR(255) NOT NULL,
                               quantity INTEGER NOT NULL CHECK (quantity > 0),
                               unit_price DECIMAL(10,2) NOT NULL CHECK (unit_price >= 0),
                               total_price DECIMAL(12,2) NOT NULL CHECK (total_price >= 0),
                               invoice_id BIGINT NOT NULL,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
);

-- =====================================================
-- CRÉATION DES INDEX
-- =====================================================

-- Index sur les utilisateurs
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_enabled ON users(enabled);

-- Index sur les clients
CREATE INDEX idx_clients_name ON clients(name);
CREATE INDEX idx_clients_email ON clients(email);
CREATE INDEX idx_clients_city ON clients(city);

-- Index sur les produits
CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_active ON products(active);
CREATE INDEX idx_products_stock ON products(stock_quantity);

-- Index sur les commandes
CREATE INDEX idx_orders_number ON orders(order_number);
CREATE INDEX idx_orders_date ON orders(order_date);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_client ON orders(client_id);
CREATE INDEX idx_orders_created_at ON orders(created_at);

-- Index sur les lignes de commande
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);

-- Index sur les factures
CREATE INDEX idx_invoices_number ON invoices(invoice_number);
CREATE INDEX idx_invoices_date ON invoices(invoice_date);
CREATE INDEX idx_invoices_due_date ON invoices(due_date);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_order ON invoices(order_id);
CREATE INDEX idx_invoices_email_sent ON invoices(email_sent);

-- Index sur les lignes de facture
CREATE INDEX idx_invoice_items_invoice ON invoice_items(invoice_id);

-- =====================================================
-- CRÉATION DES FONCTIONS ET TRIGGERS
-- =====================================================

-- Fonction pour mettre à jour automatiquement updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers pour updated_at
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_clients_updated_at BEFORE UPDATE ON clients FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_products_updated_at BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_invoices_updated_at BEFORE UPDATE ON invoices FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Fonction pour générer automatiquement les numéros de commande
CREATE OR REPLACE FUNCTION generate_order_number()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.order_number IS NULL OR NEW.order_number = '' THEN
        NEW.order_number = 'CMD-' || TO_CHAR(CURRENT_DATE, 'YYYY') || '-' || LPAD(nextval('orders_id_seq')::text, 6, '0');
END IF;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger pour générer les numéros de commande
CREATE TRIGGER generate_order_number_trigger BEFORE INSERT ON orders FOR EACH ROW EXECUTE FUNCTION generate_order_number();

-- Fonction pour générer automatiquement les numéros de facture
CREATE OR REPLACE FUNCTION generate_invoice_number()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.invoice_number IS NULL OR NEW.invoice_number = '' THEN
        NEW.invoice_number = 'FACT-' || TO_CHAR(CURRENT_DATE, 'YYYY') || '-' || LPAD(nextval('invoices_id_seq')::text, 6, '0');
END IF;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger pour générer les numéros de facture
CREATE TRIGGER generate_invoice_number_trigger BEFORE INSERT ON invoices FOR EACH ROW EXECUTE FUNCTION generate_invoice_number();

-- Fonction pour calculer automatiquement le total des lignes
CREATE OR REPLACE FUNCTION calculate_line_total()
RETURNS TRIGGER AS $$
BEGIN
    NEW.total_price = NEW.quantity * NEW.unit_price;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers pour calculer les totaux des lignes
CREATE TRIGGER calculate_order_item_total BEFORE INSERT OR UPDATE ON order_items FOR EACH ROW EXECUTE FUNCTION calculate_line_total();
CREATE TRIGGER calculate_invoice_item_total BEFORE INSERT OR UPDATE ON invoice_items FOR EACH ROW EXECUTE FUNCTION calculate_line_total();

-- =====================================================
-- INSERTION DES DONNÉES DE TEST
-- =====================================================



-- Insertion des utilisateurs (mot de passe: "password" hashé avec BCrypt)
INSERT INTO users (username, password, email, first_name, last_name, role, enabled) VALUES
                                                                                        ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9P2.nRs.bvn0Stu', 'admin@company.com', 'Admin', 'System', 'ADMIN', true),
                                                                                        ('manager1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9P2.nRs.bvn0Stu', 'manager@company.com', 'Jean', 'Dupont', 'MANAGER', true),
                                                                                        ('vendeur1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9P2.nRs.bvn0Stu', 'vendeur1@company.com', 'Marie', 'Martin', 'VENDEUR', true),
                                                                                        ('vendeur2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9P2.nRs.bvn0Stu', 'vendeur2@company.com', 'Pierre', 'Durand', 'VENDEUR', true);

-- Insertion des clients
INSERT INTO clients (name, email, phone, address, city, postal_code, country) VALUES
                                                                                  ('SARL TechnoPlus', 'contact@technoplus.fr', '01.23.45.67.89', '15 Rue de la Technologie', 'Paris', '75001', 'France'),
                                                                                  ('Entreprise Martin & Fils', 'martin@martinetfils.com', '02.34.56.78.90', '28 Avenue des Affaires', 'Lyon', '69000', 'France'),
                                                                                  ('SAS InnovaCorp', 'info@innovacorp.fr', '03.45.67.89.01', '42 Boulevard de l''Innovation', 'Marseille', '13000', 'France'),
                                                                                  ('EURL Solutions Pro', 'contact@solutionspro.fr', '04.56.78.90.12', '7 Place du Commerce', 'Toulouse', '31000', 'France'),
                                                                                  ('SA Digital Services', 'hello@digitalservices.fr', '05.67.89.01.23', '33 Rue du Numérique', 'Nice', '06000', 'France'),
                                                                                  ('SASU CreativeWorks', 'team@creativeworks.fr', '06.78.90.12.34', '19 Allée de la Créativité', 'Nantes', '44000', 'France'),
                                                                                  ('SARL BizConsult', 'contact@bizconsult.fr', '07.89.01.23.45', '51 Cours du Business', 'Strasbourg', '67000', 'France'),
                                                                                  ('Entreprise GlobalTech', 'info@globaltech.fr', '08.90.12.34.56', '8 Rue Internationale', 'Bordeaux', '33000', 'France');

-- Insertion des produits
INSERT INTO products (name, description, category, price, stock_quantity, active) VALUES
                                                                                      ('Ordinateur Portable Pro', 'Ordinateur portable haute performance pour professionnels', 'Informatique', 1299.99, 25, true),
                                                                                      ('Écran 27 pouces 4K', 'Moniteur professionnel 27 pouces résolution 4K', 'Informatique', 449.99, 15, true),
                                                                                      ('Clavier Mécanique RGB', 'Clavier mécanique gaming avec rétroéclairage RGB', 'Périphériques', 129.99, 50, true),
                                                                                      ('Souris Ergonomique', 'Souris ergonomique sans fil haute précision', 'Périphériques', 79.99, 40, true),
                                                                                      ('Webcam HD Pro', 'Webcam haute définition pour visioconférences', 'Périphériques', 89.99, 30, true),
                                                                                      ('Casque Audio Premium', 'Casque audio professionnel avec réduction de bruit', 'Audio', 199.99, 20, true),
                                                                                      ('Microphone Studio', 'Microphone de studio pour enregistrements professionnels', 'Audio', 159.99, 12, true),
                                                                                      ('Tablette Graphique', 'Tablette graphique professionnelle pour designers', 'Graphisme', 299.99, 18, true),
                                                                                      ('Imprimante Laser', 'Imprimante laser couleur multifonction', 'Bureau', 399.99, 8, true),
                                                                                      ('Scanner Professionnel', 'Scanner haute résolution pour documents', 'Bureau', 249.99, 10, true),
                                                                                      ('Disque SSD 1To', 'Disque SSD externe 1To USB 3.0', 'Stockage', 119.99, 35, true),
                                                                                      ('Routeur WiFi 6', 'Routeur WiFi 6 haute performance', 'Réseau', 179.99, 22, true),
                                                                                      ('Switch Gigabit', 'Switch réseau Gigabit 24 ports', 'Réseau', 89.99, 15, true),
                                                                                      ('Câble HDMI 4K', 'Câble HDMI 4K 2 mètres', 'Accessoires', 19.99, 100, true),
                                                                                      ('Adaptateur USB-C', 'Hub USB-C multiports avec HDMI', 'Accessoires', 49.99, 60, true);

-- Insertion des commandes
INSERT INTO orders (order_date, status, total_amount, discount_amount, notes, user_id, client_id) VALUES
                                                                                                      ('2024-12-01', 'DELIVERED', 1849.97, 50.00, 'Commande urgente - Livraison express', 3, 1),
                                                                                                      ('2024-12-05', 'DELIVERED', 579.98, 0.00, 'Installation sur site demandée', 4, 2),
                                                                                                      ('2024-12-10', 'SHIPPED', 429.98, 30.00, 'Remise fidélité appliquée', 3, 3),
                                                                                                      ('2024-12-15', 'PROCESSING', 1199.99, 0.00, 'Commande standard', 4, 4),
                                                                                                      ('2024-12-18', 'CONFIRMED', 359.97, 0.00, 'Paiement par virement', 3, 5),
                                                                                                      ('2024-12-20', 'CONFIRMED', 669.98, 20.00, 'Commande groupée', 4, 6),
                                                                                                      ('2024-12-22', 'DRAFT', 249.99, 0.00, 'Devis en cours de validation', 3, 7),
                                                                                                      ('2024-12-28', 'CONFIRMED', 899.97, 100.00, 'Remise de fin d''année', 4, 8);

-- Insertion des lignes de commande
INSERT INTO order_items (quantity, unit_price, order_id, product_id) VALUES
-- Commande 1 (DELIVERED)
(1, 1299.99, 1, 1), -- Ordinateur Portable Pro
(1, 449.99, 1, 2),  -- Écran 27 pouces 4K
(1, 129.99, 1, 3),  -- Clavier Mécanique RGB

-- Commande 2 (DELIVERED)
(2, 199.99, 2, 6),  -- Casque Audio Premium x2
(2, 89.99, 2, 5),   -- Webcam HD Pro x2

-- Commande 3 (SHIPPED)
(1, 299.99, 3, 8),  -- Tablette Graphique
(1, 159.99, 3, 7),  -- Microphone Studio

-- Commande 4 (PROCESSING)
(1, 1299.99, 4, 1), -- Ordinateur Portable Pro

-- Commande 5 (CONFIRMED)
(3, 119.99, 5, 11), -- Disque SSD 1To x3

-- Commande 6 (CONFIRMED)
(1, 399.99, 6, 9),  -- Imprimante Laser
(1, 249.99, 6, 10), -- Scanner Professionnel
(1, 49.99, 6, 15),  -- Adaptateur USB-C

-- Commande 7 (DRAFT)
(1, 249.99, 7, 10), -- Scanner Professionnel

-- Commande 8 (CONFIRMED)
(1, 179.99, 8, 12), -- Routeur WiFi 6
(5, 119.99, 8, 11), -- Disque SSD 1To x5
(10, 19.99, 8, 14); -- Câble HDMI 4K x10

-- Insertion des factures (seulement pour les commandes livrées et expédiées)
INSERT INTO invoices (invoice_date, due_date, status, total_amount, paid_amount, notes, email_sent, pdf_generated, order_id) VALUES
                                                                                                                                 ('2024-12-02', '2024-12-17', 'PAID', 1799.97, 1799.97, 'Paiement reçu par carte bancaire', true, true, 1),
                                                                                                                                 ('2024-12-06', '2024-12-21', 'PAID', 579.98, 579.98, 'Paiement par virement bancaire', true, true, 2),
                                                                                                                                 ('2024-12-11', '2024-12-26', 'SENT', 399.98, 0.00, 'Facture envoyée par email', true, true, 3);

-- Insertion des lignes de facture
INSERT INTO invoice_items (description, quantity, unit_price, invoice_id) VALUES
-- Facture 1
('Ordinateur Portable Pro - Modèle Business', 1, 1299.99, 1),
('Écran 27 pouces 4K - Professionnel', 1, 449.99, 1),
('Clavier Mécanique RGB - Gaming Pro', 1, 129.99, 1),
('Remise commerciale', 1, -50.00, 1),

-- Facture 2
('Casque Audio Premium - Réduction de bruit', 2, 199.99, 2),
('Webcam HD Pro - Visioconférence', 2, 89.99, 2),

-- Facture 3
('Tablette Graphique - Design Pro', 1, 299.99, 3),
('Microphone Studio - Enregistrement', 1, 159.99, 3),
('Remise fidélité', 1, -30.00, 3);

-- =====================================================
-- VUES UTILES POUR L'APPLICATION
-- =====================================================

-- Vue des commandes avec informations client et vendeur
CREATE VIEW v_orders_details AS
SELECT
    o.id,
    o.order_number,
    o.order_date,
    o.status,
    o.total_amount,
    o.discount_amount,
    c.name as client_name,
    c.email as client_email,
    u.first_name || ' ' || u.last_name as vendeur_name,
    COUNT(oi.id) as items_count
FROM orders o
         JOIN clients c ON o.client_id = c.id
         JOIN users u ON o.user_id = u.id
         LEFT JOIN order_items oi ON o.id = oi.order_id
GROUP BY o.id, o.order_number, o.order_date, o.status, o.total_amount, o.discount_amount, c.name, c.email, u.first_name, u.last_name;

-- Vue des factures avec informations détaillées
CREATE VIEW v_invoices_details AS
SELECT
    i.id,
    i.invoice_number,
    i.invoice_date,
    i.due_date,
    i.status,
    i.total_amount,
    i.paid_amount,
    (i.total_amount - i.paid_amount) as remaining_amount,
    i.email_sent,
    o.order_number,
    c.name as client_name,
    c.email as client_email
FROM invoices i
         JOIN orders o ON i.order_id = o.id
         JOIN clients c ON o.client_id = c.id;

-- Vue des produits avec stock faible
CREATE VIEW v_low_stock_products AS
SELECT
    id,
    name,
    category,
    price,
    stock_quantity,
    CASE
        WHEN stock_quantity = 0 THEN 'Rupture de stock'
        WHEN stock_quantity <= 5 THEN 'Stock très faible'
        WHEN stock_quantity <= 10 THEN 'Stock faible'
        END as stock_status
FROM products
WHERE active = true AND stock_quantity <= 10
ORDER BY stock_quantity ASC;

-- Vue des statistiques de vente par mois
CREATE VIEW v_monthly_sales AS
SELECT
    DATE_TRUNC('month', o.order_date) as month,
    COUNT(o.id) as orders_count,
    SUM(o.total_amount) as total_sales,
    AVG(o.total_amount) as avg_order_value
FROM orders o
WHERE o.status IN ('DELIVERED', 'SHIPPED', 'PROCESSING')
GROUP BY DATE_TRUNC('month', o.order_date)
ORDER BY month DESC;

-- Vue des top clients
CREATE VIEW v_top_clients AS
SELECT
    c.id,
    c.name,
    c.email,
    COUNT(o.id) as orders_count,
    SUM(o.total_amount) as total_spent,
    AVG(o.total_amount) as avg_order_value,
    MAX(o.order_date) as last_order_date
FROM clients c
         LEFT JOIN orders o ON c.id = o.client_id AND o.status IN ('DELIVERED', 'SHIPPED', 'PROCESSING')
GROUP BY c.id, c.name, c.email
HAVING COUNT(o.id) > 0
ORDER BY total_spent DESC;

-- =====================================================
-- PROCÉDURES STOCKÉES UTILES
-- =====================================================

-- Procédure pour mettre à jour le stock après une commande
CREATE OR REPLACE FUNCTION update_stock_after_order(order_id_param BIGINT)
RETURNS VOID AS $$
DECLARE
item_record RECORD;
BEGIN
FOR item_record IN
SELECT oi.product_id, oi.quantity
FROM order_items oi
WHERE oi.order_id = order_id_param
    LOOP
UPDATE products
SET stock_quantity = stock_quantity - item_record.quantity
WHERE id = item_record.product_id;
END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Procédure pour restaurer le stock lors d'une annulation
CREATE OR REPLACE FUNCTION restore_stock_after_cancellation(order_id_param BIGINT)
RETURNS VOID AS $$
DECLARE
item_record RECORD;
BEGIN
FOR item_record IN
SELECT oi.product_id, oi.quantity
FROM order_items oi
WHERE oi.order_id = order_id_param
    LOOP
UPDATE products
SET stock_quantity = stock_quantity + item_record.quantity
WHERE id = item_record.product_id;
END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Fonction pour calculer le CA d'une période
CREATE OR REPLACE FUNCTION calculate_revenue(start_date DATE, end_date DATE)
RETURNS DECIMAL(12,2) AS $$
DECLARE
total_revenue DECIMAL(12,2);
BEGIN
SELECT COALESCE(SUM(total_amount), 0)
INTO total_revenue
FROM orders
WHERE order_date BETWEEN start_date AND end_date
  AND status IN ('DELIVERED', 'SHIPPED', 'PROCESSING');

RETURN total_revenue;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- COMMENTAIRES SUR LES TABLES
-- =====================================================

COMMENT ON TABLE users IS 'Table des utilisateurs de l''application';
COMMENT ON TABLE clients IS 'Table des clients de l''entreprise';
COMMENT ON TABLE products IS 'Table des produits/services vendus';
COMMENT ON TABLE orders IS 'Table des commandes clients';
COMMENT ON TABLE order_items IS 'Table des lignes de commande';
COMMENT ON TABLE invoices IS 'Table des factures';
COMMENT ON TABLE invoice_items IS 'Table des lignes de facture';

-- =====================================================
-- PERMISSIONS ET SÉCURITÉ
-- =====================================================

-- Création des rôles d'application
CREATE ROLE app_admin;
CREATE ROLE app_manager;
CREATE ROLE app_vendeur;
CREATE ROLE app_readonly;

-- Permissions pour app_admin (accès complet)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO app_admin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO app_admin;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO app_admin;

-- Permissions pour app_manager (lecture/écriture sauf utilisateurs)
GRANT SELECT, INSERT, UPDATE, DELETE ON clients, products, orders, order_items, invoices, invoice_items TO app_manager;
GRANT SELECT ON users TO app_manager;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO app_manager;

-- Permissions pour app_vendeur (lecture/écriture limitée)
GRANT SELECT ON users, clients, products TO app_vendeur;
GRANT SELECT, INSERT, UPDATE ON orders, order_items TO app_vendeur;
GRANT SELECT ON invoices, invoice_items TO app_vendeur;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO app_vendeur;

-- Permissions pour app_readonly (lecture seule)
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_readonly;

-- =====================================================
-- FINALISATION
-- =====================================================

-- Mise à jour des statistiques
ANALYZE;

-- Message de fin
SELECT 'Base de données initialisée avec succès !' as message,
       (SELECT COUNT(*) FROM users) as nb_users,
       (SELECT COUNT(*) FROM clients) as nb_clients,
       (SELECT COUNT(*) FROM products) as nb_products,
       (SELECT COUNT(*) FROM orders) as nb_orders,
       (SELECT COUNT(*) FROM invoices) as nb_invoices;

-- =====================================================
--
-- =====================================================