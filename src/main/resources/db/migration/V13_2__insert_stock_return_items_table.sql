-- =====================================================================
-- GESCOM — Données de référence (contexte belge)
-- Table : public.stock_return_items
-- 3 enregistrement(s)
-- Généré automatiquement — compatible PostgreSQL 17 / Flyway
-- =====================================================================

INSERT INTO public.stock_return_items (id, quantity, unit_price, refund_amount, reason, treatment, product_id, replacement_product_id, stock_return_id, created_at) VALUES
                                                                                                                                                                        (1, 4, 119.00, 476.00, 'WRONG_ITEM', 'RESTOCK', 142, NULL, 1, '2026-03-06 09:55:00'),
                                                                                                                                                                        (2, 2, 2.29, 4.58, 'OTHER', 'RESTOCK', 38, NULL, 2, '2026-05-21 21:40:00'),
                                                                                                                                                                        (3, 2, 189.90, 379.80, 'ORDER_ERROR', 'EXCHANGE', 7, 76, 3, '2026-02-23 14:52:00');

SELECT setval('public.stock_return_items_id_seq', 3, true);