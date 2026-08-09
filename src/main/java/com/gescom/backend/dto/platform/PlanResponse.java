package com.gescom.backend.dto.platform;

import java.math.BigDecimal;

/**
 * Formule du catalogue.
 *
 * {@code subscriptionCount} accompagne la fiche : c'est lui qui dit a l'interface si la
 * suppression est possible — une formule deja souscrite ne peut etre que desactivee, faute
 * de quoi l'historique des contrats pointerait dans le vide.
 */
public record PlanResponse(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal monthlyPrice,
        BigDecimal yearlyPrice,
        Integer maxUsers,
        Integer maxProducts,
        Integer trialDays,
        Boolean active,
        Integer sortOrder,
        long subscriptionCount
) {
}
