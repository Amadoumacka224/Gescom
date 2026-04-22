package com.gescom.backend.dto.user;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        String currentPassword,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        String newPassword
) {
}
