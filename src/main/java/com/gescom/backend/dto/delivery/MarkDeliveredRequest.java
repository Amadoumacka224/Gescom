package com.gescom.backend.dto.delivery;

import jakarta.validation.constraints.Size;

/**
 * Body de PATCH /deliveries/{id}/mark-delivered.
 * {@code deliveredBy} est optionnel (nom de la personne qui a livré / réceptionné) ;
 * passer par un DTO évite un NullPointerException quand le body est vide.
 */
public record MarkDeliveredRequest(
        @Size(max = 100)
        String deliveredBy
) {
}
