package com.gescom.backend.dto.stock;

import com.gescom.backend.entity.StockReturnItem;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Un article rendu dans un retour. Comme pour les autres DTO de requête, le produit est
 * donné par son id, jamais par un objet imbriqué.
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
         * Article donné en échange, ignoré si le traitement n'est pas {@code EXCHANGE}.
         * S'il est absent, l'échange se fait avec le même produit.
         */
        Long replacementProductId
) {
}
