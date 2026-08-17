package com.gescom.backend.dto.delivery;

import java.util.List;

/**
 * Compteurs d'en-tête de l'écran Livraisons.
 *
 * Ils décrivent l'ensemble des livraisons du périmètre, jamais la page affichée.
 *
 * <p>{@code late} n'est pas un statut mais une date prévue dépassée sur une livraison encore
 * en attente : une livraison effectuée en retard n'est plus en retard, elle est faite. Le
 * compteur ne recoupe donc pas {@code pending + delivered}, il en désigne un sous-ensemble.
 *
 * @param total     livraisons enregistrées
 * @param pending   livraisons restant à effectuer
 * @param delivered livraisons effectuées
 * @param late      livraisons en attente dont la date prévue est passée
 */
public record DeliverySummary(
        long total,
        long pending,
        long delivered,
        long late
) {
    /** Une valeur proposée par un filtre : un identifiant et son libellé déjà composé. */
    public record Option(Long id, String label) {
    }

    /**
     * Valeurs proposées par les listes déroulantes des filtres.
     *
     * Elles doivent rester exhaustives — une ville qui n'apparaît qu'en page 3 doit être
     * proposée depuis la page 1 —, ce qu'une déduction sur la page affichée ne donne plus.
     */
    public record FilterOptions(
            List<Option> clients,
            List<String> cities,
            List<String> countries
    ) {
    }
}
