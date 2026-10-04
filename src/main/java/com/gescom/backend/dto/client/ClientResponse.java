package com.gescom.backend.dto.client;

import com.gescom.backend.entity.Client;

import java.time.LocalDateTime;

public record ClientResponse(
        Long id,
        String firstName,
        String lastName,
        // Nom complet (prénom + nom) déjà calculé pour l'affichage : utilisé par exemple sur la
        // facture (détail, PDF) sans avoir à le reconstruire côté front.
        String name,
        String email,
        String phone,
        String address,
        String city,
        String postalCode,
        String country,
        String company,
        Client.ClientType type,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
