package com.gescom.backend.service.stripe;

import com.gescom.backend.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Map;

/**
 * Passerelle par défaut : rejoue le protocole de Stripe sans sortir de la machine.
 *
 * Elle sert le cas prévu par l'énoncé — démontrer le parcours complet avec une clé factice,
 * donc sans compte Stripe. Les identifiants produits suivent la forme du prestataire
 * ({@code pi_sim_...}, {@code ..._secret_...}) et les moyens de paiement acceptés sont ceux
 * du bac à sable de Stripe ({@code pm_card_visa}, {@code pm_card_chargeDeclined}, …), pour
 * que le passage en {@code stripe.mode=api} ne demande aucune retouche du terminal.
 *
 * Volontairement sans état : le résultat se déduit du moyen de paiement présenté, l'intention
 * n'est pas mémorisée ici. Ce qui fait foi côté application, c'est la ligne {@code payments} —
 * dupliquer cet état dans une map en mémoire n'apporterait rien et ferait échouer les
 * confirmations après un redémarrage, au beau milieu d'une démonstration.
 */
@Component
@ConditionalOnProperty(name = "stripe.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedStripeGateway implements StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedStripeGateway.class);

    private static final String ID_PREFIX = "pi_sim_";
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * Cartes de test reconnues, calquées sur celles de Stripe. La marque et les quatre
     * derniers chiffres sont ceux que renverrait le prestataire pour le même jeton.
     */
    private static final Map<String, TestCard> TEST_CARDS = Map.of(
            "pm_card_visa", TestCard.accepted("VISA", "4242"),
            "pm_card_mastercard", TestCard.accepted("MASTERCARD", "4444"),
            "pm_card_chargeDeclined", TestCard.declined("VISA", "0002",
                    "Carte refusée par l'émetteur"),
            "pm_card_chargeDeclinedInsufficientFunds", TestCard.declined("VISA", "9995",
                    "Provision insuffisante"),
            "pm_card_chargeDeclinedExpiredCard", TestCard.declined("VISA", "0069",
                    "Carte expirée"),
            "pm_card_chargeDeclinedIncorrectCvc", TestCard.declined("VISA", "0127",
                    "Cryptogramme visuel incorrect")
    );

    private final SecureRandom random = new SecureRandom();

    @Override
    public StripeIntent createIntent(long amountInCents, String currency, String description,
                                     Map<String, String> metadata) {
        String id = ID_PREFIX + randomToken(16);
        log.info("[Stripe simulé] Intention {} créée : {} {} — {}", id, amountInCents, currency, description);
        return StripeIntent.created(id, id + "_secret_" + randomToken(16));
    }

    @Override
    public StripeIntent confirmIntent(String intentId, String paymentMethodId) {
        requireSimulatedIntent(intentId);

        TestCard card = TEST_CARDS.get(paymentMethodId);
        if (card == null) {
            // Erreur d'intégration, pas un refus de l'émetteur : le terminal a présenté un
            // jeton que Stripe lui-même ne connaîtrait pas.
            throw new BusinessException("Moyen de paiement de test inconnu : " + paymentMethodId);
        }

        if (card.declineReason() != null) {
            log.info("[Stripe simulé] Intention {} refusée ({})", intentId, card.declineReason());
            return StripeIntent.failed(intentId, null, card.brand(), card.last4(), card.declineReason());
        }

        log.info("[Stripe simulé] Intention {} acceptée ({} •••• {})", intentId, card.brand(), card.last4());
        return StripeIntent.succeeded(intentId, null, card.brand(), card.last4());
    }

    @Override
    public void cancelIntent(String intentId) {
        requireSimulatedIntent(intentId);
        log.info("[Stripe simulé] Intention {} annulée", intentId);
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    /**
     * Refuse une intention issue d'un autre mode : basculer {@code stripe.mode} en cours de
     * route laisserait sinon confirmer en local une intention créée chez Stripe.
     */
    private void requireSimulatedIntent(String intentId) {
        if (intentId == null || !intentId.startsWith(ID_PREFIX)) {
            throw new BusinessException(
                    "Intention " + intentId + " inconnue de la passerelle simulée "
                    + "(créée alors que stripe.mode=api ?)");
        }
    }

    private String randomToken(int length) {
        StringBuilder token = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            token.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return token.toString();
    }

    /** Carte de test : acceptée, ou refusée avec son motif. */
    private record TestCard(String brand, String last4, String declineReason) {
        static TestCard accepted(String brand, String last4) {
            return new TestCard(brand, last4, null);
        }

        static TestCard declined(String brand, String last4, String reason) {
            return new TestCard(brand, last4, reason);
        }
    }
}
