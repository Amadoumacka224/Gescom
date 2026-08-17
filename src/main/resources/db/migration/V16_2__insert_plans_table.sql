-- Catalogue de depart. Le tarif annuel vaut dix mensualites : deux mois offerts,
-- pratique courante en SaaS B2B et remise que le calcul du MRR doit lisser
-- (un abonnement annuel de 290 EUR pese 290/12 dans le MRR, pas 29).
INSERT INTO plans (code, name, description, monthly_price, yearly_price, max_users, max_products, trial_days, sort_order) VALUES
    ('ESSENTIEL',  'Essentiel',  'Caisse, stock et facturation pour une petite equipe',        29.00,  290.00,    3,   500, 14, 1),
    ('PRO',        'Pro',        'Multi-caisses, rapports avances et retours clients',          71.39,  713.90,   10,  5000, 14, 2),
    ('ENTREPRISE', 'Entreprise', 'Utilisateurs et catalogue illimites, accompagnement dedie',  199.00, 1990.00, NULL,  NULL, 30, 3);
