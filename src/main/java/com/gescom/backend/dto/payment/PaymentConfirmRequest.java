package com.gescom.backend.dto.payment;

import jakarta.validation.constraints.NotBlank;

/**
 * Carte utilisée au terminal, sous forme de moyen de paiement de test Stripe
 * ({@code pm_card_visa}, {@code pm_card_chargeDeclined}, …).
 *
 * Aucun numéro de carte ne passe par cette API : le terminal n'utilise que des tokens, comme
 * dans une vraie intégration où la saisie se fait chez Stripe.
 */
public record PaymentConfirmRequest(
        @NotBlank(message = "Le moyen de paiement est obligatoire")
        String paymentMethodId
) {
}
