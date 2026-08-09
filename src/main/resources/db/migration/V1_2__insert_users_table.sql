-- =====================================================================
-- GESCOM — Données de référence (contexte belge)
-- Table : public.users
-- 4 enregistrement(s)
-- Généré automatiquement — compatible PostgreSQL 17 / Flyway
-- =====================================================================

INSERT INTO public.users (id, username, email, password, first_name, last_name, phone, role, active, created_at, updated_at) VALUES
                                                                                                                                 (1, 'admin', 'admin@gescom.be', '$2a$10$dWOlIkPCrFxmR88dlz3N3.cv9yATcTWt/x2N1FsWR1b2pLfPU/YQW', 'Amadou', 'Diallo', '0467613461', 'ADMIN', TRUE, '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                                                 (2, 'cbernard', 'c.bernard@gescom.be', '$2a$10$pAohe0cwcVwsyCxfHTRm3u9iqfDVnVmou3kKpHV1bTmligyCC56ra', 'Caroline', 'Bernard', '0471223344', 'CAISSIER', TRUE, '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                                                 (3, 'mdubois', 'm.dubois@gescom.be', '$2a$10$pAohe0cwcVwsyCxfHTRm3u9iqfDVnVmou3kKpHV1bTmligyCC56ra', 'Mathieu', 'Dubois', '0472334455', 'CAISSIER', TRUE, '2026-01-05 09:00:00', '2026-01-05 09:00:00'),
                                                                                                                                 (4, 'speeters', 's.peeters@gescom.be', '$2a$10$pAohe0cwcVwsyCxfHTRm3u9iqfDVnVmou3kKpHV1bTmligyCC56ra', 'Sofie', 'Peeters', '0473445566', 'CAISSIER', TRUE, '2026-01-05 09:00:00', '2026-01-05 09:00:00');

SELECT setval('public.users_id_seq', 4, true);