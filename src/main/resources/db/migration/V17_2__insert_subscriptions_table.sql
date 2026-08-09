-- L'entreprise historique est abonnee a la formule Pro, facturee au mois.
-- Aucun autre abonnement n'est invente : le back-office proprietaire doit refleter
-- l'etat reel de la base, pas un jeu de demonstration.
INSERT INTO subscriptions (company_id, plan_id, status, billing_period, amount, current_period_end)
SELECT c.id,
       p.id,
       'ACTIVE',
       'MONTHLY',
       p.monthly_price,
       CURRENT_TIMESTAMP + INTERVAL '1 month'
FROM companies c
         CROSS JOIN plans p
WHERE c.slug = 'gescom'
  AND p.code = 'PRO';
