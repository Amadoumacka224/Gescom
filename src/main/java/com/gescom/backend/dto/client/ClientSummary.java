package com.gescom.backend.dto.client;

/**
 * Compteurs d'en-tête de l'écran Clients.
 *
 * Ils décrivent le fichier ENTIER, jamais la page affichée ni le résultat d'une recherche :
 * « 42 entreprises » doit rester vrai quelle que soit la page ouverte. C'est la raison d'être
 * de cette réponse séparée — la liste est paginée, ces chiffres ne le sont pas.
 *
 * @param total       clients enregistrés
 * @param active      comptes actifs
 * @param individuals clients de type PARTICULIER
 * @param companies   clients de type ENTREPRISE
 */
public record ClientSummary(
        long total,
        long active,
        long individuals,
        long companies
) {
}
