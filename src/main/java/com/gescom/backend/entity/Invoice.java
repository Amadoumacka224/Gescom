package com.gescom.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gescom.backend.tenancy.TenantEntityListener;
import com.gescom.backend.tenancy.TenantOwned;
import org.hibernate.annotations.Filter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité facture, liée à une commande (relation 1-1) et optionnellement à une livraison.
 * Conserve la décomposition du montant (sous-total, remise, TVA, total) et le suivi des
 * paiements (montant payé, restant dû). Le numéro de facture et le montant restant sont
 * calculés automatiquement via les callbacks @PrePersist / @PreUpdate.
 */
@Entity
@Table(name = "invoices", uniqueConstraints = @UniqueConstraint(name = "uq_invoices_company_number", columnNames = {"company_id", "invoice_number"}))
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Invoice implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Entreprise proprietaire de la ligne - cle du cloisonnement multi-entreprises.
     *
     * Renseignee automatiquement a la creation par TenantEntityListener : aucun service ni
     * mapper n'a a s'en occuper, ce qui evite qu'un oubli produise une ligne orpheline.
     * Ecartee de la serialisation JSON, les controleurs ne renvoyant que des DTO.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company ownerCompany;

    @Column(nullable = false, length = 50)
    private String invoiceNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column
    private LocalDate paymentDate;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        generateInvoiceNumber();
        calculateRemainingAmount();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateRemainingAmount();
    }

    private void generateInvoiceNumber() {
        if (invoiceNumber == null) {
            invoiceNumber = "FACT-" + System.currentTimeMillis();
        }
    }

    private void calculateRemainingAmount() {
        remainingAmount = totalAmount.subtract(paidAmount);
    }

    // État de règlement de la facture, déduit du rapport montant payé / montant total (cf. InvoiceService).
    public enum InvoiceStatus {
        UNPAID, PARTIALLY_PAID, PAID, CANCELED
    }

    // Moyen de paiement enregistré lors du règlement.
    public enum PaymentMethod {
        CASH, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, CHECK, MOBILE_PAYMENT
    }
}
