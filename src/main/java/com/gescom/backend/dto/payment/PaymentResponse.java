package com.gescom.backend.dto.payment;

import com.gescom.backend.dto.invoice.InvoiceResponse;
import com.gescom.backend.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * État d'une transaction du terminal.
 *
 * On renvoie toute la facture (et donc la commande) : après une confirmation, le terminal
 * peut afficher le nouveau statut de paiement sans refaire d'appel.
 *
 * {@code clientSecret} n'est rempli qu'à la création de l'intention ; {@code simulated}
 * indique au front s'il parle à Stripe ou à la passerelle locale, pour l'afficher clairement
 * et ne pas faire croire à un vrai paiement.
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
