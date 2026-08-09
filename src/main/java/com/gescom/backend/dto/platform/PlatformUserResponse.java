package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;

/**
 * Utilisateur vu depuis le back-office proprietaire.
 *
 * Distinct d'{@code UserResponse} sur deux points qui font tout l'interet de l'ecran :
 * l'entreprise d'appartenance — sans elle, une liste consolidee est illisible — et la
 * derniere connexion, qui repond a la question du support (« ce compte sert-il encore ? »).
 *
 * Ni telephone ni aucune donnee personnelle superflue : l'exploitant de la plateforme
 * supervise des comptes, il n'a pas a recevoir la fiche des employes de ses clients.
 */
public record PlatformUserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String role,
        Boolean active,
        Long companyId,
        String companyName,
        String companyStatus,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
