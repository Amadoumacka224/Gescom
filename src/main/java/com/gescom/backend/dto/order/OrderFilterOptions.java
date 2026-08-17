package com.gescom.backend.dto.order;

import java.util.List;

/**
 * Valeurs proposées par les listes déroulantes des filtres de l'écran Commandes.
 *
 * Elles étaient déduites des commandes chargées dans le navigateur. Elles doivent rester
 * exhaustives — un opérateur ou une ville qui n'apparaît qu'en page 3 doit être proposé depuis
 * la page 1 —, ce qu'une déduction sur la page affichée ne donne plus.
 *
 * Le périmètre est celui de l'appelant : un caissier ne se voit proposer que lui-même comme
 * opérateur, et les seules villes de ses propres ventes.
 *
 * @param operators opérateurs ayant saisi au moins une vente, avec un libellé déjà composé
 * @param cities    villes des clients ayant commandé
 */
public record OrderFilterOptions(
        List<Operator> operators,
        List<String> cities
) {
    /**
     * Le libellé est composé côté serveur : l'écran affichait « prénom nom », à défaut le nom
     * de connexion. Recomposer cette règle dans chaque appelant la ferait diverger.
     */
    public record Operator(Long id, String label) {
    }
}
