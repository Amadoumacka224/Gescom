-- 180 produits générés avec des données variées
INSERT INTO products (code, barcode, name, description, purchase_price, selling_price, stock_quantity, min_stock_alert, unit, active, category_id, vat_rate)
SELECT
    'PROD-' || LPAD(generate_series::text, 5, '0'),
    '3' || LPAD(floor(random()*10000000000)::text, 10, '0'),
    'Produit ' || generate_series,
    'Description du produit ' || generate_series,
    round((random()*100+10)::numeric, 2),
    round((random()*200+20)::numeric, 2),
    floor(random()*500+10)::int,
    floor(random()*20+2)::int,
    'PIECE',
    TRUE,
    (floor(random()*12)+1)::int,
    CASE WHEN floor(random()*3)=0 THEN 6.0 ELSE 21.0 END
FROM generate_series(1, 180);