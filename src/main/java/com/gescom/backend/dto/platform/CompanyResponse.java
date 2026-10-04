package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;

/**
 * Fiche d'une entreprise cliente vue par le super admin.
 *
 * On inclut les compteurs ({@code userCount}, {@code orderCount}...) et l'abonnement en cours :
 * la liste des entreprises sert a voir rapidement si un compte va bien, et faire un appel par
 * entreprise pour les avoir serait un N+1.
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
