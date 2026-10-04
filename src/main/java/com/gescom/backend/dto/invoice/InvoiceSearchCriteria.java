package com.gescom.backend.dto.invoice;

import com.gescom.backend.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Filtres de l'écran Factures. Tous facultatifs et combinables.
 *
 * @param search         recherche libre : numéro de facture, numéro de commande, nom du client
 * @param status         statut de la facture
 * @param overdue        vrai pour garder seulement les factures en retard (ce n'est PAS un
 *                       statut, c'est une facture ni payée ni annulée dont l'échéance est passée)
 * @param clientId       client de la commande facturée
 * @param paymentMethod  mode de paiement
 * @param issuedFrom     date d'émission minimum, incluse
 * @param issuedTo       date d'émission maximum, incluse
 * @param amountMin      total minimum
 * @param amountMax      total maximum
 * @param onlyRemaining  garder seulement les factures avec un reste à payer
 */
public record InvoiceSearchCriteria(
        String search,
        Invoice.InvoiceStatus status,
        boolean overdue,
        Long clientId,
        Invoice.PaymentMethod paymentMethod,
        LocalDate issuedFrom,
        LocalDate issuedTo,
        BigDecimal amountMin,
        BigDecimal amountMax,
        boolean onlyRemaining
) {
}
