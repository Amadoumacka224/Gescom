package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Enregistrement d'un encaissement d'abonnement.
 *
 * Le montant est saisi plutot que deduit de l'abonnement : un versement peut etre partiel,
 * porter une regularisation ou un geste commercial. Le statut est saisi lui aussi, un echec
 * de prelevement devant pouvoir etre journalise au meme titre qu'un succes.
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
