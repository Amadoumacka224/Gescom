package com.gescom.backend.dto.stock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body des opérations de stock avec une quantité : entrée (/add), sortie (/remove) et casse (/damage).
 *
 * Les trois font la même chose (tant d'unités sur tel produit, avec un motif) et ne
 * changeaient que par leurs champs facultatifs. Le type d'opération vient de la route, pas
 * du body ; {@code unitCost} n'est utilisé que pour une entrée et {@code reference} pas pour
 * une casse, mais on ne refuse pas la requête si ces champs sont envoyés quand même.
 *
 * L'ajustement d'inventaire a son propre DTO : il donne un stock cible
 * ({@link StockAdjustRequest#newQuantity()}), pas une quantité à ajouter ou enlever.
 */
public record StockMovementRequest(
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
