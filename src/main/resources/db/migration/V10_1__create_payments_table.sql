CREATE TABLE payments (
                          id BIGSERIAL PRIMARY KEY,
                          provider VARCHAR(20) NOT NULL,
                          intent_id VARCHAR(100) UNIQUE NOT NULL,
                          amount DECIMAL(10,2) NOT NULL,
                          currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
                          status VARCHAR(30) NOT NULL DEFAULT 'REQUIRES_CONFIRMATION' CHECK (status IN ('REQUIRES_CONFIRMATION', 'SUCCEEDED', 'FAILED', 'CANCELED')),
                          card_brand VARCHAR(30),
                          card_last4 VARCHAR(4),
                          failure_message VARCHAR(255),
                          simulated BOOLEAN NOT NULL DEFAULT FALSE,
                          confirmed_at TIMESTAMP,
                          invoice_id BIGINT NOT NULL REFERENCES invoices(id),
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payments_invoice ON payments(invoice_id);
