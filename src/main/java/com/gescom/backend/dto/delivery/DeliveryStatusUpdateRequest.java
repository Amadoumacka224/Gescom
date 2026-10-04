package com.gescom.backend.dto.delivery;

import jakarta.validation.constraints.NotBlank;

/**
 * Body de PATCH /deliveries/{id}/status. Le statut arrive en texte et est converti dans le
 * contrôleur (avec un message clair si la valeur ne correspond à aucun statut).
 */
public record DeliveryStatusUpdateRequest(
        @NotBlank(message = "Le champ 'status' est obligatoire")
        String status
) {
}
