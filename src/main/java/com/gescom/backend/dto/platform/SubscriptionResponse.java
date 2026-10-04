package com.gescom.backend.dto.platform;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Abonnement renvoye au back-office.
 *
 * On renvoie {@code monthlyAmount} en plus de {@code amount} : c'est ce que le contrat
 * rapporte vraiment par mois dans le MRR (deja divise par 12 pour un contrat annuel). Comme
 * ca le front n'a pas a refaire le calcul, avec le risque d'arrondir differemment.
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
