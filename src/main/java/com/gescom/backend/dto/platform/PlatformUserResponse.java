package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;

/**
 * Utilisateur vu depuis le back-office plateforme.
 *
 * Deux differences avec {@code UserResponse}, qui font tout l'interet de l'ecran :
 * l'entreprise (sans elle, une liste globale est illisible) et la derniere connexion, qui
 * repond a la question du support (« est-ce que ce compte sert encore ? »).
 *
 * Pas de telephone ni d'autre donnee perso inutile : le super admin gere des comptes, il n'a
 * pas besoin de la fiche des employes de ses clients.
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
