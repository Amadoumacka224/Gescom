package com.gescom.backend.dto.product;

import java.math.BigDecimal;

/**
 * Compteurs en haut de l'écran Produits.
 *
 * Ils portent sur TOUT le catalogue, jamais sur la page affichée ni sur une recherche :
 * « 12 ruptures » doit rester vrai quelle que soit la page. C'est pour ça que c'est une
 * réponse à part : la liste est paginée, ces chiffres non.
 *
 * @param total      nombre de produits
 * @param outOfStock produits avec un stock à zéro
 * @param lowStock   produits sous leur seuil d'alerte, sans compter les ruptures (les deux
 *                   compteurs ne se chevauchent pas, l'écran les additionne)
 * @param stockValue valeur du stock au prix d'achat
 */
public record ProductCatalogSummary(
        long total,
        long outOfStock,
        long lowStock,
        BigDecimal stockValue
) {
}
