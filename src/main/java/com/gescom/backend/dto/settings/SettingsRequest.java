package com.gescom.backend.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Corps de PUT /settings. DTO dédié plutôt que l'entité JPA {@code Settings} pour :
 *   - empêcher le mass-assignment (id / createdAt / updatedAt non exposés) ;
 *   - valider les champs obligatoires (contraintes NOT NULL en base) en amont,
 *     de façon à renvoyer un 400 clair plutôt qu'un 409 d'intégrité.
 */
public record SettingsRequest(
        // Général
        @NotBlank(message = "La langue est obligatoire") @Size(max = 10)
        String language,

        @NotBlank(message = "La devise est obligatoire") @Size(max = 10)
        String currency,

        @NotBlank(message = "Le fuseau horaire est obligatoire") @Size(max = 50)
        String timezone,

        @NotBlank(message = "Le format de date est obligatoire") @Size(max = 20)
        String dateFormat,

        // Entreprise
        @NotBlank(message = "Le nom de l'entreprise est obligatoire") @Size(max = 200)
        String companyName,

        @Size(max = 100) String companyEmail,
        @Size(max = 30) String companyPhone,
        @Size(max = 255) String companyAddress,
        @Size(max = 100) String companyCity,
        @Size(max = 20) String companyPostalCode,
        @Size(max = 100) String companyCountry,
        @Size(max = 50) String companyTaxId,
        @Size(max = 50) String companyIban,
        @Size(max = 20) String companyBic,

        // Facturation
        @NotNull(message = "Le taux de TVA est obligatoire")
        @PositiveOrZero(message = "Le taux de TVA doit être positif ou nul")
        Double taxRate,

        @NotBlank(message = "Le préfixe de facture est obligatoire") @Size(max = 10)
        String invoicePrefix,

        @NotNull(message = "Le numéro de facture de départ est obligatoire")
        @PositiveOrZero(message = "Le numéro de départ doit être positif ou nul")
        Integer invoiceNumberStart,

        @NotNull(message = "Le délai de paiement est obligatoire")
        @PositiveOrZero(message = "Le délai de paiement doit être positif ou nul")
        Integer paymentTerms,

        String footerText,

        // Notifications
        @NotNull Boolean notifications,
        @NotNull Boolean emailNotifications,
        @NotNull Boolean orderNotifications,
        @NotNull Boolean stockAlerts,

        @NotNull(message = "Le seuil de stock bas est obligatoire")
        @PositiveOrZero(message = "Le seuil de stock bas doit être positif ou nul")
        Integer lowStockThreshold,

        // Apparence
        @NotBlank(message = "Le thème est obligatoire") @Size(max = 20)
        String theme
) {
}
