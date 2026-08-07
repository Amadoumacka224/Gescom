-- Une seule livraison par commande (UNIQUE sur order_id) : c'est la regle appliquee par
-- DeliveryService.createDelivery. Le lien vers la facture n'est pas porte ici mais par
-- invoices.delivery_id (voir V9_1).
CREATE TABLE deliveries (
                            id BIGSERIAL PRIMARY KEY,
                            delivery_number VARCHAR(50) UNIQUE NOT NULL,
                            delivery_address VARCHAR(255) NOT NULL,
                            delivery_city VARCHAR(100),
                            delivery_postal_code VARCHAR(20),
                            delivery_country VARCHAR(100),
                            contact_name VARCHAR(100),
                            contact_phone VARCHAR(20),
                            status VARCHAR(255) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED')),
                            scheduled_date TIMESTAMP NOT NULL,
                            delivered_date TIMESTAMP,
                            delivered_by VARCHAR(100),
                            notes VARCHAR(500),
                            order_id BIGINT UNIQUE NOT NULL REFERENCES orders(id),
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_deliveries_order ON deliveries(order_id);
