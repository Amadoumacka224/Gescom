package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.NotBlank;

/**
 * Ajout d'un message au fil d'un ticket.
 *
 * {@code internal} vrai marque une note de service, qui ne serait pas transmise au client
 * le jour ou un envoi automatique sera branche.
 */
public record SupportMessageRequest(
        @NotBlank(message = "Le message ne peut pas etre vide")
        String body,

        Boolean internal
) {
}
