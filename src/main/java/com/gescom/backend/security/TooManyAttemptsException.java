package com.gescom.backend.security;

import com.gescom.backend.exception.LocalizedException;

/**
 * Levee quand un couple identifiant/adresse a epuise ses tentatives de connexion.
 *
 * Distincte de {@code BadCredentialsException}, et c'est volontaire : le message ne dit pas si
 * l'identifiant existe ni si le mot de passe etait bon, seulement que la porte est fermee pour
 * un temps. Mappee en 429 par {@code GlobalExceptionHandler} — ni 401, qui laisserait croire a
 * un simple echec de plus, ni 403, qui suggererait un droit manquant.
 */
public class TooManyAttemptsException extends RuntimeException implements LocalizedException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Trop de tentatives de connexion. Reessayez dans " + retryAfterSeconds + " secondes.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Delai restant, en secondes. Sert aussi a renseigner l'en-tete HTTP {@code Retry-After}. */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public String getMessageKey() {
        return "auth.tooManyAttempts";
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[]{retryAfterSeconds};
    }
}
