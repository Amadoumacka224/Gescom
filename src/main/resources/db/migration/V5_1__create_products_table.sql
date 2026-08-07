CREATE TABLE products (
                          id BIGSERIAL PRIMARY KEY,
                          code VARCHAR(50) UNIQUE NOT NULL,
                          barcode VARCHAR(50),
                          name VARCHAR(200) NOT NULL,
                          description TEXT,
                          image_url TEXT,
                          purchase_price DECIMAL(10,2) NOT NULL,
                          selling_price DECIMAL(10,2) NOT NULL,
                          stock_quantity INTEGER NOT NULL DEFAULT 0,
                          min_stock_alert INTEGER NOT NULL DEFAULT 5,
                          unit VARCHAR(50) DEFAULT 'PIECE',
                          active BOOLEAN NOT NULL DEFAULT TRUE,
                          category_id BIGINT REFERENCES categories(id),
                          vat_rate DECIMAL(5,2),
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_products_category ON products(category_id);
