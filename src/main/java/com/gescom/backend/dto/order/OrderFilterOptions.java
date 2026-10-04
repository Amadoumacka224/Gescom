package com.gescom.backend.dto.order;

import java.util.List;

/**
 * Valeurs proposées dans les listes déroulantes des filtres de l'écran Commandes.
 *
 * Avant, elles étaient calculées à partir des commandes chargées dans le navigateur. Il faut
 * la liste complète (un utilisateur ou une ville qui n'apparaît qu'en page 3 doit être
 * proposé dès la page 1), ce qu'on n'a plus en partant de la page affichée.
 *
 * On reste dans le périmètre de l'appelant : un caissier ne voit que lui-même comme
 * utilisateur, et seulement les villes de ses propres ventes.
 *
 * @param operators utilisateurs qui ont saisi au moins une vente, avec leur libellé
 * @param cities    villes des clients qui ont commandé
 */
public record OrderFilterOptions(
        List<Operator> operators,
        List<String> cities
) {
    /**
     * Le libellé est construit côté serveur : « prénom nom », ou le login à défaut. Si chaque
     * appelant refaisait cette règle, on finirait par avoir des différences.
     */
    public record Operator(Long id, String label) {
    }
}
