package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;

/**
 * Ligne du journal d'activite vue depuis la plateforme.
 *
 * Distincte d'{@code ActivityLogResponse} sur un point qui fait toute la difference a ce
 * niveau : l'entreprise d'origine. Dans un journal consolide, savoir qu'un article a ete
 * supprime n'a de sens que si l'on sait chez qui.
 *
 * L'auteur est reduit a son nom et son role, sans la fiche utilisateur complete : le
 * proprietaire de la plateforme supervise des comptes, il n'a pas a recevoir le detail des
 * employes de ses clients.
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
