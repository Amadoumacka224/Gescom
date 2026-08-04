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
 * Le prix unitaire est celui de la ligne de commande d'origine, toutes remises déduites (celle
 * de la ligne, plus la part de la remise globale de la vente), afin que le montant remboursé
 * reste celui réellement payé, même si le tarif du produit change ensuite.
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
     * Article remis au client en échange, uniquement pour un traitement {@code EXCHANGE}.
     * Par défaut le même produit (échange à l'identique de l'unité rendue) — c'est lui qui
     * ressort du stock, l'article rendu y étant réintégré.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_product_id")
    private Product replacementProduct;

    @Column(nullable = false)
    private Integer quantity;

    // Prix unitaire net payé sur la vente d'origine (remise de ligne déduite).
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    // Montant remboursé pour cette ligne : quantité × prix unitaire si traitement REFUND, sinon 0.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnTreatment treatment;

    /** Motif du retour. Une liste fermée plutôt qu'un texte libre : c'est ce qui rend les retours analysables. */
    public enum ReturnReason {
        DEFECTIVE,      // Produit défectueux
        DAMAGED,        // Produit endommagé (transport, manipulation)
        WRONG_ITEM,     // Article livré ne correspondant pas à la commande
        NOT_SATISFIED,  // Client non satisfait
        ORDER_ERROR,    // Erreur de saisie de la commande
        OTHER           // Autre motif — à préciser dans les notes du retour
    }

    /**
     * Traitement appliqué à l'article rendu. Chaque valeur décrit un effet de stock précis,
     * appliqué par {@code StockReturnService} :
     *
     * <ul>
     *   <li>{@code RESTOCK} — remise en stock simple : la marchandise réintègre le stock
     *       vendable (mouvement RETURN), sans contrepartie financière.</li>
     *   <li>{@code REFUND} — remboursement : même réintégration au stock, et le montant payé
     *       pour la ligne est tracé sur le retour.</li>
     *   <li>{@code EXCHANGE} — échange : l'article rendu réintègre le stock (RETURN) et
     *       l'article de remplacement en ressort (STOCK_OUT). À l'identique, les deux
     *       mouvements se compensent — mais restent tous deux tracés.</li>
     * </ul>
     */
    public enum ReturnTreatment {
        RESTOCK,
        REFUND,
        EXCHANGE
    }
}
