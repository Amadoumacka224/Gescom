package com.gescom.backend.dto.invoice;

import java.math.BigDecimal;
import java.util.List;

/**
 * Compteurs en haut de l'écran Factures.
 *
 * Ils portent sur toutes les factures, jamais sur la page affichée.
 *
 * <p>{@code collected} et {@code pending} ne comptent que les factures NON ANNULÉES, et on a
 * toujours {@code pending = facturé − collected}. L'écran affiche les deux côte à côte : si
 * on comptait ce qui a été payé sur une facture annulée sans compter ce qui reste (ou
 * l'inverse), le taux d'encaissement ne voudrait plus rien dire.
 *
 * @param total     factures émises, annulées comprises
 * @param collected montant encaissé sur les factures non annulées
 * @param pending   reste à encaisser sur ces mêmes factures
 * @param overdue   factures en retard (échéance passée, ni payées ni annulées)
 * @param unpaid    factures UNPAID
 * @param partial   factures PARTIALLY_PAID
 * @param paid      factures payées
 * @param canceled  factures annulées
 */
public record InvoiceSummary(
        long total,
        BigDecimal collected,
        BigDecimal pending,
        long overdue,
        long unpaid,
        long partial,
        long paid,
        long canceled
) {
    /** Clients qui ont au moins une facture, pour la liste du filtre. */
    public record ClientOption(Long id, String label) {
    }

    /** Options du filtre, renvoyées avec les compteurs : les deux portent sur les mêmes factures. */
    public record FilterOptions(List<ClientOption> clients) {
    }
}
