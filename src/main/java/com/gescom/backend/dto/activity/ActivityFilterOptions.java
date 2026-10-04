package com.gescom.backend.dto.activity;

import java.util.List;

/**
 * Valeurs possibles pour les filtres du journal, tirées du journal lui-même.
 *
 * On les prend en base et pas dans une liste fixe côté front : dans le journal global du
 * back-office, les entités présentes dépendent de ce que les entreprises ont vraiment fait,
 * et proposer un filtre qui ne donne aucun résultat ne sert à rien.
 *
 * Les types d'action sont renvoyés en String plutôt qu'en enum, comme partout sous
 * {@code /api/platform} : le back-office affiche juste des valeurs, il n'a pas besoin du modèle.
 */
public record ActivityFilterOptions(
        List<String> actionTypes,
        List<String> entities
) {
}
