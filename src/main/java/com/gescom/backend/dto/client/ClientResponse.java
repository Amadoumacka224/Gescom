package com.gescom.backend.dto.client;

import com.gescom.backend.entity.Client;

import java.time.LocalDateTime;

public record ClientResponse(
        Long id,
        String firstName,
        String lastName,
        // Nom complet (prénom + nom) pré-calculé pour l'affichage : figure notamment sur la facture
        // (modal de détail, génération PDF) sans recomposition côté client.
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
