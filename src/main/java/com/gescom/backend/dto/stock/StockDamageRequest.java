package com.gescom.backend.dto.stock;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StockDamageRequest(
        @NotNull(message = "L'identifiant du produit est obligatoire")
        Long productId,

        @NotNull(message = "La quantité est obligatoire")
        @Positive(message = "La quantité doit être strictement positive")
        Integer quantity,

        @Size(max = 500)
        String reason
) {
}
