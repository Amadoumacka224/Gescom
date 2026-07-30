package com.gescom.backend.service.stripe;

import com.gescom.backend.config.StripeProperties;
import com.gescom.backend.exception.BusinessException;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Passerelle branchée sur l'environnement de test de Stripe ({@code stripe.mode=api}).
 *
 * Même contrat que la passerelle simulée, mêmes jetons de carte de test : seul le
 * paramétrage change. La clé est passée par requête ({@link RequestOptions}) plutôt que via
 * le champ statique {@code Stripe.apiKey}, pour ne pas installer d'état global dans la JVM.
 *
 * Un refus de l'émetteur remonte en {@link CardException} : c'est une réponse métier normale,
 * traduite en intention FAILED. Seules les vraies pannes (réseau, clé invalide, montant
 * refusé par l'API) deviennent des {@link BusinessException}.
 */
@Component
@ConditionalOnProperty(name = "stripe.mode", havingValue = "api")
public class StripeApiGateway implements StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeApiGateway.class);

    private final RequestOptions requestOptions;

    public StripeApiGateway(StripeProperties properties) {
        this.requestOptions = RequestOptions.builder()
                .setApiKey(properties.getApiKey())
                .build();
    }

    @Override
    public StripeIntent createIntent(long amountInCents, String currency, String description,
                                     Map<String, String> metadata) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .setDescription(description)
                    .putAllMetadata(metadata)
                    // Carte uniquement, et confirmation pilotée par le terminal : pas de
                    // moyens de paiement automatiques, donc aucune redirection à gérer.
                    .addPaymentMethodType("card")
                    .build();

            PaymentIntent intent = PaymentIntent.create(params, requestOptions);
            log.info("[Stripe test] Intention {} créée : {} {}", intent.getId(), amountInCents, currency);
            return StripeIntent.created(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            throw new BusinessException("Création du paiement Stripe impossible : " + e.getMessage());
        }
    }

    @Override
    public StripeIntent confirmIntent(String intentId, String paymentMethodId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(intentId, requestOptions);
            PaymentIntent confirmed = intent.confirm(
                    PaymentIntentConfirmParams.builder().setPaymentMethod(paymentMethodId).build(),
                    requestOptions);

            return switch (confirmed.getStatus()) {
                case "succeeded" -> {
                    CardDetails card = cardDetailsOf(confirmed.getPaymentMethod());
                    log.info("[Stripe test] Intention {} acceptée ({} •••• {})",
                            intentId, card.brand(), card.last4());
                    yield StripeIntent.succeeded(intentId, confirmed.getClientSecret(), card.brand(), card.last4());
                }
                // Le terminal encaisse ou refuse : une intention laissée en attente
                // d'authentification n'a pas d'écran pour la porter.
                case "requires_action" -> StripeIntent.failed(intentId, confirmed.getClientSecret(), null, null,
                        "Authentification 3-D Secure requise, non prise en charge par le terminal");
                default -> StripeIntent.failed(intentId, confirmed.getClientSecret(), null, null,
                        "Paiement non abouti (statut Stripe : " + confirmed.getStatus() + ")");
            };
        } catch (CardException e) {
            // Refus de l'émetteur : réponse attendue du parcours, pas une panne.
            log.info("[Stripe test] Intention {} refusée : {}", intentId, e.getMessage());
            return StripeIntent.failed(intentId, null, null, null, e.getStripeError() != null
                    ? e.getStripeError().getMessage()
                    : "Paiement refusé");
        } catch (StripeException e) {
            throw new BusinessException("Confirmation du paiement Stripe impossible : " + e.getMessage());
        }
    }

    @Override
    public void cancelIntent(String intentId) {
        try {
            PaymentIntent.retrieve(intentId, requestOptions).cancel(requestOptions);
            log.info("[Stripe test] Intention {} annulée", intentId);
        } catch (StripeException e) {
            throw new BusinessException("Annulation du paiement Stripe impossible : " + e.getMessage());
        }
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    /**
     * Marque et quatre derniers chiffres de la carte. Purement informatif (ticket, journal) :
     * si Stripe ne les rend pas, on n'échoue pas un paiement déjà accepté.
     */
    private CardDetails cardDetailsOf(String paymentMethodId) {
        if (paymentMethodId == null) {
            return CardDetails.UNKNOWN;
        }
        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId, requestOptions);
            if (paymentMethod.getCard() == null) {
                return CardDetails.UNKNOWN;
            }
            return new CardDetails(
                    paymentMethod.getCard().getBrand() != null
                            ? paymentMethod.getCard().getBrand().toUpperCase()
                            : null,
                    paymentMethod.getCard().getLast4());
        } catch (StripeException e) {
            log.warn("Détail de carte indisponible pour {} : {}", paymentMethodId, e.getMessage());
            return CardDetails.UNKNOWN;
        }
    }

    private record CardDetails(String brand, String last4) {
        static final CardDetails UNKNOWN = new CardDetails(null, null);
    }
}
