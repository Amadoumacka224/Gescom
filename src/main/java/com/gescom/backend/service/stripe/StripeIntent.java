package com.gescom.backend.service.stripe;

import com.gescom.backend.entity.Payment;

/**
 * Réponse du prestataire, normalisée : ce que les deux passerelles savent dire d'une intention.
 *
 * @param id             identifiant d'intention ({@code pi_...})
 * @param clientSecret   secret d'usage unique, transmis au terminal et jamais persisté
 * @param status         statut traduit dans le vocabulaire du domaine
 * @param cardBrand      marque de la carte, connue seulement après confirmation
 * @param cardLast4      quatre derniers chiffres, idem
 * @param failureMessage motif de refus, renseigné uniquement si {@code status = FAILED}
 */
public record StripeIntent(
        String id,
        String clientSecret,
        Payment.PaymentStatus status,
        String cardBrand,
        String cardLast4,
        String failureMessage
) {
    public static StripeIntent created(String id, String clientSecret) {
        return new StripeIntent(id, clientSecret, Payment.PaymentStatus.REQUIRES_CONFIRMATION, null, null, null);
    }

    public static StripeIntent succeeded(String id, String clientSecret, String cardBrand, String cardLast4) {
        return new StripeIntent(id, clientSecret, Payment.PaymentStatus.SUCCEEDED, cardBrand, cardLast4, null);
    }

    public static StripeIntent failed(String id, String clientSecret, String cardBrand, String cardLast4, String message) {
        return new StripeIntent(id, clientSecret, Payment.PaymentStatus.FAILED, cardBrand, cardLast4, message);
    }
}
