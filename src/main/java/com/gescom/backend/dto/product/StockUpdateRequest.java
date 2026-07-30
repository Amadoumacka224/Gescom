package com.gescom.backend.dto.product;

import jakarta.validation.constraints.NotNull;

/**
 * Ajustement direct de la quantité en stock d'un produit (PATCH /products/{id}/stock).
 * La quantité est un delta signé (positif = entrée, négatif = sortie) ; elle est obligatoire
 * pour éviter un déréférencement null lors du calcul du nouveau stock.
 */
public record StockUpdateRequest(
        @NotNull(message = "La quantité est obligatoire")
        Integer quantity
) {
}
