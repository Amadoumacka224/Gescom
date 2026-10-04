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
 * Historique des mouvements de stock.
 * Chaque ligne garde : le type de mouvement, la quantité, et surtout le stock avant
 * (previousStock) et après (newStock). Ça permet de vérifier ou d'annuler un mouvement sans
 * tout recalculer. On garde aussi l'auteur et le motif.
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
