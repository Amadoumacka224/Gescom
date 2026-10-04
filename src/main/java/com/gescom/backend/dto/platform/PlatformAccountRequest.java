package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Modification du compte super admin : email et, si on veut, mot de passe.
 *
 * {@code currentPassword} est toujours demande, meme pour changer juste l'email : c'est ce
 * qui prouve qu'on est bien le titulaire du compte et pas quelqu'un qui a recupere une
 * session ouverte. Sinon, il suffirait de changer l'adresse de recuperation pour prendre le
 * controle de toute la plateforme.
 *
 * {@code newPassword} vide = on ne change pas le mot de passe.
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
