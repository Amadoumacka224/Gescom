package com.gescom.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@EqualsAndHashCode(exclude = {"order", "product"})
@ToString(exclude = {"order", "product"})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 255)
    private String description;

    // Méthodes utilitaires
    @PrePersist
    @PreUpdate
    public void calculateTotals() {
        if (unitPrice == null || quantity == null) {
            this.totalPriceHT = BigDecimal.ZERO;
            this.totalVatAmount = BigDecimal.ZERO;
            this.totalPrice = BigDecimal.ZERO;
            this.discountAmount = BigDecimal.ZERO;
            return;
        }

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
        
        System.out.println("OrderItem calculateTotals - Qté: " + quantity + 
                         ", Prix: " + unitPrice + 
                         ", Total HT: " + totalPriceHT);
    }

    public BigDecimal getUnitPriceAfterDiscount() {
        if (discountRate == null || discountRate.compareTo(BigDecimal.ZERO) == 0) {
            return unitPrice;
        }
        BigDecimal discount = unitPrice.multiply(discountRate).divide(BigDecimal.valueOf(100));
        return unitPrice.subtract(discount);
    }

    public BigDecimal getTotalPriceAfterDiscount() {
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) == 0) {
            return totalPrice;
        }
        return totalPrice.subtract(discountAmount);
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description != null ? description.trim() : null;
    }
}