-- Entreprise porteuse des donnees deja presentes en base.
--
-- Avant la bascule multi-tenant, toute la base appartenait implicitement a une seule
-- societe, decrite par la ligne unique de `settings`. On la materialise ici pour que
-- V19_1 puisse rattacher l'existant a un proprietaire reel plutot qu'a un identifiant
-- invente. Le slug 'gescom' est le point d'ancrage utilise par ce backfill.
INSERT INTO companies (name, slug, email, phone, country, status)
SELECT COALESCE(s.company_name, 'Gescom SA'),
       'gescom',
       COALESCE(s.company_email, 'contact@gescom.com'),
       s.company_phone,
       COALESCE(s.company_country, 'Belgique'),
       'ACTIVE'
FROM settings s
ORDER BY s.id
LIMIT 1;

-- Filet de securite : si `settings` etait vide, la requete ci-dessus n'aurait rien insere
-- et le backfill de V19_1 echouerait faute de proprietaire.
INSERT INTO companies (name, slug, email, country, status)
SELECT 'Gescom SA', 'gescom', 'contact@gescom.com', 'Belgique', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM companies WHERE slug = 'gescom');
