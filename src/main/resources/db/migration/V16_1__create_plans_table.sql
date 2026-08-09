-- Catalogue commercial : les formules auxquelles une entreprise peut souscrire.
--
-- Les deux prix sont stockes cote a cote plutot que derives l'un de l'autre : le tarif
-- annuel porte generalement une remise, et c'est le montant reellement facture qui doit
-- servir de base au calcul du MRR (voir PlatformMetricsService.mrr()).
--
-- max_users / max_products a NULL signifient « illimite ».
CREATE TABLE plans (
                       id BIGSERIAL PRIMARY KEY,
                       code VARCHAR(30) UNIQUE NOT NULL,
                       name VARCHAR(100) NOT NULL,
                       description VARCHAR(255),
                       monthly_price NUMERIC(10, 2) NOT NULL,
                       yearly_price NUMERIC(10, 2) NOT NULL,
                       max_users INTEGER,
                       max_products INTEGER,
                       trial_days INTEGER NOT NULL DEFAULT 14,
                       -- Une formule retiree du catalogue reste en base : les abonnements
                       -- deja souscrits continuent d'y pointer et de compter dans le MRR.
                       active BOOLEAN NOT NULL DEFAULT TRUE,
                       sort_order INTEGER NOT NULL DEFAULT 0,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
