package com.gescom.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Data
@EqualsAndHashCode(exclude = {"orderItems"})
@ToString(exclude = {"orderItems"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le nom du produit est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotBlank(message = "La référence est obligatoire")
    @Size(max = 50, message = "La référence ne peut pas dépasser 50 caractères")
    @Column(unique = true, nullable = false)
    private String reference;

    @Column(name = "bar_code")
    @Size(max = 50, message = "Le code-barres ne peut pas dépasser 50 caractères")
    private String barCode;

    @NotNull(message = "Le prix unitaire est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le prix doit être supérieur à 0")
    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @DecimalMin(value = "0.0", message = "Le prix d'achat ne peut pas être négatif")
    @Column(name = "purchase_price", precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    @NotNull(message = "Le stock est obligatoire")
    @Min(value = 0, message = "Le stock ne peut pas être négatif")
    @Column(nullable = false)
    private Integer stock = 0;

    @Min(value = 0, message = "Le stock minimum ne peut pas être négatif")
    @Column(name = "min_stock")
    private Integer minStock = 0;

    @Min(value = 0, message = "Le stock maximum ne peut pas être négatif")
    @Column(name = "max_stock")
    private Integer maxStock;

    @Size(max = 50, message = "L'unité ne peut pas dépasser 50 caractères")
    private String unit = "pièce";

    @NotBlank(message = "La catégorie est obligatoire")
    @Size(max = 50, message = "La catégorie ne peut pas dépasser 50 caractères")
    @Column(nullable = false)
    private String category;

    @Size(max = 50, message = "La marque ne peut pas dépasser 50 caractères")
    private String brand;

    @DecimalMin(value = "0.0", message = "Le poids ne peut pas être négatif")
    @Column(precision = 8, scale = 3)
    private BigDecimal weight;

    @Size(max = 200, message = "L'URL de l'image ne peut pas dépasser 200 caractères")
    @Column(name = "image_url")
    private String imageUrl;

    @DecimalMin(value = "0.0", message = "Le taux de TVA ne peut pas être négatif")
    @DecimalMax(value = "100.0", message = "Le taux de TVA ne peut pas dépasser 100%")
    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.valueOf(18); // Taux de TVA par défaut à 18%

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_featured")
    private Boolean isFeatured = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Relations
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> orderItems = new ArrayList<>();

    // Audit
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Méthodes utilitaires
    public BigDecimal getPriceIncludingVat() {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        if (vatRate == null) {
            return unitPrice;
        }
        BigDecimal vatRateDecimal = vatRate.divide(BigDecimal.valueOf(100));
        return unitPrice.add(unitPrice.multiply(vatRateDecimal));
    }

    public boolean isLowStock() {
        return stock != null && minStock != null && stock <= minStock;
    }

    public boolean isOutOfStock() {
        return stock == null || stock <= 0;
    }

}