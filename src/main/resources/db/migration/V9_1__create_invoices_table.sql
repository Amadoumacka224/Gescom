CREATE TABLE invoices (
                          id BIGSERIAL PRIMARY KEY,
                          invoice_number VARCHAR(50) UNIQUE NOT NULL,
                          invoice_date DATE NOT NULL,
                          due_date DATE NOT NULL,
                          subtotal DECIMAL(10,2) NOT NULL,
                          discount DECIMAL(10,2) DEFAULT 0,
                          tax_rate DECIMAL(5,2) DEFAULT 0,
                          tax_amount DECIMAL(10,2) DEFAULT 0,
                          total_amount DECIMAL(10,2) NOT NULL,
                          paid_amount DECIMAL(10,2) DEFAULT 0,
                          remaining_amount DECIMAL(10,2) DEFAULT 0,
                          status VARCHAR(255) NOT NULL DEFAULT 'UNPAID' CHECK (status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'CANCELED')),
                          payment_method VARCHAR(255) NOT NULL CHECK (payment_method IN ('CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'CHECK', 'MOBILE_PAYMENT')),
                          payment_date DATE,
                          notes VARCHAR(500),
                          order_id BIGINT UNIQUE NOT NULL REFERENCES orders(id),
                          delivery_id BIGINT UNIQUE REFERENCES deliveries(id),
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_invoices_order ON invoices(order_id);
