package com.gescom.backend.dto.delivery;

import com.gescom.backend.entity.Delivery;

import java.time.LocalDate;

/**
 * Filtres de l'écran Livraisons. Tous facultatifs et combinables.
 *
 * @param search        recherche libre : numéro de livraison, numéro de commande, client,
 *                      contact sur place, ville de livraison
 * @param status        statut de la livraison
 * @param late          vrai pour garder seulement les livraisons en retard (ce n'est PAS un
 *                      statut, c'est une livraison encore en attente dont la date est passée)
 * @param clientId      client de la commande
 * @param city          ville de livraison, valeur exacte
 * @param country       pays de livraison, valeur exacte
 * @param contact       morceau du nom du contact sur place
 * @param scheduledFrom date prévue minimum, incluse
 * @param scheduledTo   date prévue maximum, incluse (toute la journée)
 */
public record DeliverySearchCriteria(
        String search,
        Delivery.DeliveryStatus status,
        boolean late,
        Long clientId,
        String city,
        String country,
        String contact,
        LocalDate scheduledFrom,
        LocalDate scheduledTo
) {
}
