package com.gescom.backend.dto.client;

import com.gescom.backend.entity.Client;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientRequest(
        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100)
        String lastName,

        @Email(message = "Format d'email invalide")
        @Size(max = 100)
        String email,

        @NotBlank(message = "Le téléphone est obligatoire")
        @Pattern(regexp = "^[0-9+\\- ]{6,20}$", message = "Format de téléphone invalide")
        String phone,

        @Size(max = 255)
        String address,

        @Size(max = 100)
        String city,

        @Size(max = 20)
        String postalCode,

        @Size(max = 100)
        String country,

        @Size(max = 50)
        String company,

        @NotNull(message = "Le type est obligatoire")
        Client.ClientType type,

        Boolean active
) {
}
