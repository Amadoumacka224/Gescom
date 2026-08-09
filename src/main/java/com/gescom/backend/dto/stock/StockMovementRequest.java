package com.gescom.backend.dto.stock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Corps des écritures de stock à quantité : entrée (/add), sortie (/remove) et casse (/damage).
 *
 * Les trois décrivent le même geste — tant d'unités sur tel produit, avec un motif — et ne
 * différaient que par les champs facultatifs qu'elles acceptaient. Le sens de l'écriture est
 * porté par la route, pas par le corps ; {@code unitCost} n'est retenu qu'à l'entrée et
 * {@code reference} ne l'est pas sur la casse, sans qu'un envoi superflu soit refusé.
 *
 * L'ajustement d'inventaire garde son propre DTO : il porte un stock cible
 * ({@link StockAdjustRequest#newQuantity()}), pas une quantité à ajouter ou à retirer.
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
