-- Singleton de configuration : SettingsService ne gere qu'une seule ligne, creee a la
-- volee avec ses valeurs par defaut a la premiere lecture. Les DEFAULT ci-dessous
-- reprennent ces memes valeurs pour que V4_2 puisse n'inserer que l'identite societe.
CREATE TABLE settings (
                          id BIGSERIAL PRIMARY KEY,
                          company_name VARCHAR(255) NOT NULL DEFAULT 'Gescom',
                          company_address VARCHAR(255),
                          company_city VARCHAR(255),
                          company_postal_code VARCHAR(255),
                          company_country VARCHAR(255),
                          company_phone VARCHAR(255),
                          company_email VARCHAR(255),
                          company_tax_id VARCHAR(255),
                          company_iban VARCHAR(255),
                          company_bic VARCHAR(255),
                          currency VARCHAR(255) NOT NULL DEFAULT 'EUR',
                          tax_rate DOUBLE PRECISION NOT NULL DEFAULT 21.0,
                          timezone VARCHAR(255) NOT NULL DEFAULT 'Europe/Brussels',
                          language VARCHAR(255) NOT NULL DEFAULT 'fr',
                          date_format VARCHAR(255) NOT NULL DEFAULT 'dd/MM/yyyy',
                          theme VARCHAR(255) NOT NULL DEFAULT 'light',
                          invoice_prefix VARCHAR(255) NOT NULL DEFAULT 'FACT',
                          invoice_number_start INTEGER NOT NULL DEFAULT 1,
                          payment_terms INTEGER NOT NULL DEFAULT 30,
                          footer_text TEXT,
                          low_stock_threshold INTEGER NOT NULL DEFAULT 10,
                          stock_alerts BOOLEAN NOT NULL DEFAULT TRUE,
                          notifications BOOLEAN NOT NULL DEFAULT TRUE,
                          email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
                          order_notifications BOOLEAN NOT NULL DEFAULT TRUE,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
