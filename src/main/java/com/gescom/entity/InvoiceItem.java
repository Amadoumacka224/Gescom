package com.gescom.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_items")
@Data
@EqualsAndHashCode(exclude = {"invoice"})
@ToString(exclude = {"invoice"})
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 200, message = "La description ne peut pas dépasser 200 caractères")
    @Column(nullable = false)
    private String description;

    @Min(value = 1, message = "La quantité doit être au moins de 1")
    @Column(nullable = false)
    private Integer quantity;


    @DecimalMin(value = "0.0", inclusive = false, message = "Le prix unitaire doit être supérieur à 0")
    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "discount_rate", precision = 5, scale = 2)
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.valueOf(20.0);

    @Column(name = "total_price_ht", precision = 10, scale = 2)
    private BigDecimal totalPriceHT;

    @Column(name = "total_vat_amount", precision = 10, scale = 2)
    private BigDecimal totalVatAmount;

    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Size(max = 50, message = "L'unité ne peut pas dépasser 50 caractères")
    private String unit = "pièce";

    @Size(max = 50, message = "La référence ne peut pas dépasser 50 caractères")
    private String reference;

    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // Méthodes utilitaires
    @PrePersist
    @PreUpdate
    public void calculateTotals() {

        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        // Application de la remise
        if (discountRate != null && discountRate.compareTo(BigDecimal.ZERO) > 0) {
            discountAmount = subtotal.multiply(discountRate).divide(BigDecimal.valueOf(100));
        } else {
            discountAmount = BigDecimal.ZERO;
        }

        this.totalPriceHT = subtotal.subtract(discountAmount);

        // Calcul de la TVA
        if (vatRate != null && vatRate.compareTo(BigDecimal.ZERO) > 0) {
            this.totalVatAmount = totalPriceHT.multiply(vatRate).divide(BigDecimal.valueOf(100));
        } else {
            this.totalVatAmount = BigDecimal.ZERO;
        }

        this.totalPrice = totalPriceHT.add(totalVatAmount);
    }

    public BigDecimal getUnitPriceAfterDiscount() {
        if (discountRate == null || discountRate.compareTo(BigDecimal.ZERO) == 0) {
            return unitPrice;
        }
        BigDecimal discount = unitPrice.multiply(discountRate).divide(BigDecimal.valueOf(100));
        return unitPrice.subtract(discount);
    }
}