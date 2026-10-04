package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creation ou modification d'une entreprise cliente depuis le back-office.
 *
 * Pas de slug ici : il est genere a partir du nom a la creation puis ne change plus (un
 * identifiant stable n'a d'interet que s'il ne bouge pas). Pas de statut non plus : il change
 * via les actions prevues (suspendre, reactiver, resilier), qui ont chacune leurs regles.
 */
public record CompanyRequest(
        @NotBlank(message = "Le nom de l'entreprise est obligatoire")
        @Size(max = 150)
        String name,

        @NotBlank(message = "L'email de l'entreprise est obligatoire")
        @Email(message = "Format d'email invalide")
        @Size(max = 100)
        String email,

        @Pattern(regexp = "^$|^[0-9+\\- ]{6,30}$", message = "Format de telephone invalide")
        String phone,

        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String country,
        @Size(max = 50) String taxId,
        @Size(max = 500) String notes
) {
}
