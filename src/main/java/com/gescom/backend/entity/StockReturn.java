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
 * Entête d'un retour client, toujours rattaché à la vente d'origine.
 *
 * Un retour n'existe jamais « dans le vide » : il pointe la commande (et, si elle est facturée,
 * la facture) qui a fait sortir la marchandise du stock. C'est ce rattachement qui rend le
 * contrôle des quantités possible — on ne peut pas rendre plus que ce qui a été vendu — et qui
 * donne la piste d'audit complète : quelle vente, quel article, quel motif, quel traitement,
 * par qui et quand. Les mouvements de stock générés portent le {@code returnNumber} en
 * référence, ce qui relie le grand livre à ce document.
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
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Renseignée dès que la vente est facturée : c'est la pièce à laquelle se rattache un
    // éventuel remboursement. Null pour une commande seulement confirmée.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @OneToMany(mappedBy = "stockReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockReturnItem> items = new ArrayList<>();

    // Quantité totale rendue, toutes lignes confondues.
    @Column(nullable = false)
    private Integer totalQuantity = 0;

    // Somme des lignes traitées en remboursement, au prix effectivement payé (remises de ligne
    // et globale déduites). Les lignes remises en stock ou échangées n'y contribuent pas.
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
        if (returnNumber == null) {
            returnNumber = "RET-" + System.currentTimeMillis();
        }
    }
}
