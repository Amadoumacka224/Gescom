-- Entreprise cliente de la plateforme GESCOM : c'est le « tenant ».
--
-- A ne pas confondre avec `clients`, qui designe les clients du magasin d'une entreprise.
-- Chaque ligne ici correspond a une societe abonnee a GESCOM ; toutes les tables metier
-- portent desormais un company_id qui pointe vers cette table (voir V19_1).
--
-- Le slug sert d'identifiant stable et lisible (URL du back-office, references support) :
-- il est unique et ne change pas, meme si la raison sociale evolue.
CREATE TABLE companies (
                           id BIGSERIAL PRIMARY KEY,
                           name VARCHAR(150) NOT NULL,
                           slug VARCHAR(80) UNIQUE NOT NULL,
                           email VARCHAR(100) NOT NULL,
                           phone VARCHAR(30),
                           address VARCHAR(255),
                           city VARCHAR(100),
                           postal_code VARCHAR(20),
                           country VARCHAR(100) NOT NULL DEFAULT 'Belgique',
                           tax_id VARCHAR(50),
                           -- Cycle de vie commercial du compte, distinct du statut d'abonnement :
                           -- une entreprise SUSPENDED garde ses donnees mais ses utilisateurs
                           -- ne peuvent plus se connecter (voir CompanyAccessFilter).
                           status VARCHAR(20) NOT NULL DEFAULT 'TRIAL'
                               CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'CANCELED')),
                           trial_ends_at TIMESTAMP,
                           canceled_at TIMESTAMP,
                           notes VARCHAR(500),
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_companies_status ON companies(status);
CREATE INDEX idx_companies_created_at ON companies(created_at);
