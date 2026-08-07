CREATE TABLE activity_logs (
                               id BIGSERIAL PRIMARY KEY,
                               action_type VARCHAR(255) NOT NULL CHECK (action_type IN ('CREATE', 'UPDATE', 'DELETE', 'VIEW', 'LOGIN', 'LOGOUT', 'SALE', 'PAYMENT', 'STOCK_IN', 'STOCK_OUT', 'EXPORT', 'IMPORT')),
                               entity VARCHAR(100) NOT NULL,
                               entity_id BIGINT,
                               description VARCHAR(500),
                               ip_address VARCHAR(50),
                               details TEXT,
                               user_id BIGINT NOT NULL REFERENCES users(id),
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_activity_logs_user ON activity_logs(user_id);
