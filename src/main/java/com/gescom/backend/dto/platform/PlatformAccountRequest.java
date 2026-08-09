package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Modification du compte proprietaire : email et, facultativement, mot de passe.
 *
 * {@code currentPassword} est toujours exige, meme pour un simple changement d'email : c'est
 * ce qui distingue le titulaire du compte de quelqu'un ayant mis la main sur une session
 * ouverte. Sans lui, detourner l'adresse de recuperation suffirait a prendre le controle de
 * toute la plateforme.
 *
 * {@code newPassword} vide signifie « ne pas changer le mot de passe ».
 */
public record PlatformAccountRequest(
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Format d'email invalide")
        @Size(max = 100)
        String email,

        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        String currentPassword,

        String newPassword
) {
}
