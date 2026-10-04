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
import java.util.ArrayList;
import java.util.List;

/**
 * En-tête d'un retour client, toujours lié à la vente d'origine.
 *
 * Un retour pointe toujours vers la commande (et la facture si elle existe) qui a fait sortir
 * la marchandise du stock. C'est ce lien qui permet de contrôler les quantités (on ne peut
 * pas rendre plus que ce qui a été vendu) et de tout retracer : quelle vente, quel article,
 * quel motif, quel traitement, qui et quand. Les mouvements de stock créés ont le
 * {@code returnNumber} comme référence, c'est ce qui les relie à ce document.
 */
@Entity
@Table(name = "stock_returns", uniqueConstraints = @UniqueConstraint(name = "uq_stock_returns_company_number", columnNames = {"company_id", "return_number"}))
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockReturn implements TenantOwned {

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
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Remplie si la vente est facturée : c'est à cette facture qu'on rattache un
    // remboursement. Null pour une commande juste confirmée.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @OneToMany(mappedBy = "stockReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockReturnItem> items = new ArrayList<>();

    // Quantité totale rendue, toutes lignes comprises.
    @Column(nullable = false)
    private Integer totalQuantity = 0;

    // Total des lignes remboursées, au prix vraiment payé (remises de ligne et globale
    // enlevées). Les lignes remises en stock ou échangées ne comptent pas.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        // Numéro donné par DocumentNumberService avant l'enregistrement (voir Order).
    }
}
