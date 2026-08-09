-- Reglages de la plateforme elle-meme, distincts de `settings` qui est le parametrage
-- metier d'une entreprise cliente (raison sociale, TVA, prefixe de facture...).
--
-- Singleton persistant, sur le meme modele que `settings` : une seule ligne, creee a la
-- volee avec ses valeurs par defaut au premier acces. La contrainte CHECK sur l'id est ce
-- qui rend le singleton reel plutot que conventionnel — sans elle, rien n'empecherait une
-- seconde ligne d'apparaitre et de rendre le « premier acces » dependant de l'ordre de tri.
--
-- Les valeurs par defaut ci-dessous reprennent exactement les constantes qui etaient
-- ecrites en dur dans PlatformMetricsService : la bascule ne change donc rien a ce que le
-- tableau de bord affichait jusqu'ici.
CREATE TABLE platform_settings (
                                   id BIGINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),

                                   -- Fenetre du bloc « renouvellements a venir ».
                                   renewal_window_days INTEGER NOT NULL DEFAULT 30
                                       CHECK (renewal_window_days BETWEEN 1 AND 365),

                                   -- Anticipation de l'alerte « fin d'essai ».
                                   trial_alert_days INTEGER NOT NULL DEFAULT 7
                                       CHECK (trial_alert_days BETWEEN 1 AND 90),

                                   -- Profondeur de la courbe de revenus.
                                   revenue_history_months INTEGER NOT NULL DEFAULT 12
                                       CHECK (revenue_history_months BETWEEN 1 AND 60),

                                   -- Penalites du score de sante, en points sur 100.
                                   overdue_penalty_points INTEGER NOT NULL DEFAULT 5
                                       CHECK (overdue_penalty_points BETWEEN 0 AND 50),
                                   failed_payment_penalty_points INTEGER NOT NULL DEFAULT 2
                                       CHECK (failed_payment_penalty_points BETWEEN 0 AND 50),

                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
