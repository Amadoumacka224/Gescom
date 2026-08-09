-- La ligne unique, semee avec les valeurs par defaut de la table.
-- PlatformSettingsService sait la creer a la volee si elle manque, mais l'inserer ici evite
-- que le tout premier appel au tableau de bord soit celui qui ecrit en base.
INSERT INTO platform_settings (id) VALUES (1);
