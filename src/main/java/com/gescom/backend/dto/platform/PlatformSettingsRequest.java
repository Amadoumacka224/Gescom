package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Seuils du tableau de bord.
 *
 * Les bornes reprennent celles des CHECK en base : la validation echoue ici avec un message
 * lisible plutot que plus loin avec une violation d'integrite.
 */
public record PlatformSettingsRequest(
        @NotNull(message = "La fenetre de renouvellement est obligatoire")
        @Min(value = 1, message = "La fenetre de renouvellement doit etre d'au moins 1 jour")
        @Max(value = 365, message = "La fenetre de renouvellement ne peut pas depasser 365 jours")
        Integer renewalWindowDays,

        @NotNull(message = "Le delai d'alerte de fin d'essai est obligatoire")
        @Min(value = 1, message = "Le delai d'alerte doit etre d'au moins 1 jour")
        @Max(value = 90, message = "Le delai d'alerte ne peut pas depasser 90 jours")
        Integer trialAlertDays,

        @NotNull(message = "La profondeur d'historique est obligatoire")
        @Min(value = 1, message = "L'historique doit couvrir au moins 1 mois")
        @Max(value = 60, message = "L'historique ne peut pas depasser 60 mois")
        Integer revenueHistoryMonths,

        @NotNull(message = "La penalite par impaye est obligatoire")
        @Min(value = 0, message = "La penalite ne peut pas etre negative")
        @Max(value = 50, message = "La penalite ne peut pas depasser 50 points")
        Integer overduePenaltyPoints,

        @NotNull(message = "La penalite par echec de paiement est obligatoire")
        @Min(value = 0, message = "La penalite ne peut pas etre negative")
        @Max(value = 50, message = "La penalite ne peut pas depasser 50 points")
        Integer failedPaymentPenaltyPoints
) {
}
