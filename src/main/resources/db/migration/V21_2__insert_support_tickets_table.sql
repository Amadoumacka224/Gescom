-- =====================================================================
-- GESCOM — Donnees de demonstration : billetterie du support
--
-- Ce script arrive apres V22_1 alors que sa version lui est inferieure : c'est la
-- consequence directe de la convention du projet, qui numerote par table et non par ordre
-- chronologique. `spring.flyway.out-of-order=true` autorise ce cas — voir la note dans
-- application.properties.
--
-- Dates relatives a l'execution (CURRENT_TIMESTAMP - INTERVAL) et non figees : un jeu de
-- demonstration date de janvier perd tout son sens consulte en aout, et l'ecran Support se
-- lit precisement par l'anciennete des demandes.
--
-- L'auteur cote plateforme est le SUPER_ADMIN quand il existe, sinon le premier ADMIN :
-- le compte proprietaire est cree au demarrage par PlatformAdminBootstrap, pas par Flyway,
-- et peut donc manquer au moment ou ce script s'execute.
-- =====================================================================

INSERT INTO support_tickets (reference, company_id, subject, status, priority, category,
                             contact_user_id, opened_by_id, resolved_at, closed_at,
                             created_at, updated_at)
SELECT TO_CHAR(t.created_at, '"TK-"YYYYMM') || '-' || t.seq,
       (SELECT id FROM companies WHERE slug = 'gescom'),
       t.subject, t.status, t.priority, t.category,
       (SELECT id FROM users WHERE username = t.contact),
       COALESCE((SELECT id FROM users WHERE role = 'SUPER_ADMIN' ORDER BY id LIMIT 1),
                (SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id LIMIT 1)),
       t.resolved_at, t.closed_at, t.created_at, t.updated_at
FROM (VALUES
    ('001', 'Caisse 2 bloquee sur la derniere vente', 'IN_PROGRESS', 'HIGH', 'TECHNICAL',
     'cbernard', NULL::TIMESTAMP, NULL::TIMESTAMP,
     CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '4 hours'),

    ('002', 'Ecart de TVA sur la facture du mois', 'WAITING_CUSTOMER', 'NORMAL', 'BILLING',
     'admin', NULL, NULL,
     CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),

    ('003', 'Ajouter un caissier supplementaire', 'RESOLVED', 'LOW', 'ACCOUNT',
     'admin', CURRENT_TIMESTAMP - INTERVAL '6 days', NULL,
     CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '6 days'),

    ('004', 'Export comptable au format CODA', 'OPEN', 'NORMAL', 'FEATURE',
     'admin', NULL, NULL,
     CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'),

    ('005', 'Lecteur de codes-barres non reconnu', 'CLOSED', 'URGENT', 'TECHNICAL',
     'mdubois', CURRENT_TIMESTAMP - INTERVAL '13 days', CURRENT_TIMESTAMP - INTERVAL '12 days',
     CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '12 days')
) AS t(seq, subject, status, priority, category, contact, resolved_at, closed_at, created_at, updated_at)
WHERE EXISTS (SELECT 1 FROM companies WHERE slug = 'gescom');

-- Fils de discussion. Les tickets sont retrouves par leur objet, stable, et non par leur
-- reference, qui depend du mois d'execution.
INSERT INTO support_ticket_messages (ticket_id, author_id, body, internal, created_at)
SELECT tk.id,
       COALESCE((SELECT id FROM users WHERE username = m.author),
                (SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id LIMIT 1)),
       m.body, m.internal, tk.created_at + m.delay
FROM (VALUES
    ('Caisse 2 bloquee sur la derniere vente',
     'admin', 'La caisse 2 reste figee sur le dernier ticket depuis ce matin. Redemarrage sans effet.',
     FALSE, INTERVAL '0 minute'),
    ('Caisse 2 bloquee sur la derniere vente',
     NULL, 'Verifie cote journal : aucun mouvement de stock depuis 09h12. Piste synchronisation.',
     TRUE, INTERVAL '35 minutes'),
    ('Caisse 2 bloquee sur la derniere vente',
     NULL, 'Service de synchronisation relance. Merci de confirmer que la caisse repart.',
     FALSE, INTERVAL '2 hours'),

    ('Ecart de TVA sur la facture du mois',
     'admin', 'La facture FACT-2026-0142 affiche 21 % sur une ligne qui devrait etre a 6 %.',
     FALSE, INTERVAL '0 minute'),
    ('Ecart de TVA sur la facture du mois',
     NULL, 'Le taux est celui de la fiche article. Pouvez-vous confirmer la categorie du produit ?',
     FALSE, INTERVAL '3 hours'),

    ('Ajouter un caissier supplementaire',
     'admin', 'Nous embauchons une personne en renfort, il lui faut un acces caisse.',
     FALSE, INTERVAL '0 minute'),
    ('Ajouter un caissier supplementaire',
     NULL, 'Compte cree. La formule Pro autorise jusqu''a 10 utilisateurs, vous en avez 4.',
     FALSE, INTERVAL '1 day'),

    ('Export comptable au format CODA',
     'admin', 'Notre comptable demande un export CODA en plus du CSV actuel.',
     FALSE, INTERVAL '0 minute'),

    ('Lecteur de codes-barres non reconnu',
     'mdubois', 'Le lecteur Zebra branche ce matin n''est pas detecte par la caisse.',
     FALSE, INTERVAL '0 minute'),
    ('Lecteur de codes-barres non reconnu',
     NULL, 'Modele non HID. Bascule en mode clavier cote peripherique, fonctionne ensuite.',
     FALSE, INTERVAL '5 hours'),
    ('Lecteur de codes-barres non reconnu',
     NULL, 'A documenter dans la procedure d''installation : le mode HID n''est pas le defaut.',
     TRUE, INTERVAL '6 hours')
) AS m(subject, author, body, internal, delay)
JOIN support_tickets tk ON tk.subject = m.subject;
