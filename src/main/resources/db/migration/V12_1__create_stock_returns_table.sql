CREATE TABLE stock_returns (
                               id BIGSERIAL PRIMARY KEY,
                               return_number VARCHAR(50) UNIQUE NOT NULL,
                               total_quantity INTEGER NOT NULL DEFAULT 0,
                               refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
                               notes VARCHAR(500),
                               order_id BIGINT NOT NULL REFERENCES orders(id),
                               invoice_id BIGINT REFERENCES invoices(id),
                               user_id BIGINT REFERENCES users(id),
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_stock_returns_order ON stock_returns(order_id);
CREATE INDEX idx_stock_returns_invoice ON stock_returns(invoice_id);
CREATE INDEX idx_stock_returns_user ON stock_returns(user_id);
