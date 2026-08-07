CREATE TABLE clients (
                         id BIGSERIAL PRIMARY KEY,
                         first_name VARCHAR(100) NOT NULL,
                         last_name VARCHAR(100) NOT NULL,
                         company VARCHAR(50),
                         email VARCHAR(100) UNIQUE,
                         phone VARCHAR(20) NOT NULL,
                         address VARCHAR(255),
                         city VARCHAR(100),
                         country VARCHAR(100),
                         postal_code VARCHAR(20),
                         type VARCHAR(255) NOT NULL DEFAULT 'PARTICULIER' CHECK (type IN ('PARTICULIER', 'ENTREPRISE')),
                         active BOOLEAN NOT NULL DEFAULT TRUE,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
