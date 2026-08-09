-- Premiere echeance de l'abonnement historique, deja reglee.
-- Les revenus affiches par le back-office proviennent exclusivement de cette table :
-- on n'y injecte donc que des mouvements reels, jamais un historique fabrique.
INSERT INTO saas_payments (company_id, subscription_id, reference, amount, currency, status, method,
                           period_start, period_end, paid_at)
SELECT s.company_id,
       s.id,
       'SP-' || TO_CHAR(s.current_period_start, 'YYYYMM') || '-' || LPAD(s.company_id::TEXT, 4, '0'),
       s.amount,
       s.currency,
       'SUCCEEDED',
       'TRANSFER',
       s.current_period_start,
       s.current_period_end,
       s.current_period_start
FROM subscriptions s
         JOIN companies c ON c.id = s.company_id
WHERE c.slug = 'gescom';
