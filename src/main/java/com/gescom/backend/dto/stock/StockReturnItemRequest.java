package com.gescom.backend.dto.stock;

import com.gescom.backend.entity.StockReturnItem;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Un article rendu au sein d'un retour. Conformément au contrat des DTO de requête, le produit
 * est désigné par son identifiant scalaire, jamais par un objet imbriqué.
 */
public record StockReturnItemRequest(
        @NotNull(message = "L'identifiant du produit est obligatoire")
        Long productId,

        @NotNull(message = "La quantité est obligatoire")
        @Positive(message = "La quantité doit être strictement positive")
        Integer quantity,

        @NotNull(message = "Le motif du retour est obligatoire")
        StockReturnItem.ReturnReason reason,

        @NotNull(message = "Le type de traitement est obligatoire")
        StockReturnItem.ReturnTreatment treatment,

        /**
         * Article remis en échange, ignoré hors traitement {@code EXCHANGE}.
         * Absent, l'échange se fait à l'identique (même produit).
         */
        Long replacementProductId
) {
}
