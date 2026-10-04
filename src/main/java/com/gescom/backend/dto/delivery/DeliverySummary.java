package com.gescom.backend.dto.delivery;

import java.util.List;

/**
 * Compteurs en haut de l'écran Livraisons.
 *
 * Ils portent sur toutes les livraisons, jamais sur la page affichée.
 *
 * <p>{@code late} n'est pas un statut : c'est une livraison encore en attente dont la date
 * prévue est passée (une livraison faite en retard n'est plus en retard, elle est faite).
 * Ce compteur fait donc partie de {@code pending}, il ne s'ajoute pas.
 *
 * @param total     nombre de livraisons
 * @param pending   livraisons à faire
 * @param delivered livraisons faites
 * @param late      livraisons en attente dont la date prévue est passée
 */
public record DeliverySummary(
        long total,
        long pending,
        long delivered,
        long late
) {
    /** Une option de filtre : un id et son libellé déjà construit. */
    public record Option(Long id, String label) {
    }

    /**
     * Valeurs proposées dans les listes déroulantes des filtres.
     *
     * Il faut la liste complète (une ville qui n'apparaît qu'en page 3 doit être proposée dès
     * la page 1), ce qu'on n'a plus en partant de la page affichée.
     */
    public record FilterOptions(
            List<Option> clients,
            List<String> cities,
            List<String> countries
    ) {
    }
}
