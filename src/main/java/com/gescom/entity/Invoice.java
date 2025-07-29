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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Data
@EqualsAndHashCode(exclude = {"invoiceItems", "order"})
@ToString(exclude = {"invoiceItems", "order"})
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 50, message = "Le numéro de facture ne peut pas dépasser 50 caractères")
    @Column(name = "invoice_number", unique = true, nullable = false)
    private String invoiceNumber;

    @NotNull(message = "La date de facture est obligatoire")
    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @NotNull(message = "La date d'échéance est obligatoire")
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type")
    private InvoiceType invoiceType = InvoiceType.STANDARD;

    @Column(name = "discount_rate", precision = 5, scale = 2)
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_cost", precision = 10, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "total_amount_ht", precision = 10, scale = 2)
    private BigDecimal totalAmountHT = BigDecimal.ZERO;

    @Column(name = "total_vat_amount", precision = 10, scale = 2)
    private BigDecimal totalVatAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "payment_reference")
    @Size(max = 100, message = "La référence de paiement ne peut pas dépasser 100 caractères")
    private String paymentReference;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "terms_conditions", columnDefinition = "TEXT")
    private String termsConditions;

    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "email_sent")
    private Boolean emailSent = false;

    @Column(name = "email_sent_date")
    private LocalDateTime emailSentDate;

    // Relations
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<InvoiceItem> invoiceItems = new ArrayList<>();

    // Audit
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Enums
    public enum InvoiceStatus {
        DRAFT("Brouillon"),
        SENT("Envoyée"),
        PAID("Payée"),
        PARTIAL("Partiellement payée"),
        OVERDUE("Échue"),
        CANCELLED("Annulée");

        private final String displayName;

        InvoiceStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum InvoiceType {
        STANDARD("Standard"),
        PROFORMA("Proforma"),
        CREDIT_NOTE("Avoir"),
        DEPOSIT("Acompte");

        private final String displayName;

        InvoiceType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum PaymentMethod {
        CASH("Espèces"),
        CARD("Carte bancaire"),
        TRANSFER("Virement"),
        CHECK("Chèque"),
        PAYPAL("PayPal"),
        OTHER("Autre");

        private final String displayName;

        PaymentMethod(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Méthodes utilitaires
    public void calculateTotals() {
        BigDecimal subtotalHT = invoiceItems.stream()
                .map(InvoiceItem::getTotalPriceHT)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal subtotalVAT = invoiceItems.stream()
                .map(InvoiceItem::getTotalVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Application de la remise
        if (discountRate != null && discountRate.compareTo(BigDecimal.ZERO) > 0) {
            discountAmount = subtotalHT.multiply(discountRate).divide(BigDecimal.valueOf(100));
        }

        this.totalAmountHT = subtotalHT.subtract(discountAmount != null ? discountAmount : BigDecimal.ZERO);
        this.totalVatAmount = subtotalVAT;
        this.totalAmount = totalAmountHT.add(totalVatAmount).add(shippingCost != null ? shippingCost : BigDecimal.ZERO);
    }

    public BigDecimal getRemainingAmount() {
        if (paidAmount == null) return totalAmount;
        return totalAmount.subtract(paidAmount);
    }

    public boolean isOverdue() {
        return status != InvoiceStatus.PAID &&
                status != InvoiceStatus.CANCELLED &&
                dueDate.isBefore(LocalDate.now());
    }

    public boolean isFullyPaid() {
        return paidAmount != null && paidAmount.compareTo(totalAmount) >= 0;
    }

    public boolean isPartiallyPaid() {
        return paidAmount != null &&
                paidAmount.compareTo(BigDecimal.ZERO) > 0 &&
                paidAmount.compareTo(totalAmount) < 0;
    }

    public long getDaysOverdue() {
        if (!isOverdue()) return 0;
        return dueDate.until(LocalDate.now()).getDays();
    }
}