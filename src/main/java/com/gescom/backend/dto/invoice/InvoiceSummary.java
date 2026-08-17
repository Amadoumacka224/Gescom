package com.gescom.backend.dto.invoice;

import java.math.BigDecimal;
import java.util.List;

/**
 * Compteurs d'en-tête de l'écran Factures.
 *
 * Ils décrivent l'ensemble des factures du périmètre, jamais la page affichée.
 *
 * <p>{@code collected} et {@code pending} portent sur les factures NON ANNULÉES seulement, et
 * l'identité {@code pending = facturé − collected} est garantie par construction : l'écran
 * affiche les deux côte à côte, et compter l'encaissement d'une facture annulée sans compter
 * son reliquat — ou l'inverse — donnerait un taux d'encaissement qui n'est celui d'aucun
 * périmètre.
 *
 * @param total     factures émises, annulées comprises
 * @param collected montant encaissé sur les factures vivantes
 * @param pending   reste à encaisser sur les mêmes
 * @param overdue   factures dont l'échéance est dépassée, ni soldées ni annulées
 * @param unpaid    factures au statut UNPAID
 * @param partial   factures au statut PARTIALLY_PAID
 * @param paid      factures soldées
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
    /** Clients ayant au moins une facture, pour la liste déroulante du filtre. */
    public record ClientOption(Long id, String label) {
    }

    /** Options du filtre, servies avec la synthèse : les deux décrivent le même périmètre. */
    public record FilterOptions(List<ClientOption> clients) {
    }
}
