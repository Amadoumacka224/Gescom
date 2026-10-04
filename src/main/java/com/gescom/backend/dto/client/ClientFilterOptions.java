package com.gescom.backend.dto.client;

import java.util.List;

/**
 * Valeurs proposées dans les listes déroulantes des filtres.
 *
 * Avant, elles étaient calculées à partir de la liste complète chargée dans le navigateur.
 * Il faut la liste complète (une ville qui n'apparaît qu'en page 3 doit être proposée dès la
 * page 1), ce qu'on n'aurait plus en partant de la page affichée.
 *
 * On ne renvoie que les valeurs qui existent : un filtre qui ne donne aucun résultat ne sert
 * à rien.
 */
public record ClientFilterOptions(
        List<String> cities,
        List<String> countries
) {
}
