package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creation d'un ticket depuis le back-office.
 *
 * {@code description} n'est pas une colonne du ticket : elle devient le premier message.
 * Comme ca tout le contenu du ticket est au meme endroit, au lieu d'avoir un resume d'un
 * cote et la discussion de l'autre.
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

        /** Personne a contacter chez le client, si on la connait. */
        Long contactUserId
) {
}
