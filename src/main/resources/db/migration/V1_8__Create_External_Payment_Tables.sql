-- Migration pour créer les tables de paiement externe
-- Version 1.8 - Paiements externes sécurisés

-- Création de la table des paiements externes
CREATE TABLE external_payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- Référence à la facture
    invoice_id BIGINT NOT NULL,
    
    -- Informations du paiement
    amount DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    card_type VARCHAR(10),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    
    -- Informations de la passerelle de paiement (tokenisées)
    gateway_provider VARCHAR(50) NOT NULL,
    gateway_transaction_id VARCHAR(255),
    gateway_session_id VARCHAR(255),
    gateway_payment_intent_id VARCHAR(255),
    
    -- Informations sécurisées (pas de données sensibles)
    card_last_four VARCHAR(4),
    card_brand VARCHAR(20),
    customer_email VARCHAR(255),
    customer_name VARCHAR(255),
    
    -- Informations de traçabilité
    client_ip VARCHAR(45),
    user_agent TEXT,
    failure_reason TEXT,
    gateway_fee DECIMAL(10,2),
    net_amount DECIMAL(10,2),
    
    -- Métadonnées de sécurité
    security_token VARCHAR(255) UNIQUE,
    expires_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Contraintes
    CONSTRAINT fk_external_payments_invoice 
        FOREIGN KEY (invoice_id) REFERENCES invoices(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT chk_external_payment_amount 
        CHECK (amount > 0),
    
    CONSTRAINT chk_external_payment_method 
        CHECK (payment_method IN ('VISA', 'MASTERCARD', 'PAYPAL', 'BANK_TRANSFER')),
    
    CONSTRAINT chk_external_payment_status 
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REFUNDED', 'DISPUTED')),
    
    CONSTRAINT chk_external_card_type 
        CHECK (card_type IS NULL OR card_type IN ('CREDIT', 'DEBIT', 'PREPAID'))
);

-- Index pour les performances et la sécurité
CREATE INDEX idx_external_payments_invoice_id ON external_payments(invoice_id);
CREATE INDEX idx_external_payments_status ON external_payments(status);
CREATE INDEX idx_external_payments_gateway_transaction_id ON external_payments(gateway_transaction_id);
CREATE INDEX idx_external_payments_gateway_session_id ON external_payments(gateway_session_id);
CREATE INDEX idx_external_payments_gateway_payment_intent_id ON external_payments(gateway_payment_intent_id);
CREATE INDEX idx_external_payments_security_token ON external_payments(security_token);
CREATE INDEX idx_external_payments_client_ip ON external_payments(client_ip);
CREATE INDEX idx_external_payments_created_at ON external_payments(created_at);
CREATE INDEX idx_external_payments_expires_at ON external_payments(expires_at);

-- Index composé pour les requêtes de sécurité
CREATE INDEX idx_external_payments_security ON external_payments(client_ip, status, created_at);

-- Création de la table pour les logs de sécurité des paiements
CREATE TABLE payment_security_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- Informations de l'événement
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    
    -- Informations de la source
    client_ip VARCHAR(45) NOT NULL,
    user_agent TEXT,
    request_uri VARCHAR(500),
    
    -- Détails de l'événement
    event_details TEXT,
    additional_data JSON,
    
    -- Référence optionnelle au paiement
    external_payment_id BIGINT,
    
    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Contraintes
    CONSTRAINT fk_payment_security_logs_external_payment 
        FOREIGN KEY (external_payment_id) REFERENCES external_payments(id) 
        ON DELETE SET NULL,
    
    CONSTRAINT chk_payment_security_severity 
        CHECK (severity IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL'))
);

-- Index pour les logs de sécurité
CREATE INDEX idx_payment_security_logs_event_type ON payment_security_logs(event_type);
CREATE INDEX idx_payment_security_logs_severity ON payment_security_logs(severity);
CREATE INDEX idx_payment_security_logs_client_ip ON payment_security_logs(client_ip);
CREATE INDEX idx_payment_security_logs_created_at ON payment_security_logs(created_at);
CREATE INDEX idx_payment_security_logs_external_payment_id ON payment_security_logs(external_payment_id);

-- Index composé pour l'analyse de sécurité
CREATE INDEX idx_payment_security_analysis ON payment_security_logs(client_ip, event_type, created_at);

-- Création de la table pour les tentatives de paiement suspicieuses
CREATE TABLE suspicious_payment_attempts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- Informations de l'IP suspecte
    client_ip VARCHAR(45) NOT NULL,
    
    -- Compteurs
    failed_attempts INT NOT NULL DEFAULT 0,
    blocked_attempts INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP,
    
    -- Raisons du blocage
    block_reason TEXT,
    blocked_until TIMESTAMP,
    
    -- Métadonnées
    first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Données supplémentaires pour l'analyse
    user_agents JSON,
    attempted_invoice_numbers JSON,
    
    -- Contraintes
    UNIQUE KEY uk_suspicious_payment_attempts_ip (client_ip)
);

-- Index pour les tentatives suspectes
CREATE INDEX idx_suspicious_payment_attempts_blocked_until ON suspicious_payment_attempts(blocked_until);
CREATE INDEX idx_suspicious_payment_attempts_last_attempt ON suspicious_payment_attempts(last_attempt_at);

-- Vue pour les statistiques de paiement en temps réel
CREATE VIEW payment_statistics AS
SELECT 
    COUNT(*) as total_payments,
    COUNT(CASE WHEN status = 'SUCCEEDED' THEN 1 END) as successful_payments,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed_payments,
    COUNT(CASE WHEN status IN ('PENDING', 'PROCESSING') THEN 1 END) as pending_payments,
    COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' THEN amount END), 0) as total_amount_processed,
    COALESCE(AVG(CASE WHEN status = 'SUCCEEDED' THEN amount END), 0) as average_payment_amount,
    COALESCE(
        ROUND(
            COUNT(CASE WHEN status = 'SUCCEEDED' THEN 1 END) * 100.0 / 
            NULLIF(COUNT(*), 0), 
            2
        ), 
        0
    ) as success_rate_percentage
FROM external_payments
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY);

-- Vue pour les paiements récents avec détails de sécurité
CREATE VIEW recent_payments_security AS
SELECT 
    ep.id,
    ep.amount,
    ep.payment_method,
    ep.status,
    ep.client_ip,
    ep.customer_email,
    ep.created_at,
    i.invoice_number,
    spa.failed_attempts,
    spa.blocked_attempts,
    CASE 
        WHEN spa.blocked_until > NOW() THEN 'BLOCKED'
        WHEN spa.failed_attempts > 5 THEN 'SUSPICIOUS'
        ELSE 'NORMAL'
    END as security_status
FROM external_payments ep
JOIN invoices i ON ep.invoice_id = i.id
LEFT JOIN suspicious_payment_attempts spa ON ep.client_ip = spa.client_ip
WHERE ep.created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
ORDER BY ep.created_at DESC;

-- Procédure stockée pour nettoyer les données expirées
DELIMITER //

CREATE PROCEDURE CleanupExpiredPaymentData()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE cleanup_count INT DEFAULT 0;
    
    -- Nettoyage des paiements expirés
    UPDATE external_payments 
    SET status = 'CANCELLED', 
        failure_reason = 'Session expirée'
    WHERE expires_at < NOW() 
    AND status IN ('PENDING', 'PROCESSING');
    
    GET DIAGNOSTICS cleanup_count = ROW_COUNT;
    
    -- Nettoyage des logs de sécurité anciens (> 90 jours)
    DELETE FROM payment_security_logs 
    WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);
    
    -- Nettoyage des tentatives suspectes anciennes (> 30 jours)
    DELETE FROM suspicious_payment_attempts 
    WHERE last_updated_at < DATE_SUB(NOW(), INTERVAL 30 DAY)
    AND blocked_until < NOW();
    
    -- Log du nettoyage
    INSERT INTO payment_security_logs (event_type, severity, client_ip, event_details)
    VALUES ('CLEANUP', 'INFO', '127.0.0.1', 
            CONCAT('Nettoyage automatique: ', cleanup_count, ' paiements expirés traités'));
            
END //

DELIMITER ;

-- Fonction pour calculer le score de risque d'une IP
DELIMITER //

CREATE FUNCTION CalculateRiskScore(ip_address VARCHAR(45)) 
RETURNS INT
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE risk_score INT DEFAULT 0;
    DECLARE failed_count INT DEFAULT 0;
    DECLARE recent_failures INT DEFAULT 0;
    
    -- Récupérer le nombre total d'échecs
    SELECT COALESCE(failed_attempts, 0) INTO failed_count
    FROM suspicious_payment_attempts 
    WHERE client_ip = ip_address;
    
    -- Récupérer les échecs récents (dernières 24h)
    SELECT COUNT(*) INTO recent_failures
    FROM external_payments 
    WHERE client_ip = ip_address 
    AND status = 'FAILED' 
    AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR);
    
    -- Calculer le score de risque
    SET risk_score = (failed_count * 2) + (recent_failures * 5);
    
    -- Plafond à 100
    IF risk_score > 100 THEN
        SET risk_score = 100;
    END IF;
    
    RETURN risk_score;
END //

DELIMITER ;

-- Trigger pour loguer les tentatives de paiement
DELIMITER //

CREATE TRIGGER log_payment_attempt
AFTER INSERT ON external_payments
FOR EACH ROW
BEGIN
    INSERT INTO payment_security_logs (
        event_type, 
        severity, 
        client_ip, 
        user_agent, 
        event_details, 
        external_payment_id
    ) VALUES (
        'PAYMENT_ATTEMPT', 
        'INFO', 
        NEW.client_ip, 
        NEW.user_agent, 
        CONCAT('Tentative de paiement: ', NEW.amount, ' EUR pour facture ID ', NEW.invoice_id),
        NEW.id
    );
END //

DELIMITER ;

-- Trigger pour loguer les échecs de paiement
DELIMITER //

CREATE TRIGGER log_payment_failure
AFTER UPDATE ON external_payments
FOR EACH ROW
BEGIN
    IF NEW.status = 'FAILED' AND OLD.status != 'FAILED' THEN
        INSERT INTO payment_security_logs (
            event_type, 
            severity, 
            client_ip, 
            user_agent, 
            event_details, 
            external_payment_id
        ) VALUES (
            'PAYMENT_FAILED', 
            'WARN', 
            NEW.client_ip, 
            NEW.user_agent, 
            CONCAT('Échec de paiement: ', COALESCE(NEW.failure_reason, 'Raison inconnue')),
            NEW.id
        );
        
        -- Mettre à jour ou créer l'entrée de tentative suspecte
        INSERT INTO suspicious_payment_attempts (client_ip, failed_attempts, last_attempt_at)
        VALUES (NEW.client_ip, 1, NOW())
        ON DUPLICATE KEY UPDATE 
            failed_attempts = failed_attempts + 1,
            last_attempt_at = NOW(),
            last_updated_at = NOW();
    END IF;
END //

DELIMITER ;

-- Insertion de données de test (uniquement en développement)
-- Ces données ne doivent PAS être utilisées en production

-- Commentaires pour la documentation
-- Table external_payments : Stocke toutes les tentatives de paiement externe
-- Table payment_security_logs : Journal de sécurité pour les événements de paiement
-- Table suspicious_payment_attempts : Suivi des IP suspectes et des tentatives d'attaque
-- Vue payment_statistics : Statistiques en temps réel des paiements
-- Vue recent_payments_security : Vue d'ensemble de la sécurité des paiements récents