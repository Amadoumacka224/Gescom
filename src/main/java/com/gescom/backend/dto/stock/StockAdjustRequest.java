package com.gescom.backend.dto.stock;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record StockAdjustRequest(
        @NotNull(message = "L'identifiant du produit est obligatoire")
        Long productId,

        @NotNull(message = "La nouvelle quantité est obligatoire")
        @PositiveOrZero(message = "La nouvelle quantité ne peut pas être négative")
        Integer newQuantity,

        @Size(max = 500)
        String reason
) {
}
