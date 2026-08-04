package com.gescom.backend.dto.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Requête de retour client : le client rend un ou plusieurs articles d'une vente identifiée.
 *
 * Le retour est toujours rattaché à sa commande d'origine — c'est ce qui permet de contrôler les
 * quantités, de retrouver le prix payé et de tracer l'opération de bout en bout. La facture n'est
 * pas demandée : elle est déduite de la commande, qu'on l'ait retrouvée par l'un ou l'autre numéro.
 */
public record StockReturnRequest(
        @NotNull(message = "La commande d'origine est obligatoire")
        Long orderId,

        @NotEmpty(message = "Sélectionnez au moins un article à retourner")
        @Valid
        List<StockReturnItemRequest> items,

        @Size(max = 500)
        String notes
) {
}
