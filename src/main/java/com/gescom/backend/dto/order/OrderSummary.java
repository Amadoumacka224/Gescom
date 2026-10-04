package com.gescom.backend.dto.order;

/**
 * Compteurs des cartes de l'écran Commandes, un par statut.
 *
 * Ils portent sur toutes les commandes que l'appelant peut voir (un caissier ne voit que les
 * siennes), jamais sur la page affichée : « 12 en attente » doit rester vrai quelle que soit
 * la page, et les cartes servent justement à filtrer.
 *
 * Par contre ils ne tiennent PAS compte de la recherche en cours, comme avant : les cartes
 * montrent la situation globale, et on filtre ensuite.
 */
public record OrderSummary(
        long total,
        long pending,
        long confirmed,
        long invoiced,
        long delivered,
        long canceled
) {
}
