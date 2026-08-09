package com.gescom.backend.dto.platform;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Souscription ou changement de formule.
 *
 * Conforme au contrat DTO du projet : des identifiants scalaires, jamais d'objets imbriques.
 * {@code amount} est facultatif — laisse vide, le tarif catalogue de la formule s'applique ;
 * renseigne, il permet de figer un tarif negocie que le catalogue ne doit pas ecraser.
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
