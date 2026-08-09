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
import java.time.LocalDateTime;

/**
 * Entité d'historique des mouvements de stock (traçabilité).
 * Chaque ligne fige un instantané : le type de mouvement, la quantité concernée, et surtout
 * le stock avant (previousStock) et après (newStock) — ce qui permet d'auditer et d'annuler
 * un mouvement sans recalculer toute la chaîne. Renseigne aussi l'auteur et le motif.
 */
@Entity
@Table(name = "stock_movements")
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement implements TenantOwned {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer previousStock;

    @Column(nullable = false)
    private Integer newStock;

    @Column(precision = 10, scale = 2)
    private BigDecimal unitCost;

    @Column(length = 500)
    private String reason;

    @Column(length = 100)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum MovementType {
        STOCK_IN,           // Entrée de stock (achat, retour client, etc.)
        STOCK_OUT,          // Sortie de stock (vente, perte, etc.)
        ADJUSTMENT,         // Ajustement d'inventaire
        RETURN,             // Retour client (la marchandise revient en stock)
        DAMAGE,             // Produit endommagé
        TRANSFER            // Transfert entre entrepôts
    }
}
