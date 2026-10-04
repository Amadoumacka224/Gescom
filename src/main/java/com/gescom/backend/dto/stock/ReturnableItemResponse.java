package com.gescom.backend.dto.stock;

import java.math.BigDecimal;

/**
 * Ligne d'une vente vue depuis l'écran des retours.
 *
 * {@code quantityReturnable} = {@code quantitySold} − {@code quantityReturned} : c'est la seule
 * quantité que l'écran doit permettre de saisir, et le service la revérifie à
 * l'enregistrement. Un client ne peut pas rendre plus que ce qu'il a acheté, ni rendre deux
 * fois le même article.
 */
public record ReturnableItemResponse(
        Long productId,
        String productCode,
        String productName,
        String unit,
        Integer quantitySold,
        Integer quantityReturned,
        Integer quantityReturnable,
        // Prix unitaire vraiment payé, avec toutes les remises : celle de la ligne et la part
        // de la remise globale qui revient à cette ligne. C'est sur ce prix qu'on rembourse,
        // on ne rend pas plus que ce qui a été payé.
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        // Stock actuel du produit, pour voir l'effet du retour avant de valider.
        Integer stockQuantity
) {
}
