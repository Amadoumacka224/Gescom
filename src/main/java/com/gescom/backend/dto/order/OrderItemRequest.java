package com.gescom.backend.dto.order;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderItemRequest(
        @NotNull(message = "L'identifiant du produit est obligatoire")
        Long productId,

        @NotNull(message = "La quantité est obligatoire")
        @Positive(message = "La quantité doit être strictement positive")
        Integer quantity,

        // Remise en pourcentage (0–100) appliquée à la ligne. Optionnelle : null = aucune remise.
        @DecimalMin(value = "0", message = "La remise ne peut pas être négative")
        @DecimalMax(value = "100", message = "La remise ne peut pas dépasser 100%")
        BigDecimal discount
) {
}
