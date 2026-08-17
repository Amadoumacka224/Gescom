-- Journal des encaissements d'abonnement — le registre de revenus de la plateforme.
--
-- A ne pas confondre avec `payments`, qui trace les transactions carte du terminal de
-- caisse d'une entreprise (encaissement de ses propres factures clients). Ici il s'agit
-- de ce que les entreprises versent a GESCOM.
--
-- Les echecs sont conserves au meme titre que les succes : c'est ce qui permet au
-- back-office d'afficher un taux de reussite et de reperer les impayes. Le montant
-- reste donc renseigne meme quand le statut est FAILED.
CREATE TABLE saas_payments (
                               id BIGSERIAL PRIMARY KEY,
                               company_id BIGINT NOT NULL REFERENCES companies(id),
                               subscription_id BIGINT REFERENCES subscriptions(id),
                               reference VARCHAR(50) UNIQUE NOT NULL,
                               amount NUMERIC(10, 2) NOT NULL,
                               currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                   CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
                               method VARCHAR(20) NOT NULL DEFAULT 'TRANSFER'
                                   CHECK (method IN ('TRANSFER', 'CARD', 'DIRECT_DEBIT', 'CASH')),
                               -- Periode d'abonnement couverte par ce versement : deux paiements
                               -- d'un meme mois se distinguent ainsi sans ambiguite.
                               period_start TIMESTAMP,
                               period_end TIMESTAMP,
                               paid_at TIMESTAMP,
                               failure_message VARCHAR(255),
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_saas_payments_company ON saas_payments(company_id);
CREATE INDEX idx_saas_payments_subscription ON saas_payments(subscription_id);
CREATE INDEX idx_saas_payments_status ON saas_payments(status);
CREATE INDEX idx_saas_payments_paid_at ON saas_payments(paid_at);
