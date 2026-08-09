-- =====================================================================
-- GESCOM — Données de référence (contexte belge)
-- Table : public.payments
-- 6 enregistrement(s)
-- Généré automatiquement — compatible PostgreSQL 17 / Flyway
-- =====================================================================

INSERT INTO public.payments (id, provider, intent_id, amount, currency, status, card_brand, card_last4, failure_message, simulated, confirmed_at, invoice_id, created_at, updated_at) VALUES
                                                                                                                                                                                          (1, 'STRIPE', 'pi_2026_000001', 265.72, 'EUR', 'SUCCEEDED', 'BANCONTACT', '2718', NULL, TRUE, '2026-02-04 10:18:00', 3, '2026-02-04 10:18:00', '2026-02-04 10:18:00'),
                                                                                                                                                                                          (2, 'STRIPE', 'pi_2026_000002', 562.95, 'EUR', 'SUCCEEDED', 'BANCONTACT', '1915', NULL, TRUE, '2026-06-07 10:50:00', 5, '2026-06-07 10:50:00', '2026-06-07 10:50:00'),
                                                                                                                                                                                          (3, 'STRIPE', 'pi_2026_000003', 98.34, 'EUR', 'SUCCEEDED', 'MASTERCARD', '7863', NULL, TRUE, '2026-05-17 14:57:00', 11, '2026-05-17 14:57:00', '2026-05-17 14:57:00'),
                                                                                                                                                                                          (4, 'STRIPE', 'pi_2026_000004', 1810.86, 'EUR', 'SUCCEEDED', 'VISA', '6269', NULL, TRUE, '2026-03-01 18:51:00', 12, '2026-03-01 18:51:00', '2026-03-01 18:51:00'),
                                                                                                                                                                                          (5, 'STRIPE', 'pi_2026_000005', 332.15, 'EUR', 'SUCCEEDED', 'BANCONTACT', '6286', NULL, TRUE, '2026-07-29 16:44:00', 13, '2026-07-29 16:44:00', '2026-07-29 16:44:00'),
                                                                                                                                                                                          (6, 'STRIPE', 'pi_2026_000006', 5618.57, 'EUR', 'SUCCEEDED', 'VISA', '3181', NULL, TRUE, '2026-03-18 16:22:00', 18, '2026-03-18 16:22:00', '2026-03-18 16:22:00');

SELECT setval('public.payments_id_seq', 6, true);