package com.gescom.backend.service.stripe;

import com.gescom.backend.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Map;

/**
 * Passerelle par défaut : imite le fonctionnement de Stripe sans sortir de la machine.
 *
 * Elle sert pour le cas demandé dans l'énoncé : montrer tout le parcours avec une fausse
 * clé, donc sans compte Stripe. Les ids ont le même format que chez Stripe
 * ({@code pi_sim_...}, {@code ..._secret_...}) et les cartes acceptées sont celles du mode
 * test de Stripe ({@code pm_card_visa}, {@code pm_card_chargeDeclined}, …), pour qu'on puisse
 * passer en {@code stripe.mode=api} sans rien changer au terminal.
 *
 * Pas d'état exprès : le résultat dépend juste de la carte envoyée, on ne garde pas
 * l'intention ici. Ce qui compte côté appli, c'est la ligne dans {@code payments} : garder
 * la même chose dans une map en mémoire ne servirait à rien, et les confirmations
 * échoueraient après un redémarrage, en pleine démo.
 */
@Component
@ConditionalOnProperty(name = "stripe.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedStripeGateway implements StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedStripeGateway.class);

    private static final String ID_PREFIX = "pi_sim_";
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * Cartes de test acceptées, copiées sur celles de Stripe. La marque et les quatre derniers
     * chiffres sont ceux que Stripe renverrait pour le même token.
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
        log.info("[Stripe simulé] Intention {} créée : {} {} - {}", id, amountInCents, currency, description);
        return StripeIntent.created(id, id + "_secret_" + randomToken(16));
    }

    @Override
    public StripeIntent confirmIntent(String intentId, String paymentMethodId) {
        requireSimulatedIntent(intentId);

        TestCard card = TEST_CARDS.get(paymentMethodId);
        if (card == null) {
            // Erreur d'intégration, pas un refus de la banque : le terminal a envoyé un token
            // que Stripe lui-même ne connaîtrait pas.
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
     * Refuse une intention créée dans l'autre mode : si on changeait {@code stripe.mode} en
     * cours de route, on pourrait sinon confirmer en local une intention créée chez Stripe.
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

    /** Carte de test : acceptée, ou refusée avec la raison. */
    private record TestCard(String brand, String last4, String declineReason) {
        static TestCard accepted(String brand, String last4) {
            return new TestCard(brand, last4, null);
        }

        static TestCard declined(String brand, String last4, String reason) {
            return new TestCard(brand, last4, reason);
        }
    }
}
