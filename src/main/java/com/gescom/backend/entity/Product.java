package com.gescom.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gescom.backend.tenancy.TenantEntityListener;
import com.gescom.backend.tenancy.TenantOwned;
import org.hibernate.annotations.Filter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité produit du catalogue.
 * Porte le prix d'achat (coût) et le prix de vente, la quantité en stock et le seuil
 * d'alerte minStockAlert qui déclenche les notifications de stock bas. Les contraintes de
 * validation (prix positifs, stock ≥ 0, code unique) protègent l'intégrité des données.
 */
@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(name = "uq_products_company_code", columnNames = {"company_id", "code"}))
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Product implements TenantOwned {

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

    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String code;

    @NotBlank(message = "Le nom du produit est obligatoire")
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Le prix d'achat est obligatoire")
    @DecimalMin(value = "0.0", inclusive = true, message = "Le prix d'achat doit être positif ou nul")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    @NotNull(message = "Le prix de vente est obligatoire")
    @DecimalMin(value = "0.0", inclusive = true, message = "Le prix de vente doit être positif ou nul")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal sellingPrice;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @Size(max = 50)
    @Column(length = 50)
    private String unit = "pièce";

    @NotNull
    @PositiveOrZero(message = "Le stock ne peut pas être négatif")
    @Column(nullable = false)
    private Integer stockQuantity = 0;

    @NotNull
    @PositiveOrZero(message = "Le seuil d'alerte doit être positif ou nul")
    @Column(nullable = false)
    private Integer minStockAlert = 10;

    @Size(max = 50)
    @Column(length = 50)
    private String barcode;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @NotNull
    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Callbacks JPA : horodatage automatique de création et de dernière modification.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt = LocalDateTime.now();
    }
}
