package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Creation ou modification d'une formule du catalogue.
 *
 * Le {@code code} n'est lu qu'a la creation : c'est l'identifiant stable de la formule, cite
 * dans les contrats et les exports, et le laisser changer romprait ce lien. La modification
 * ignore donc ce champ, comme le slug d'une entreprise.
 *
 * {@code maxUsers} / {@code maxProducts} laisses vides valent « illimite ».
 */
public record PlanRequest(
        @NotBlank(message = "Le code de la formule est obligatoire")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$",
                 message = "Le code doit etre en majuscules, chiffres et tirets bas")
        String code,

        @NotBlank(message = "Le nom de la formule est obligatoire")
        @Size(max = 100)
        String name,

        @Size(max = 255)
        String description,

        @NotNull(message = "Le tarif mensuel est obligatoire")
        @DecimalMin(value = "0.0", message = "Le tarif mensuel ne peut pas etre negatif")
        BigDecimal monthlyPrice,

        @NotNull(message = "Le tarif annuel est obligatoire")
        @DecimalMin(value = "0.0", message = "Le tarif annuel ne peut pas etre negatif")
        BigDecimal yearlyPrice,

        @Min(value = 1, message = "Le plafond d'utilisateurs doit etre au moins 1")
        Integer maxUsers,

        @Min(value = 1, message = "Le plafond d'articles doit etre au moins 1")
        Integer maxProducts,

        @NotNull(message = "La duree d'essai est obligatoire")
        @Min(value = 0, message = "La duree d'essai ne peut pas etre negative")
        Integer trialDays,

        Boolean active,

        Integer sortOrder
) {
}
