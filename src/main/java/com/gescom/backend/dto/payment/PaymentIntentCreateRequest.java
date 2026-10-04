package com.gescom.backend.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Création d'une session de paiement sur le terminal.
 *
 * @param invoiceId facture à payer
 * @param amount    montant ; s'il est absent, on prend le reste à payer de la facture
 */
public record PaymentIntentCreateRequest(
        @NotNull(message = "La facture est obligatoire")
        Long invoiceId,

        @DecimalMin(value = "0.01", message = "Le montant doit être strictement positif")
        BigDecimal amount
) {
}
