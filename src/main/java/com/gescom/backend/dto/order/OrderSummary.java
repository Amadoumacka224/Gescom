package com.gescom.backend.dto.order;

/**
 * Compteurs des tuiles de l'écran Commandes, une par statut.
 *
 * Ils décrivent l'ensemble des commandes visibles par l'appelant — cloisonnement caissier
 * compris —, jamais la page affichée : « 12 en attente » doit rester vrai quelle que soit la
 * page ouverte, et les tuiles servent justement à filtrer, donc à changer de page.
 *
 * Ils ne tiennent en revanche PAS compte des critères de recherche en cours, exactement comme
 * avant : les tuiles annoncent le paysage complet, sur lequel on vient ensuite appliquer un
 * filtre.
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
