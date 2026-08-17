package com.gescom.backend.dto.product;

import java.math.BigDecimal;

/**
 * Compteurs d'en-tête de l'écran Produits.
 *
 * Ils décrivent le catalogue ENTIER, jamais la page affichée ni le résultat d'une recherche :
 * « 12 ruptures » doit rester vrai quelle que soit la page ouverte. C'est aussi la raison
 * d'être de cette réponse séparée — la liste est paginée, ces chiffres ne le sont pas.
 *
 * @param total      nombre de produits au catalogue
 * @param outOfStock produits à stock nul
 * @param lowStock   produits sous leur seuil d'alerte, rupture exclue (les deux compteurs ne
 *                   se recouvrent pas : l'écran les additionne pour décrire l'ensemble)
 * @param stockValue valeur du stock au prix d'achat
 */
public record ProductCatalogSummary(
        long total,
        long outOfStock,
        long lowStock,
        BigDecimal stockValue
) {
}
