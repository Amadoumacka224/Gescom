package com.gescom.backend.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @Size(max = 50)
        String code,

        @NotBlank(message = "Le nom du produit est obligatoire")
        @Size(max = 200)
        String name,

        String description,

        @NotNull(message = "Le prix d'achat est obligatoire")
        @DecimalMin(value = "0.0", inclusive = true, message = "Le prix d'achat doit être positif ou nul")
        BigDecimal purchasePrice,

        @NotNull(message = "Le prix de vente est obligatoire")
        @DecimalMin(value = "0.0", inclusive = true, message = "Le prix de vente doit être positif ou nul")
        BigDecimal sellingPrice,

        Long categoryId,

        @Size(max = 50)
        String unit,

        @PositiveOrZero(message = "Le stock ne peut pas être négatif")
        Integer stockQuantity,

        @PositiveOrZero(message = "Le seuil d'alerte doit être positif ou nul")
        Integer minStockAlert,

        @Size(max = 50)
        String barcode,

        String imageUrl,

        Boolean active
) {
}
