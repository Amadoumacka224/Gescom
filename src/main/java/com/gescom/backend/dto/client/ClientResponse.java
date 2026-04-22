package com.gescom.backend.dto.client;

import com.gescom.backend.entity.Client;

import java.time.LocalDateTime;

public record ClientResponse(
        Long id,
        String firstName,
        String lastName,
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
    public static ClientResponse from(Client client) {
        if (client == null) return null;
        return new ClientResponse(
                client.getId(),
                client.getFirstName(),
                client.getLastName(),
                client.getEmail(),
                client.getPhone(),
                client.getAddress(),
                client.getCity(),
                client.getPostalCode(),
                client.getCountry(),
                client.getCompany(),
                client.getType(),
                client.getActive(),
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }
}
