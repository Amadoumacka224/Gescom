package com.gescom.backend.dto.client;

import com.gescom.backend.entity.Client;

import java.time.LocalDate;

/**
 * Critères de recherche du fichier clients. Tous facultatifs, tous combinables.
 *
 * Regroupés dans un enregistrement plutôt que passés en huit arguments : la signature du
 * service resterait lisible une semaine, et un appelant finirait par intervertir deux chaînes
 * voisines — {@code city} et {@code country} se ressemblent trop pour que le compilateur nous
 * rattrape.
 *
 * @param search      recherche libre sur nom, prénom, société, e-mail, téléphone et ville
 * @param type        PARTICULIER ou ENTREPRISE
 * @param active      état du compte client
 * @param city        ville exacte, telle que proposée par {@link ClientFilterOptions}
 * @param country     pays exact, même origine
 * @param company     fragment de raison sociale (contient, insensible à la casse)
 * @param withEmail   vrai : seulement ceux qui ont un e-mail ; faux : seulement ceux sans
 * @param createdFrom borne basse de création, incluse
 * @param createdTo   borne haute de création, incluse — la journée entière est prise
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
