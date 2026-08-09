package com.gescom.backend.dto.platform;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Abonnement expose au back-office proprietaire.
 *
 * {@code monthlyAmount} accompagne {@code amount} : c'est la contribution reelle du contrat
 * au MRR, deja mensualisee pour un contrat annuel. L'afficher evite que l'interface refasse
 * ce calcul de son cote, avec le risque d'une regle d'arrondi divergente.
 */
public record SubscriptionResponse(
        Long id,
        Long companyId,
        String companyName,
        Long planId,
        String planCode,
        String planName,
        String status,
        String billingPeriod,
        BigDecimal amount,
        BigDecimal monthlyAmount,
        String currency,
        LocalDateTime startedAt,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        LocalDateTime canceledAt,
        String cancelReason
) {
}
