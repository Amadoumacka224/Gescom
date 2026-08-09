package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creation ou modification d'une entreprise cliente depuis le back-office proprietaire.
 *
 * Le slug n'y figure pas : il est derive du nom a la creation puis fige, un identifiant
 * stable n'ayant d'interet que s'il ne bouge pas. Le statut non plus — il se pilote par les
 * actions dediees (suspendre, reactiver, resilier), qui portent chacune leur regle metier.
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
