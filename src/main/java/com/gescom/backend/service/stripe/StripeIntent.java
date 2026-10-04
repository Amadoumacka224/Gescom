package com.gescom.backend.service.stripe;

import com.gescom.backend.entity.Payment;

/**
 * Réponse de Stripe, dans un format commun aux deux passerelles.
 *
 * @param id             id de l'intention ({@code pi_...})
 * @param clientSecret   secret à usage unique, envoyé au terminal et jamais enregistré
 * @param status         statut converti dans nos propres valeurs
 * @param cardBrand      marque de la carte, connue seulement après la confirmation
 * @param cardLast4      quatre derniers chiffres, pareil
 * @param failureMessage raison du refus, remplie seulement si {@code status = FAILED}
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
