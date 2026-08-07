CREATE TABLE categories (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(100) UNIQUE NOT NULL,
                            code VARCHAR(50),
                            description VARCHAR(500),
                            active BOOLEAN NOT NULL DEFAULT TRUE,
                            vat_rate DECIMAL(5,2),
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
