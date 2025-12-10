-- Script d'initialisation de la base de données GESCOM
-- Créer un utilisateur admin par défaut
-- Mot de passe: admin123 (hashé avec BCrypt)

INSERT INTO users (username, email, password, first_name, last_name, phone, role, active, created_at, updated_at)
VALUES ('admin', 'admin@gescom.com', '$2a$10$xZ5J5J5J5J5J5J5J5J5J5OqKqKqKqKqKqKqKqKqKqKqKqKqKqKqKq', 'Admin', 'System', '0600000000', 'ADMIN', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Créer un caissier par défaut
-- Mot de passe: caissier123
INSERT INTO users (username, email, password, first_name, last_name, phone, role, active, created_at, updated_at)
VALUES ('caissier', 'caissier@gescom.com', '$2a$10$xZ5J5J5J5J5J5J5J5J5J5OqKqKqKqKqKqKqKqKqKqKqKqKqKqKqKq', 'Caissier', 'Test', '0600000001', 'CAISSIER', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Note: Les mots de passe ci-dessus sont des exemples.
-- Vous devrez les hasher correctement avec BCrypt avant de les utiliser en production.
-- Utilisez un service en ligne BCrypt ou créez-les via l'API après le premier démarrage.
