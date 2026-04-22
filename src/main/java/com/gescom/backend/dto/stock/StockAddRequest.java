package com.gescom.backend.dto.stock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockAddRequest(
        @NotNull(message = "L'identifiant du produit est obligatoire")
        Long productId,

        @NotNull(message = "La quantité est obligatoire")
        @Positive(message = "La quantité doit être strictement positive")
        Integer quantity,

        @DecimalMin(value = "0.0", inclusive = true, message = "Le coût unitaire doit être positif ou nul")
        BigDecimal unitCost,

        @Size(max = 500)
        String reason,

        @Size(max = 100)
        String reference
) {
}
