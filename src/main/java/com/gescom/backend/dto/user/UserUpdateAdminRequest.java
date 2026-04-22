package com.gescom.backend.dto.user;

import com.gescom.backend.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateAdminRequest(
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Format d'email invalide")
        @Size(max = 100)
        String email,

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100)
        String lastName,

        @Pattern(regexp = "^$|^[0-9+\\- ]{6,20}$", message = "Format de téléphone invalide")
        String phone,

        @NotNull(message = "Le rôle est obligatoire")
        User.Role role,

        @NotNull(message = "Le statut actif est obligatoire")
        Boolean active,

        String password
) {
}
