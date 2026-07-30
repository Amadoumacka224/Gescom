package com.gescom.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Paramétrage de l'intégration Stripe (préfixe {@code stripe} dans application.properties).
 *
 * L'intégration est volontairement bornée au mode test :
 * <ul>
 *   <li>{@code simulated} (défaut) — passerelle locale, aucun appel réseau, aucune clé requise.
 *       C'est ce mode qui permet de démontrer le parcours complet avec une clé factice.</li>
 *   <li>{@code api} — appels réels vers l'environnement de test de Stripe, avec une clé
 *       secrète de test {@code sk_test_...}.</li>
 * </ul>
 *
 * Une clé {@code sk_live_} est refusée au démarrage : cette application n'a aucune raison
 * d'encaisser réellement, et un mauvais paramétrage doit échouer bruyamment plutôt que
 * débiter une vraie carte.
 */
@Component
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {

    private static final Logger log = LoggerFactory.getLogger(StripeProperties.class);

    /** Mode d'exécution : {@code simulated} ou {@code api}. */
    private String mode = "simulated";

    /** Clé secrète Stripe. Ignorée en mode simulé ; doit être une clé de test en mode api. */
    private String apiKey = "";

    /** Devise ISO 4217 en minuscules (Stripe l'exige ainsi). */
    private String currency = "eur";

    public boolean isSimulated() {
        return !"api".equalsIgnoreCase(mode);
    }

    @PostConstruct
    void validate() {
        if (apiKey != null && apiKey.startsWith("sk_live_")) {
            throw new IllegalStateException(
                    "Clé Stripe live détectée : cette intégration est réservée au mode test (sk_test_...)");
        }
        if (isSimulated()) {
            log.info("Stripe en mode simulé : aucune requête ne sera envoyée à Stripe.");
        } else if (apiKey == null || !apiKey.startsWith("sk_test_")) {
            throw new IllegalStateException(
                    "stripe.mode=api exige une clé secrète de test (STRIPE_API_KEY=sk_test_...)");
        } else {
            log.info("Stripe en mode test réel : appels vers api.stripe.com avec une clé sk_test_.");
        }
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
