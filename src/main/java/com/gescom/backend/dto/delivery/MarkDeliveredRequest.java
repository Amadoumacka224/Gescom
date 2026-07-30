package com.gescom.backend.dto.delivery;

import jakarta.validation.constraints.Size;

/**
 * Corps de PATCH /deliveries/{id}/mark-delivered.
 * {@code deliveredBy} est optionnel (nom de la personne ayant réceptionné/livré) ;
 * l'utiliser via un DTO évite un NullPointerException sur un corps vide.
 */
public record MarkDeliveredRequest(
        @Size(max = 100)
        String deliveredBy
) {
}
