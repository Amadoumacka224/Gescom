-- =====================================================================
-- GESCOM — Données de référence (contexte belge)
-- Table : public.categories
-- 12 enregistrement(s)
-- Généré automatiquement — compatible PostgreSQL 17 / Flyway
-- =====================================================================

INSERT INTO public.categories (id, name, code, description, active, vat_rate, created_at, updated_at) VALUES
                                                                                                          (1, 'Ordinateurs & Accessoires', 'PC-101', 'Portables, fixes, écrans, périphériques et impression', TRUE, '21.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (2, 'Téléphonie & Mobilité', 'TEL-202', 'Smartphones, chargeurs, protections et audio nomade', TRUE, '21.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (3, 'Alimentation & Épicerie', 'FOOD-303', 'Épicerie, boissons et spécialités belges', TRUE, '6.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (4, 'Fournitures de bureau', 'OFF-404', 'Écriture, classement et petit matériel de bureau', TRUE, '21.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (5, 'Électroménager', 'ELEC-505', 'Petit et gros électroménager pour la maison', TRUE, '21.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (6, 'Réseau & Sécurité', 'NET-606', 'Routeurs, switchs, caméras IP et câblage', TRUE, '21.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (7, 'Hygiène & Beauté', 'BEAU-707', 'Soins du corps, du visage et des cheveux', TRUE, '21.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (8, 'Animalerie', 'PET-808', 'Alimentation et accessoires pour animaux', TRUE, '6.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (9, 'Sport & Loisirs', 'SPT-909', 'Fitness, outdoor et équipements de loisirs', TRUE, '21.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (10, 'Jardin & Extérieur', 'JRD-010', 'Outillage, mobilier de jardin et barbecue', TRUE, '21.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (11, 'Livres & Papeterie', 'BOOK-011', 'Livres, carnets et articles de papeterie', TRUE, '6.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                          (12, 'Auto & Moto', 'CAR-012', 'Entretien, accessoires et équipements véhicules', TRUE, '21.00', '2026-01-05 09:00:00', '2026-01-05 09:00:00');

SELECT setval('public.categories_id_seq', 12, true);