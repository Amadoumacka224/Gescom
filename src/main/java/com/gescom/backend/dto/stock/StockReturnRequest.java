package com.gescom.backend.dto.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Retour client : le client rend un ou plusieurs articles d'une vente.
 *
 * Le retour est toujours lié à sa commande d'origine : c'est ce qui permet de contrôler les
 * quantités, de retrouver le prix payé et de suivre l'opération du début à la fin. On ne
 * demande pas la facture : on la retrouve à partir de la commande, quel que soit le numéro
 * utilisé pour la chercher.
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
