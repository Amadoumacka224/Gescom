package com.gescom.backend.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreateRequest(
        // Optionnel : null pour une vente de passage (aucun client enregistré).
        Long clientId,

        @NotEmpty(message = "La commande doit contenir au moins un article")
        @Valid
        List<OrderItemRequest> items,

        // Remise commerciale globale en euros, déduite du total HT. La TVA n'est pas de ce
        // ressort : elle est calculée à la facturation, à partir du taux saisi sur la facture.
        @DecimalMin(value = "0.0", inclusive = true, message = "La remise doit être positive ou nulle")
        BigDecimal discount,

        @Size(max = 500)
        String notes
) {
}
