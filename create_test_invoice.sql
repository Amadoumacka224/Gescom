-- Script pour créer une facture de test pour le paiement en ligne
-- ⚠️ Exécuter ce script uniquement en développement

-- 1. Vérifier les factures existantes
SELECT 
    invoice_number, 
    status, 
    total_amount, 
    invoice_date,
    order_id
FROM invoices 
ORDER BY created_at DESC 
LIMIT 5;

-- 2. Vérifier s'il existe des commandes
SELECT id, order_number, status FROM orders LIMIT 3;

-- 3. Créer une facture de test PAYABLE
INSERT INTO invoices (
    invoice_number, 
    invoice_date, 
    due_date, 
    status, 
    total_amount_ht, 
    total_vat_amount, 
    total_amount,
    billing_address,
    notes,
    order_id,
    created_at,
    updated_at
) 
SELECT 
    'FAC-TEST-' || LPAD(CAST((SELECT COALESCE(MAX(CAST(SUBSTRING(invoice_number FROM 10) AS INTEGER)), 0) + 1 FROM invoices WHERE invoice_number LIKE 'FAC-TEST-%') AS TEXT), 3, '0'),
    CURRENT_DATE,
    CURRENT_DATE + INTERVAL '30 days',
    'SENT',  -- Status PAYABLE immédiatement
    250.00,
    50.00,
    300.00,
    'Adresse de facturation test' || CHR(10) || '123 Rue de Test' || CHR(10) || '75001 Paris' || CHR(10) || 'France',
    'Facture de test pour paiement en ligne - Créée automatiquement',
    (SELECT id FROM orders ORDER BY created_at DESC LIMIT 1),  -- Dernière commande créée
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM invoices WHERE invoice_number LIKE 'FAC-TEST-%' AND status = 'SENT'
);

-- 4. Créer des articles de facture
INSERT INTO invoice_items (
    description,
    quantity,
    unit_price,
    discount_rate,
    discount_amount,
    vat_rate,
    total_price_ht,
    total_vat_amount,
    total_price,
    unit,
    reference,
    invoice_id
)
SELECT 
    'Produit Test Paiement',
    2,
    125.00,
    0.00,
    0.00,
    20.00,
    250.00,
    50.00,
    300.00,
    'unité',
    'TEST-PAY-001',
    i.id
FROM invoices i 
WHERE i.invoice_number LIKE 'FAC-TEST-%' 
  AND i.status = 'SENT'
  AND NOT EXISTS (
      SELECT 1 FROM invoice_items ii WHERE ii.invoice_id = i.id
  )
ORDER BY i.created_at DESC 
LIMIT 1;

-- 5. Vérifier la création et afficher les résultats
SELECT 
    i.invoice_number,
    i.status,
    i.total_amount,
    i.invoice_date,
    ii.description,
    ii.quantity,
    ii.unit_price,
    ii.total_price
FROM invoices i
LEFT JOIN invoice_items ii ON i.id = ii.invoice_id
WHERE i.invoice_number LIKE 'FAC-TEST-%'
ORDER BY i.created_at DESC;

-- 6. Instructions pour tester
SELECT '
🎯 INSTRUCTIONS POUR TESTER LE PAIEMENT:

1️⃣ Copiez le numéro de facture affiché ci-dessus (format: FAC-TEST-XXX)

2️⃣ Allez sur: http://localhost:9090/payment

3️⃣ Collez le numéro de facture dans le formulaire de recherche

4️⃣ Suivez le processus de paiement

📋 Pour voir toutes les factures payables:
   👉 http://localhost:9090/payment/debug/invoices

🔧 Si vous avez "Accès temporairement restreint":
   👉 http://localhost:9090/payment/debug/reset-security

⚠️  Assurez-vous que le statut est SENT pour que la facture soit payable!
' as instructions;