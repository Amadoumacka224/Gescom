package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Enregistrement d'un paiement d'abonnement.
 *
 * Le montant est saisi et pas calcule a partir de l'abonnement : un paiement peut etre
 * partiel, une regularisation ou un geste commercial. Le statut est saisi aussi, car on doit
 * pouvoir enregistrer un prelevement rate comme un paiement reussi.
 */
public record SaasPaymentRequest(
        @NotNull(message = "L'entreprise est obligatoire")
        Long companyId,

        Long subscriptionId,

        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0.0", message = "Le montant ne peut pas etre negatif")
        BigDecimal amount,

        @NotNull(message = "Le statut est obligatoire")
        String status,

        @NotNull(message = "Le moyen de paiement est obligatoire")
        String method,

        @Size(max = 255) String failureMessage
) {
}
