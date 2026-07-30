package com.gescom.backend.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Ouverture d'une session de paiement sur le terminal.
 *
 * @param invoiceId facture à encaisser
 * @param amount    montant ; omis, le terminal propose le reste dû de la facture
 */
public record PaymentIntentCreateRequest(
        @NotNull(message = "La facture est obligatoire")
        Long invoiceId,

        @DecimalMin(value = "0.01", message = "Le montant doit être strictement positif")
        BigDecimal amount
) {
}
