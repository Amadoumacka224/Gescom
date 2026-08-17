package com.gescom.backend.dto.client;

import java.util.List;

/**
 * Valeurs proposées par les listes déroulantes des filtres.
 *
 * Elles étaient dérivées du fichier complet chargé dans le navigateur. Ces listes doivent
 * rester exhaustives — une ville qui n'apparaît qu'en page 3 doit être proposée depuis la
 * page 1 —, ce qu'une déduction sur la page affichée ne donnerait plus.
 *
 * Seules les valeurs réellement présentes sont rendues : proposer un critère qui ne ramène
 * aucune ligne n'aide personne.
 */
public record ClientFilterOptions(
        List<String> cities,
        List<String> countries
) {
}
