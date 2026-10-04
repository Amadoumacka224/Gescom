package com.gescom.backend.dto.product;

import jakarta.validation.constraints.NotNull;

/**
 * Modification directe du stock d'un produit (PATCH /products/{id}/stock).
 * La quantité est une variation avec signe (positif = entrée, négatif = sortie). Elle est
 * obligatoire pour éviter un NullPointerException au calcul du nouveau stock.
 */
public record StockUpdateRequest(
        @NotNull(message = "La quantité est obligatoire")
        Integer quantity
) {
}
