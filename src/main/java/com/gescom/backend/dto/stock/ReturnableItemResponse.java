package com.gescom.backend.dto.stock;

import java.math.BigDecimal;

/**
 * Ligne d'une vente vue depuis le module de retours.
 *
 * {@code quantityReturnable} = {@code quantitySold} − {@code quantityReturned} : c'est la seule
 * valeur que l'écran doit laisser saisir, et le service la revalide à l'enregistrement — un
 * client ne peut pas rendre plus qu'il n'a acheté, ni deux fois le même article.
 */
public record ReturnableItemResponse(
        Long productId,
        String productCode,
        String productName,
        String unit,
        Integer quantitySold,
        Integer quantityReturned,
        Integer quantityReturnable,
        // Prix unitaire réellement payé, toutes remises déduites : celle de la ligne, et la
        // part de la remise globale de la vente qui revient à cette ligne. C'est la base d'un
        // remboursement — on ne rend pas plus que ce qui a été encaissé.
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        // Stock actuel du produit, pour situer l'effet du retour avant de le valider.
        Integer stockQuantity
) {
}
