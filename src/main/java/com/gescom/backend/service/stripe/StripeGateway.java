package com.gescom.backend.service.stripe;

import com.gescom.backend.entity.Payment;

import java.util.Map;

/**
 * Accès au prestataire de paiement, limité aux trois actions du paiement carte : créer
 * l'intention, la confirmer, l'annuler.
 *
 * Il y a deux implémentations, {@link SimulatedStripeGateway} (par défaut, hors ligne) et
 * {@link StripeApiGateway} (environnement de test Stripe), choisies avec {@code stripe.mode}.
 * Le reste de l'appli ne connaît que cette interface : passer du mode simulé au vrai mode
 * test ne change rien au service, au contrôleur ni au terminal.
 */
public interface StripeGateway {

    /**
     * Crée l'intention de paiement chez Stripe.
     *
     * @param amountInCents montant en centimes (Stripe le demande)
     * @param currency      devise ISO 4217 en minuscules
     * @param description   texte lisible, affiché dans le tableau de bord Stripe
     * @param metadata      infos métier (facture, commande) liées à la transaction
     */
    StripeIntent createIntent(long amountInCents, String currency, String description, Map<String, String> metadata);

    /**
     * Confirme l'intention avec une carte de test ({@code pm_card_visa}, etc.).
     * Un refus de la banque n'est pas une erreur technique : il est renvoyé dans le
     * {@link StripeIntent} avec le statut {@link Payment.PaymentStatus#FAILED} et la raison.
     */
    StripeIntent confirmIntent(String intentId, String paymentMethodId);

    /** Annule la session du terminal (client parti, mauvais montant…). */
    void cancelIntent(String intentId);

    boolean isSimulated();
}
