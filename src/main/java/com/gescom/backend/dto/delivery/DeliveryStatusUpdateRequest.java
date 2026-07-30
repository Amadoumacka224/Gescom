package com.gescom.backend.dto.delivery;

import jakarta.validation.constraints.NotBlank;

/**
 * Corps de PATCH /deliveries/{id}/status. Le statut cible est reçu en texte puis converti côté
 * contrôleur (message d'erreur explicite si la valeur ne correspond à aucun statut connu).
 */
public record DeliveryStatusUpdateRequest(
        @NotBlank(message = "Le champ 'status' est obligatoire")
        String status
) {
}
