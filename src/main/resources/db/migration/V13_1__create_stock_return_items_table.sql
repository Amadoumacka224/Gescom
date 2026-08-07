-- replacement_product_id ne sert qu'au traitement EXCHANGE : StockReturnService y ecrit
-- le produit de remplacement, par defaut le produit repris lui-meme.
CREATE TABLE stock_return_items (
                                    id BIGSERIAL PRIMARY KEY,
                                    quantity INTEGER NOT NULL CHECK (quantity > 0),
                                    unit_price DECIMAL(10,2) NOT NULL,
                                    refund_amount DECIMAL(10,2) NOT NULL,
                                    reason VARCHAR(30) NOT NULL CHECK (reason IN ('DEFECTIVE', 'DAMAGED', 'WRONG_ITEM', 'NOT_SATISFIED', 'ORDER_ERROR', 'OTHER')),
                                    treatment VARCHAR(30) NOT NULL CHECK (treatment IN ('RESTOCK', 'REFUND', 'EXCHANGE')),
                                    product_id BIGINT NOT NULL REFERENCES products(id),
                                    replacement_product_id BIGINT REFERENCES products(id),
                                    stock_return_id BIGINT NOT NULL REFERENCES stock_returns(id) ON DELETE CASCADE,
                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_return_items_return ON stock_return_items(stock_return_id);
CREATE INDEX idx_return_items_product ON stock_return_items(product_id);
