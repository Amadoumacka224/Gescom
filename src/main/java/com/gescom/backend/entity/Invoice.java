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
 * Facture, liée à une commande (relation 1-1) et parfois à une livraison.
 * On garde le détail du montant (sous-total, remise, TVA, total) et le suivi des paiements
 * (montant payé, reste à payer). Le numéro et le reste à payer sont calculés automatiquement
 * dans les callbacks @PrePersist / @PreUpdate.
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
     * Entreprise a qui appartient la ligne, c'est ce qui separe les donnees des entreprises.
     *
     * Remplie automatiquement a la creation par TenantEntityListener : ni les services ni
     * les mappers n'ont a s'en occuper, donc pas de risque d'oubli. Pas envoyee en JSON
     * (les controleurs ne renvoient que des DTO).
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
        calculateRemainingAmount();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateRemainingAmount();
    }

    /*
     * Numéro donné par DocumentNumberService (voir le commentaire du même genre dans Order).
     * C'est ici que c'est le plus important : une facture doit avoir un numéro qui se suit,
     * sans trou, ce qu'on n'a pas avec un horodatage.
     */

    private void calculateRemainingAmount() {
        remainingAmount = totalAmount.subtract(paidAmount);
    }

    // État du paiement de la facture, calculé à partir du montant payé et du total (voir InvoiceService).
    public enum InvoiceStatus {
        UNPAID, PARTIALLY_PAID, PAID, CANCELED
    }

    // Mode de paiement utilisé.
    public enum PaymentMethod {
        CASH, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, CHECK, MOBILE_PAYMENT
    }
}
