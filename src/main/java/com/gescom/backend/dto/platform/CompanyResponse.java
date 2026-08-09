package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;

/**
 * Fiche d'une entreprise cliente telle que la voit le proprietaire de la plateforme.
 *
 * Les compteurs ({@code userCount}, {@code orderCount}...) et l'abonnement courant sont
 * inclus : la liste des entreprises est l'ecran ou l'on juge d'un coup d'oeil de la sante
 * d'un compte, et un aller-retour par entreprise pour les obtenir serait un N+1 assume.
 */
public record CompanyResponse(
        Long id,
        String name,
        String slug,
        String email,
        String phone,
        String address,
        String city,
        String postalCode,
        String country,
        String taxId,
        String status,
        LocalDateTime trialEndsAt,
        LocalDateTime canceledAt,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        SubscriptionResponse subscription,
        long userCount,
        long orderCount
) {
}
