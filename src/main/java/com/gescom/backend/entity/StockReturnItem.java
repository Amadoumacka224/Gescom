package com.gescom.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ligne de retour : un article rendu, sa quantité, son motif et son traitement.
 *
 * Le prix unitaire est celui de la ligne de commande d'origine, avec toutes les remises
 * (celle de la ligne et la part de la remise globale), pour rembourser ce qui a vraiment été
 * payé même si le prix du produit change après.
 */
@Entity
@Table(name = "stock_return_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_return_id", nullable = false)
    @JsonIgnore
    private StockReturn stockReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Article donné au client en échange, seulement pour un traitement {@code EXCHANGE}.
     * Par défaut c'est le même produit (échange à l'identique). C'est lui qui sort du stock,
     * et l'article rendu y revient.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_product_id")
    private Product replacementProduct;

    @Column(nullable = false)
    private Integer quantity;

    // Prix unitaire net payé lors de la vente (remise de ligne enlevée).
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    // Montant remboursé pour la ligne : quantité × prix unitaire si REFUND, sinon 0.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnTreatment treatment;

    /** Motif du retour. Une liste fixe plutôt que du texte libre, pour pouvoir faire des stats. */
    public enum ReturnReason {
        DEFECTIVE,      // Produit défectueux
        DAMAGED,        // Produit endommagé (transport, manipulation)
        WRONG_ITEM,     // Article livré ne correspondant pas à la commande
        NOT_SATISFIED,  // Client non satisfait
        ORDER_ERROR,    // Erreur de saisie de la commande
        OTHER           // Autre motif — à préciser dans les notes du retour
    }

    /**
     * Ce qu'on fait de l'article rendu. Chaque valeur a un effet précis sur le stock, appliqué
     * par {@code StockReturnService} :
     *
     * <ul>
     *   <li>{@code RESTOCK} : simple remise en stock, la marchandise revient dans le stock
     *       vendable (mouvement RETURN), sans remboursement.</li>
     *   <li>{@code REFUND} : remboursement, la marchandise revient aussi en stock, et le
     *       montant payé pour la ligne est noté sur le retour.</li>
     *   <li>{@code EXCHANGE} : échange, l'article rendu revient en stock (RETURN) et l'article
     *       de remplacement sort (STOCK_OUT). Pour le même article les deux s'annulent, mais
     *       on garde les deux mouvements.</li>
     * </ul>
     */
    public enum ReturnTreatment {
        RESTOCK,
        REFUND,
        EXCHANGE
    }
}
