-- Journal des evenements notables de la plateforme, avec etat de lecture.
--
-- A ne pas confondre avec les alertes du tableau de bord : celles-ci decrivent l'etat
-- courant du parc et sont recalculees a chaque affichage (une echeance impayee cesse
-- d'apparaitre des qu'elle est reglee). Une notification, elle, consigne un fait date qui
-- a eu lieu et reste consultable ensuite — les deux ne se remplacent pas.
--
-- `company_id` est nullable : tout evenement n'est pas rattache a une entreprise.
CREATE TABLE platform_notifications (
                                        id BIGSERIAL PRIMARY KEY,
                                        type VARCHAR(40) NOT NULL,
                                        severity VARCHAR(10) NOT NULL DEFAULT 'INFO'
                                            CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
                                        title VARCHAR(150) NOT NULL,
                                        message VARCHAR(500),
                                        company_id BIGINT REFERENCES companies(id),
                                        -- Cible facultative, pour que l'interface propose un lien
                                        -- vers l'ecran concerne (une entreprise, un paiement...).
                                        entity VARCHAR(50),
                                        entity_id BIGINT,
                                        read_at TIMESTAMP,
                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_platform_notifications_created_at ON platform_notifications(created_at);
CREATE INDEX idx_platform_notifications_company ON platform_notifications(company_id);

-- Index partiel sur les seules non-lues : c'est la requete du compteur, jouee a chaque
-- affichage de l'interface, et elle ne doit jamais parcourir tout l'historique.
CREATE INDEX idx_platform_notifications_unread
    ON platform_notifications(created_at DESC)
    WHERE read_at IS NULL;
