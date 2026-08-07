-- Mots de passe stockes en BCrypt : c'est ce que compare le BCryptPasswordEncoder au
-- login. Un mot de passe en clair ici passerait l'insertion mais rendrait le compte
-- inutilisable (aucune correspondance possible).
-- Identifiants en clair correspondants : admin / admin123, caissier1 / caissier123.
INSERT INTO users (username, email, password, first_name, last_name, role) VALUES
    ('admin', 'admin@gescom.com', '$2a$10$dWOlIkPCrFxmR88dlz3N3.cv9yATcTWt/x2N1FsWR1b2pLfPU/YQW', 'Admin', 'Super', 'ADMIN'),
    ('caissier1', 'caissier@gescom.com', '$2a$10$pAohe0cwcVwsyCxfHTRm3u9iqfDVnVmou3kKpHV1bTmligyCC56ra', 'Jean', 'Dupont', 'CAISSIER');
