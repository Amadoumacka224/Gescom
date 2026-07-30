package com.gescom.backend.service.stripe;

import com.gescom.backend.entity.Payment;

import java.util.Map;

/**
 * Accès au prestataire de paiement, réduit aux trois gestes du parcours carte :
 * créer l'intention, la confirmer, l'abandonner.
 *
 * Deux implémentations coexistent — {@link SimulatedStripeGateway} (défaut, hors ligne) et
 * {@link StripeApiGateway} (environnement de test Stripe) — sélectionnées par
 * {@code stripe.mode}. Tout le reste de l'application ne connaît que cette interface : passer
 * du mode simulé au mode test réel ne change rien au service, au contrôleur ni au terminal.
 */
public interface StripeGateway {

    /**
     * Crée l'intention de paiement côté prestataire.
     *
     * @param amountInCents montant en plus petite unité monétaire (exigence Stripe)
     * @param currency      devise ISO 4217 en minuscules
     * @param description   libellé lisible, repris dans le tableau de bord Stripe
     * @param metadata      références métier (facture, commande) attachées à la transaction
     */
    StripeIntent createIntent(long amountInCents, String currency, String description, Map<String, String> metadata);

    /**
     * Confirme l'intention avec un moyen de paiement de test ({@code pm_card_visa}, etc.).
     * Un refus de l'émetteur n'est pas une erreur technique : il revient dans le
     * {@link StripeIntent} avec le statut {@link Payment.PaymentStatus#FAILED} et son motif.
     */
    StripeIntent confirmIntent(String intentId, String paymentMethodId);

    /** Abandon de la session du terminal (client parti, mauvais montant…). */
    void cancelIntent(String intentId);

    boolean isSimulated();
}
