package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;

/**
 * Ligne du journal d'activite vue depuis la plateforme.
 *
 * La difference avec {@code ActivityLogResponse}, c'est l'entreprise : dans un journal global,
 * savoir qu'un article a ete supprime ne sert a rien si on ne sait pas chez qui.
 *
 * Pour l'auteur, on ne renvoie que son nom et son role, pas toute sa fiche : le super admin
 * gere des comptes, il n'a pas besoin du detail des employes de ses clients.
 */
public record PlatformActivityResponse(
        Long id,
        Long companyId,
        String companyName,
        String userFullName,
        String userRole,
        String actionType,
        String entity,
        Long entityId,
        String description,
        LocalDateTime createdAt
) {
}
