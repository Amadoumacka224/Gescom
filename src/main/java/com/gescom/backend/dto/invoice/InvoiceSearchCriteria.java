package com.gescom.backend.dto.invoice;

import com.gescom.backend.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Critères de recherche de l'écran Factures. Tous facultatifs, tous combinables.
 *
 * @param search         recherche libre : numéro de facture, numéro de commande, nom du client
 * @param status         statut de la facture
 * @param overdue        vrai pour ne garder que les factures en retard — ce n'est PAS un statut
 *                       mais une échéance dépassée sur une facture ni soldée ni annulée
 * @param clientId       client de la commande facturée
 * @param paymentMethod  moyen de règlement
 * @param issuedFrom     borne basse d'émission, incluse
 * @param issuedTo       borne haute d'émission, incluse
 * @param amountMin      total minimum
 * @param amountMax      total maximum
 * @param onlyRemaining  ne garder que les factures présentant un reliquat à encaisser
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
