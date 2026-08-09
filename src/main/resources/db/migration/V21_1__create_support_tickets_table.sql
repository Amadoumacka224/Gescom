-- Billetterie du support, cote exploitant.
--
-- Les tickets ne portent pas de cloisonnement : ils referencent une entreprise cliente mais
-- appartiennent a l'exploitation de la plateforme, comme `companies` ou `subscriptions`.
-- Leur seule porte d'entree est /api/platform/**, ou @PreAuthorize tient lieu de controle.
--
-- Le ticket est ouvert par l'operateur a partir d'un appel ou d'un courriel : il n'existe
-- pas d'ecran client pour en deposer un, et c'est assume — le canal reste humain, GESCOM
-- n'en garde que la trace et le suivi.
CREATE TABLE support_tickets (
                                 id BIGSERIAL PRIMARY KEY,
                                 reference VARCHAR(30) UNIQUE NOT NULL,
                                 company_id BIGINT NOT NULL REFERENCES companies(id),
                                 subject VARCHAR(200) NOT NULL,
                                 status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                                     CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED')),
                                 priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL'
                                     CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
                                 category VARCHAR(20) NOT NULL DEFAULT 'OTHER'
                                     CHECK (category IN ('TECHNICAL', 'BILLING', 'ACCOUNT', 'FEATURE', 'OTHER')),
                                 -- Interlocuteur chez le client, quand il est identifie. Nullable :
                                 -- un appel du standard ne permet pas toujours de le rattacher.
                                 contact_user_id BIGINT REFERENCES users(id),
                                 -- Auteur de l'ouverture cote plateforme.
                                 opened_by_id BIGINT NOT NULL REFERENCES users(id),
                                 resolved_at TIMESTAMP,
                                 closed_at TIMESTAMP,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_support_tickets_company ON support_tickets(company_id);
CREATE INDEX idx_support_tickets_status ON support_tickets(status);
CREATE INDEX idx_support_tickets_created_at ON support_tickets(created_at);

-- Fil de discussion. `internal` distingue la note de service du message echange avec le
-- client : c'est ce qui permet de consigner « client injoignable, relancer lundi » sans
-- risquer de le lui adresser le jour ou un envoi automatique sera branche.
CREATE TABLE support_ticket_messages (
                                         id BIGSERIAL PRIMARY KEY,
                                         ticket_id BIGINT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
                                         author_id BIGINT NOT NULL REFERENCES users(id),
                                         body TEXT NOT NULL,
                                         internal BOOLEAN NOT NULL DEFAULT FALSE,
                                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_support_messages_ticket ON support_ticket_messages(ticket_id);
