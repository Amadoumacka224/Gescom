package com.gescom.backend.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

// On ne peut plus changer le statut avec cette requête : il change via les actions prévues
// (confirmation, annulation) et via la facturation / livraison. On ne peut modifier qu'un
// brouillon (PENDING), voir OrderService.updateOrder.
//
// À part `items` (obligatoire, les lignes de la commande sont remplacées par cette liste),
// tous les champs sont vraiment facultatifs : `null` = « on ne change pas », pas « on remet à
// zéro ». Un appelant qui ne gère pas la remise n'a donc pas besoin de la renvoyer pour la
// garder. Pour vider les notes, il faut envoyer une chaîne vide.
public record OrderUpdateRequest(
        @NotEmpty(message = "La commande doit contenir au moins un article")
        @Valid
        List<OrderItemRequest> items,

        @DecimalMin(value = "0.0", inclusive = true, message = "La remise doit être positive ou nulle")
        BigDecimal discount,

        @Size(max = 500)
        String notes
) {
}
