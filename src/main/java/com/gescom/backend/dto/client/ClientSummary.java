package com.gescom.backend.dto.client;

/**
 * Compteurs en haut de l'écran Clients.
 *
 * Ils portent sur TOUS les clients, jamais sur la page affichée ni sur une recherche :
 * « 42 entreprises » doit rester vrai quelle que soit la page. C'est pour ça que c'est une
 * réponse à part : la liste est paginée, ces chiffres non.
 *
 * @param total       nombre de clients
 * @param active      clients actifs
 * @param individuals clients PARTICULIER
 * @param companies   clients ENTREPRISE
 */
public record ClientSummary(
        long total,
        long active,
        long individuals,
        long companies
) {
}
