-- Abonnement d'une entreprise a une formule.
--
-- `amount` fige le montant reellement facture a la souscription au lieu de relire
-- plans.monthly_price : un changement de tarif du catalogue ne doit pas reecrire
-- retroactivement le MRR des contrats en cours ni les paiements deja encaisses.
--
-- L'historique est conserve : resilier n'efface pas la ligne, cela renseigne
-- canceled_at et bascule le statut. C'est cette trace qui rend le churn calculable.
CREATE TABLE subscriptions (
                               id BIGSERIAL PRIMARY KEY,
                               company_id BIGINT NOT NULL REFERENCES companies(id),
                               plan_id BIGINT NOT NULL REFERENCES plans(id),
                               status VARCHAR(20) NOT NULL DEFAULT 'TRIALING'
                                   CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
                               billing_period VARCHAR(10) NOT NULL DEFAULT 'MONTHLY'
                                   CHECK (billing_period IN ('MONTHLY', 'YEARLY')),
                               amount NUMERIC(10, 2) NOT NULL,
                               currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
                               started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               -- Fenetre de facturation courante : `current_period_end` est la date
                               -- de renouvellement, et sert aussi a reperer les echeances depassees.
                               current_period_start TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               current_period_end TIMESTAMP NOT NULL,
                               canceled_at TIMESTAMP,
                               cancel_reason VARCHAR(255),
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_subscriptions_company ON subscriptions(company_id);
CREATE INDEX idx_subscriptions_plan ON subscriptions(plan_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE INDEX idx_subscriptions_canceled_at ON subscriptions(canceled_at);

-- Une entreprise n'a qu'un abonnement vivant a la fois ; les contrats resilies ou expires
-- s'accumulent librement pour garder l'historique. L'index partiel exprime exactement cette
-- regle en base, la ou un UNIQUE simple interdirait tout reabonnement apres resiliation.
CREATE UNIQUE INDEX uq_subscriptions_active_per_company
    ON subscriptions(company_id)
    WHERE status IN ('TRIALING', 'ACTIVE', 'PAST_DUE');
