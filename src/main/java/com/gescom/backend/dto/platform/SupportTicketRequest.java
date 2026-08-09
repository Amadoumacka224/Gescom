package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ouverture d'un ticket depuis le back-office.
 *
 * {@code description} n'est pas une colonne du ticket : elle devient le premier message du
 * fil. Un ticket n'a ainsi qu'un seul endroit ou lire son contenu, au lieu d'un resume
 * fige d'un cote et la conversation de l'autre.
 */
public record SupportTicketRequest(
        @NotNull(message = "L'entreprise est obligatoire")
        Long companyId,

        @NotBlank(message = "L'objet est obligatoire")
        @Size(max = 200)
        String subject,

        @NotBlank(message = "La description est obligatoire")
        String description,

        @NotNull(message = "La priorite est obligatoire")
        String priority,

        @NotNull(message = "La categorie est obligatoire")
        String category,

        /** Interlocuteur chez le client, quand il est identifie. */
        Long contactUserId
) {
}
