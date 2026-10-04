package com.gescom.backend.dto.platform;

import java.math.BigDecimal;

/**
 * Une formule.
 *
 * On renvoie aussi {@code subscriptionCount} : c'est ce qui dit au front si on peut supprimer
 * la formule. Une formule deja souscrite peut seulement etre desactivee, sinon les anciens
 * contrats pointeraient vers rien.
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
