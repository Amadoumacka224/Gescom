-- =====================================================================
-- GESCOM — Données de référence (contexte belge)
-- Table : public.stock_returns
-- 3 enregistrement(s)
-- Généré automatiquement — compatible PostgreSQL 17 / Flyway
-- =====================================================================

INSERT INTO public.stock_returns (id, return_number, total_quantity, refund_amount, notes, order_id, invoice_id, user_id, created_at, updated_at) VALUES
                                                                                                                                                      (1, 'RET-2026-0001', 4, 476.00, NULL, 18, 12, 4, '2026-03-06 09:55:00', '2026-03-06 09:55:00'),
                                                                                                                                                      (2, 'RET-2026-0002', 2, 4.58, NULL, 4, 4, 2, '2026-05-21 21:40:00', '2026-05-21 21:40:00'),
                                                                                                                                                      (3, 'RET-2026-0003', 2, 379.80, NULL, 32, 17, 4, '2026-02-23 14:52:00', '2026-02-23 14:52:00');

SELECT setval('public.stock_returns_id_seq', 3, true);