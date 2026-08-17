package com.gescom.backend.dto.delivery;

import com.gescom.backend.entity.Delivery;

import java.time.LocalDate;

/**
 * Critères de recherche de l'écran Livraisons. Tous facultatifs, tous combinables.
 *
 * @param search        recherche libre : numéro de livraison, numéro de commande, client,
 *                      contact sur place, ville de livraison
 * @param status        statut de la livraison
 * @param late          vrai pour ne garder que les livraisons en retard — ce n'est PAS un
 *                      statut mais une date prévue dépassée sur une livraison encore en attente
 * @param clientId      client de la commande livrée
 * @param city          ville de livraison, valeur exacte
 * @param country       pays de livraison, valeur exacte
 * @param contact       fragment du nom du contact sur place
 * @param scheduledFrom borne basse de la date prévue, incluse
 * @param scheduledTo   borne haute de la date prévue, incluse — la journée entière est prise
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
