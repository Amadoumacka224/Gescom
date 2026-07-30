package com.gescom.backend.dto.payment;

import com.gescom.backend.dto.invoice.InvoiceResponse;
import com.gescom.backend.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * État d'une transaction du terminal.
 *
 * La facture complète est embarquée (et avec elle la commande) : après une confirmation,
 * le terminal doit pouvoir afficher le nouveau statut de règlement sans second appel.
 *
 * {@code clientSecret} n'est renseigné qu'à la création de l'intention ; {@code simulated}
 * dit à l'interface si elle parle à Stripe ou à la passerelle locale, pour l'annoncer
 * franchement plutôt que de laisser croire à un vrai débit.
 */
public record PaymentResponse(
        Long id,
        String provider,
        String intentId,
        String clientSecret,
        BigDecimal amount,
        String currency,
        Payment.PaymentStatus status,
        String cardBrand,
        String cardLast4,
        String failureMessage,
        boolean simulated,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        InvoiceResponse invoice
) {
}
