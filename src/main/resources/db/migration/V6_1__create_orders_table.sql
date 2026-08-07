CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,
                        order_number VARCHAR(50) UNIQUE NOT NULL,
                        status VARCHAR(255) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'INVOICED', 'DELIVERED', 'CANCELED')),
                        total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
                        discount DECIMAL(10,2) NOT NULL DEFAULT 0,
                        final_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
                        notes VARCHAR(500),
                        client_id BIGINT REFERENCES clients(id),
                        user_id BIGINT NOT NULL REFERENCES users(id),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_orders_client ON orders(client_id);
CREATE INDEX idx_orders_user ON orders(user_id);
