package com.gescom.backend.dto.order;

import jakarta.validation.constraints.NotBlank;

/**
 * Corps de PATCH /orders/{id}/status. Le statut cible est reçu en texte puis converti côté
 * contrôleur (message d'erreur explicite si la valeur ne correspond à aucun statut connu).
 */
public record OrderStatusUpdateRequest(
        @NotBlank(message = "Le statut cible est obligatoire")
        String status
) {
}
