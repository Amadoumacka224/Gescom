package com.gescom.backend.dto.client;

import com.gescom.backend.entity.Client;

import java.time.LocalDate;

/**
 * Filtres de recherche des clients. Tous facultatifs et combinables.
 *
 * Regroupés dans un record plutôt que passés en huit paramètres : avec huit paramètres, on
 * finirait par inverser deux String voisines ({@code city} et {@code country} se ressemblent
 * trop pour que le compilateur le voie).
 *
 * @param search      recherche libre sur nom, prénom, société, e-mail, téléphone et ville
 * @param type        PARTICULIER ou ENTREPRISE
 * @param active      client actif ou non
 * @param city        ville exacte, comme proposée par {@link ClientFilterOptions}
 * @param country     pays exact, même chose
 * @param company     morceau de la raison sociale (contient, sans tenir compte de la casse)
 * @param withEmail   vrai : seulement ceux qui ont un e-mail ; faux : seulement ceux qui n'en ont pas
 * @param createdFrom date de création minimum, incluse
 * @param createdTo   date de création maximum, incluse (toute la journée)
 */
public record ClientSearchCriteria(
        String search,
        Client.ClientType type,
        Boolean active,
        String city,
        String country,
        String company,
        Boolean withEmail,
        LocalDate createdFrom,
        LocalDate createdTo
) {
}
