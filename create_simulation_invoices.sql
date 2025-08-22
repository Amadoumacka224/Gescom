-- Script pour créer des factures de test pour la simulation de paiement
-- ⚠️ Exécuter uniquement en développement

-- 1. Vérifier les factures existantes
SELECT 
    invoice_number, 
    status, 
    total_amount, 
    invoice_date
FROM invoices 
ORDER BY created_at DESC 
LIMIT 5;

-- 2. Créer 3 factures de test pour simulation
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
) VALUES 
-- Facture 1
(
    'SIM-2024-001',
    CURRENT_DATE,
    CURRENT_DATE + INTERVAL '30 days',
    'SENT',
    100.00,
    20.00,
    120.00,
    'Client Simulation 1' || CHR(10) || '123 Rue de Test' || CHR(10) || '75001 Paris',
    'Facture de test pour simulation de paiement',
    (SELECT id FROM orders ORDER BY created_at DESC LIMIT 1),
    NOW(),
    NOW()
),
-- Facture 2
(
    'SIM-2024-002',
    CURRENT_DATE - INTERVAL '5 days',
    CURRENT_DATE + INTERVAL '25 days',
    'SENT',
    250.00,
    50.00,
    300.00,
    'Client Simulation 2' || CHR(10) || '456 Avenue Test' || CHR(10) || '69000 Lyon',
    'Facture de démonstration pour tests',
    (SELECT id FROM orders ORDER BY created_at DESC LIMIT 1),
    NOW(),
    NOW()
),
-- Facture 3
(
    'SIM-2024-003',
    CURRENT_DATE - INTERVAL '10 days',
    CURRENT_DATE + INTERVAL '20 days',
    'SENT',
    75.00,
    15.00,
    90.00,
    'Client Simulation 3' || CHR(10) || '789 Boulevard Demo' || CHR(10) || '33000 Bordeaux',
    'Facture test pour processus de paiement',
    (SELECT id FROM orders ORDER BY created_at DESC LIMIT 1),
    NOW(),
    NOW()
);

-- 3. Créer des articles pour ces factures
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
    CASE 
        WHEN i.invoice_number = 'SIM-2024-001' THEN 'Service de Consultation'
        WHEN i.invoice_number = 'SIM-2024-002' THEN 'Formation Utilisateur'
        WHEN i.invoice_number = 'SIM-2024-003' THEN 'Support Technique'
    END,
    CASE 
        WHEN i.invoice_number = 'SIM-2024-001' THEN 1
        WHEN i.invoice_number = 'SIM-2024-002' THEN 2
        WHEN i.invoice_number = 'SIM-2024-003' THEN 1
    END,
    CASE 
        WHEN i.invoice_number = 'SIM-2024-001' THEN 100.00
        WHEN i.invoice_number = 'SIM-2024-002' THEN 125.00
        WHEN i.invoice_number = 'SIM-2024-003' THEN 75.00
    END,
    0.00,
    0.00,
    20.00,
    i.total_amount_ht,
    i.total_vat_amount,
    i.total_amount,
    'heure',
    CASE 
        WHEN i.invoice_number = 'SIM-2024-001' THEN 'CONSULT-001'
        WHEN i.invoice_number = 'SIM-2024-002' THEN 'FORM-001'
        WHEN i.invoice_number = 'SIM-2024-003' THEN 'SUPPORT-001'
    END,
    i.id
FROM invoices i 
WHERE i.invoice_number IN ('SIM-2024-001', 'SIM-2024-002', 'SIM-2024-003');

-- 4. Vérifier la création
SELECT 
    i.invoice_number,
    i.status,
    i.total_amount,
    ii.description,
    ii.quantity,
    ii.unit_price
FROM invoices i
LEFT JOIN invoice_items ii ON i.id = ii.invoice_id
WHERE i.invoice_number LIKE 'SIM-2024-%'
ORDER BY i.invoice_number;

-- 5. Instructions pour tester
SELECT '
🎯 FACTURES DE SIMULATION CRÉÉES:

✅ SIM-2024-001 - 120,00€ - Service de Consultation
✅ SIM-2024-002 - 300,00€ - Formation Utilisateur  
✅ SIM-2024-003 - 90,00€ - Support Technique

🚀 POUR TESTER LA SIMULATION:

1️⃣ Allez sur: http://localhost:9090/payment-simulation

2️⃣ Saisissez un numéro de facture: SIM-2024-001, SIM-2024-002, ou SIM-2024-003

3️⃣ Suivez le processus de simulation de paiement

4️⃣ Observez le changement de statut vers "PAID"

📋 Pour voir toutes les factures: http://localhost:9090/payment-simulation/invoices

💡 Ces factures sont en statut SENT donc parfaites pour la simulation!
' as instructions;