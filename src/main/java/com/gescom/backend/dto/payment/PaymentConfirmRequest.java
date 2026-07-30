package com.gescom.backend.dto.payment;

import jakarta.validation.constraints.NotBlank;

/**
 * Carte présentée au terminal, sous la forme d'un moyen de paiement de test Stripe
 * ({@code pm_card_visa}, {@code pm_card_chargeDeclined}, …).
 *
 * Aucun numéro de carte ne transite par cette API : le terminal ne manipule que des jetons,
 * comme le ferait une vraie intégration où la saisie a lieu chez le prestataire.
 */
public record PaymentConfirmRequest(
        @NotBlank(message = "Le moyen de paiement est obligatoire")
        String paymentMethodId
) {
}
