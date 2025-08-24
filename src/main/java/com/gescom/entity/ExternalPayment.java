package com.gescom.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité pour gérer les paiements externes (clients sans compte)
 * Conformité PCI-DSS : aucune donnée bancaire sensible stockée
 */
@Entity
@Table(name = "external_payments")
@Data
@EqualsAndHashCode(exclude = {"invoice"})
@ToString(exclude = {"invoice"})
public class ExternalPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "La facture est obligatoire")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @NotNull(message = "Le montant du paiement est obligatoire")
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type")
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    // Informations de la passerelle de paiement (tokenisées)
    @Column(name = "gateway_provider", nullable = false)
    @Size(max = 50)
    private String gatewayProvider; // STRIPE, PAYPAL, etc.

    @Column(name = "gateway_transaction_id")
    @Size(max = 255)
    private String gatewayTransactionId;

    @Column(name = "gateway_session_id")
    @Size(max = 255)
    private String gatewaySessionId;

    @Column(name = "gateway_payment_intent_id")
    @Size(max = 255)
    private String gatewayPaymentIntentId;

    // Informations sécurisées (pas de données sensibles)
    @Column(name = "card_last_four")
    @Size(max = 4)
    private String cardLastFour;

    @Column(name = "card_brand")
    @Size(max = 20)
    private String cardBrand;

    @Column(name = "customer_email")
    @Size(max = 255)
    private String customerEmail;

    @Column(name = "customer_name")
    @Size(max = 255)
    private String customerName;

    // Informations de traçabilité
    @Column(name = "client_ip")
    @Size(max = 45)
    private String clientIp;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "gateway_fee", precision = 10, scale = 2)
    private BigDecimal gatewayFee;

    @Column(name = "net_amount", precision = 10, scale = 2)
    private BigDecimal netAmount;

    // Métadonnées de sécurité
    @Column(name = "security_token")
    @Size(max = 255)
    private String securityToken;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Audit
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Enums
    public enum PaymentMethod {
        CASH("Espèces"),
        VISA("Visa"),
        MASTERCARD("MasterCard"),
        PAYPAL("PayPal"),
        BANK_TRANSFER("Virement bancaire");

        private final String displayName;

        PaymentMethod(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum CardType {
        CREDIT("Crédit"),
        DEBIT("Débit"),
        PREPAID("Prépayée");

        private final String displayName;

        CardType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum PaymentStatus {
        PENDING("En attente"),
        PROCESSING("En cours de traitement"),
        SUCCEEDED("Réussi"),
        FAILED("Échoué"),
        CANCELLED("Annulé"),
        REFUNDED("Remboursé"),
        DISPUTED("Contesté");

        private final String displayName;

        PaymentStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Méthodes utilitaires
    public boolean isCompleted() {
        return status == PaymentStatus.SUCCEEDED;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING || status == PaymentStatus.PROCESSING;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public void markAsCompleted() {
        this.status = PaymentStatus.SUCCEEDED;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }
}