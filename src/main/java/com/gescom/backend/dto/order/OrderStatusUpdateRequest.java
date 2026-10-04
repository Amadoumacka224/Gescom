package com.gescom.backend.dto.order;

import jakarta.validation.constraints.NotBlank;

/**
 * Body de PATCH /orders/{id}/status. Le statut arrive en texte et est converti dans le
 * contrôleur (avec un message clair si la valeur ne correspond à aucun statut).
 */
public record OrderStatusUpdateRequest(
        @NotBlank(message = "Le statut cible est obligatoire")
        String status
) {
}
