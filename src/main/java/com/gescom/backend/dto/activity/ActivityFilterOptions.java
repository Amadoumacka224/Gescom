package com.gescom.backend.dto.activity;

import java.util.List;

/**
 * Valeurs proposables dans les filtres du journal, relevées dans le journal lui-même.
 *
 * Elles sont tirées de la base et non d'une liste figée côté client : sur le journal consolidé
 * du back-office, l'inventaire des entités tracées dépend de ce que les entreprises du parc ont
 * réellement fait, et proposer un critère qui ne rend aucun résultat n'aide personne.
 *
 * Les types d'action sont rendus en chaînes plutôt qu'en enum, comme partout sous
 * {@code /api/platform} : le back-office affiche des jetons, il ne reconstruit pas le domaine.
 */
public record ActivityFilterOptions(
        List<String> actionTypes,
        List<String> entities
) {
}
