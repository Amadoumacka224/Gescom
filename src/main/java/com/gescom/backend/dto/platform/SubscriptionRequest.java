package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Souscription ou changement de formule.
 *
 * Comme pour les autres DTO du projet : des ids simples, jamais d'objets imbriques.
 * {@code amount} est facultatif : vide, on prend le prix de la formule ; rempli, ca permet de
 * garder un prix negocie que le catalogue ne doit pas ecraser.
 */
public record SubscriptionRequest(
        @NotNull(message = "L'entreprise est obligatoire")
        Long companyId,

        @NotNull(message = "La formule est obligatoire")
        Long planId,

        @NotNull(message = "La periodicite est obligatoire")
        String billingPeriod,

        BigDecimal amount
) {
}
