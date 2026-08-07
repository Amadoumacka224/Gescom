CREATE TABLE stock_movements (
                                 id BIGSERIAL PRIMARY KEY,
                                 type VARCHAR(255) NOT NULL CHECK (type IN ('STOCK_IN', 'STOCK_OUT', 'ADJUSTMENT', 'RETURN', 'DAMAGE', 'TRANSFER')),
                                 quantity INTEGER NOT NULL,
                                 previous_stock INTEGER NOT NULL,
                                 new_stock INTEGER NOT NULL,
                                 unit_cost DECIMAL(10,2),
                                 reason VARCHAR(500),
                                 reference VARCHAR(100),
                                 product_id BIGINT NOT NULL REFERENCES products(id),
                                 user_id BIGINT REFERENCES users(id),
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_stock_movements_product ON stock_movements(product_id);
CREATE INDEX idx_stock_movements_user ON stock_movements(user_id);
