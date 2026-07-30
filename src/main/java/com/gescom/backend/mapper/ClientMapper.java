package com.gescom.backend.mapper;

import com.gescom.backend.dto.client.ClientRequest;
import com.gescom.backend.dto.client.ClientResponse;
import com.gescom.backend.entity.Client;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

    public ClientResponse toResponse(Client client) {
        if (client == null) return null;
        return new ClientResponse(
                client.getId(),
                client.getFirstName(),
                client.getLastName(),
                buildFullName(client),
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

    // Concatène prénom et nom en un nom complet propre (gère les valeurs manquantes).
    private String buildFullName(Client client) {
        return java.util.stream.Stream.of(client.getFirstName(), client.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    public Client toEntity(ClientRequest request) {
        return applyRequest(new Client(), request);
    }

    public Client applyRequest(Client target, ClientRequest request) {
        target.setFirstName(request.firstName());
        target.setLastName(request.lastName());
        target.setEmail(request.email());
        target.setPhone(request.phone());
        target.setAddress(request.address());
        target.setCity(request.city());
        target.setPostalCode(request.postalCode());
        target.setCountry(request.country());
        target.setCompany(request.company());
        target.setType(request.type());
        if (request.active() != null) {
            target.setActive(request.active());
        }
        return target;
    }
}
