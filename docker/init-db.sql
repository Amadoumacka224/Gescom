-- Script d'initialisation de la base de données GESCOM
-- Ce script est exécuté lors du premier démarrage du conteneur PostgreSQL

-- Créer des extensions utiles
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Créer un utilisateur supplémentaire pour la lecture seule (optionnel)
-- CREATE USER gescom_readonly WITH PASSWORD 'readonly_password';

-- Configuration de la base de données
SET timezone = 'Europe/Paris';

-- Vous pouvez ajouter ici des données d'initialisation si nécessaire
-- INSERT INTO table_name (column1, column2) VALUES ('value1', 'value2');

-- Exemple de données de test (décommentez si nécessaire)
/*
-- Insérer des données de test pour les clients
INSERT INTO client (id, name, email, phone_number, company_name, created_at) VALUES 
(1, 'Client Test 1', 'client1@test.com', '0123456789', 'Entreprise Test 1', NOW()),
(2, 'Client Test 2', 'client2@test.com', '0987654321', 'Entreprise Test 2', NOW());

-- Insérer des données de test pour les produits
INSERT INTO product (id, name, reference, unit_price, stock, unit, vat_rate, is_active, created_at) VALUES 
(1, 'Produit Test 1', 'REF001', 99.99, 100, 'pièce', 20.0, true, NOW()),
(2, 'Produit Test 2', 'REF002', 199.99, 50, 'pièce', 20.0, true, NOW());
*/

-- Afficher un message de confirmation
DO $$
BEGIN
    RAISE NOTICE 'Base de données GESCOM initialisée avec succès!';
END $$;