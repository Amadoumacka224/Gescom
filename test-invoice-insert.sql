-- Script pour créer une facture de test pour le paiement externe

-- D'abord, créons un client de test s'il n'existe pas
INSERT IGNORE INTO clients (id, first_name, last_name, email, phone, address, created_at, updated_at) 
VALUES (999, 'Client', 'Test', 'client.test@example.com', '0123456789', '123 Rue de Test', NOW(), NOW());

-- Ensuite, créons une facture de test
INSERT IGNORE INTO invoices (
    id, 
    invoice_number, 
    invoice_date, 
    due_date, 
    status, 
    invoice_type, 
    subtotal_amount, 
    tax_amount, 
    total_amount, 
    paid_amount,
    client_id, 
    created_at, 
    updated_at
) VALUES (
    999, 
    'FACT-2024-TEST', 
    CURDATE(), 
    DATE_ADD(CURDATE(), INTERVAL 30 DAY), 
    'SENT', 
    'STANDARD', 
    100.00, 
    20.00, 
    120.00, 
    0.00,
    999, 
    NOW(), 
    NOW()
);

-- Créons aussi quelques items de facture
INSERT IGNORE INTO invoice_items (
    id,
    description, 
    quantity, 
    unit_price, 
    total_price, 
    invoice_id
) VALUES 
(999, 'Produit de test', 1, 100.00, 100.00, 999),
(998, 'TVA 20%', 1, 20.00, 20.00, 999);

-- Vérification
SELECT 
    i.invoice_number,
    i.status,
    i.total_amount,
    i.paid_amount,
    (i.total_amount - COALESCE(i.paid_amount, 0)) as remaining_amount,
    c.first_name,
    c.last_name,
    c.email
FROM invoices i 
JOIN clients c ON i.client_id = c.id 
WHERE i.invoice_number = 'FACT-2024-TEST';