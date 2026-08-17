-- =====================================================================
-- GESCOM — Donnees de demonstration : journal des evenements de la plateforme
--
-- Comme V21_2, ce script peut arriver apres une version superieure deja appliquee : la
-- numerotation du projet suit les tables, pas la chronologie. Autorise par
-- `spring.flyway.out-of-order=true`.
--
-- Dates relatives, meme raison qu'en V21_2 : une liste de notifications se lit par son
-- anciennete, et un jeu fige perdrait tout sens quelques mois plus tard.
--
-- Les plus recentes sont laissees non lues (read_at NULL) pour que le badge du menu porte
-- une valeur des le premier affichage ; les plus anciennes sont marquees lues, ce qui donne
-- a l'ecran ses deux etats au lieu d'un seul.
--
-- `entity` / `entity_id` restent nuls ici : les identifiants cibles dependraient des lignes
-- semees par les autres scripts, et un lien pointant a cote vaut moins que pas de lien.
-- =====================================================================

INSERT INTO platform_notifications (type, severity, title, message, company_id,
                                    entity, entity_id, read_at, created_at)
SELECT n.type, n.severity, n.title, n.message,
       (SELECT id FROM companies WHERE slug = 'gescom'),
       NULL, NULL, n.read_at, n.created_at
FROM (VALUES
    ('SUPPORT_TICKET_OPENED', 'WARNING',
     'Ticket ouvert : caisse 2 bloquee',
     'Gescom SA — priorite haute, signale par Caroline Bernard',
     NULL::TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '2 days'),

    ('PAYMENT_FAILED', 'CRITICAL',
     'Paiement refuse : Gescom SA',
     'Domiciliation rejetee — provision insuffisante',
     NULL, CURRENT_TIMESTAMP - INTERVAL '3 days'),

    ('SUBSCRIPTION_RENEWED', 'INFO',
     'Abonnement renouvele : Gescom SA',
     'Formule Pro, mensuel — 71,39 EUR encaisses',
     NULL, CURRENT_TIMESTAMP - INTERVAL '6 hours'),

    ('TRIAL_ENDING', 'WARNING',
     'Periode d''essai bientot terminee',
     'Il reste 3 jours avant la fin de l''essai',
     CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP - INTERVAL '8 days'),

    ('COMPANY_PROVISIONED', 'INFO',
     'Nouveau client : Gescom SA',
     'Compte ouvert avec l''administrateur admin',
     CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '25 days'),

    ('SUPPORT_TICKET_OPENED', 'INFO',
     'Ticket ouvert : export comptable CODA',
     'Gescom SA — demande d''evolution',
     NULL, CURRENT_TIMESTAMP - INTERVAL '1 day'),

    ('SUBSCRIPTION_PAST_DUE', 'CRITICAL',
     'Echeance impayee : Gescom SA',
     'Le contrat reste actif, relance a prevoir',
     CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '3 days'),

    ('COMPANY_SUSPENDED', 'WARNING',
     'Compte suspendu puis retabli',
     'Suspension levee apres regularisation',
     CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '14 days')
) AS n(type, severity, title, message, read_at, created_at)
WHERE EXISTS (SELECT 1 FROM companies WHERE slug = 'gescom');
