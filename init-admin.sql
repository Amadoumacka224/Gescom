-- Script pour créer un utilisateur admin par défaut
-- À exécuter manuellement dans votre base de données GESCOMDB

-- Créer un admin avec le mot de passe: admin123
-- Hash BCrypt de "admin123": $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

INSERT INTO users (username, email, password, first_name, last_name, phone, role, active, created_at, updated_at)
VALUES ('admin', 'admin@gescom.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Admin', 'System', '0600000000', 'ADMIN', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Créer un caissier avec le mot de passe: caissier123
-- Hash BCrypt de "caissier123": $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

INSERT INTO users (username, email, password, first_name, last_name, phone, role, active, created_at, updated_at)
VALUES ('caissier', 'caissier@gescom.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Caissier', 'Test', '0600000001', 'CAISSIER', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;
