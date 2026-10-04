package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.NotBlank;

/**
 * Ajout d'un message dans un ticket.
 *
 * {@code internal} a vrai indique une note interne, qui ne serait pas envoyee au client le
 * jour ou on ajoutera un envoi automatique.
 */
public record SupportMessageRequest(
        @NotBlank(message = "Le message ne peut pas etre vide")
        String body,

        Boolean internal
) {
}
