package com.gescom.backend.security;

import com.gescom.backend.exception.LocalizedException;

/**
 * Lancee quand un couple identifiant / adresse a utilise toutes ses tentatives de connexion.
 *
 * Differente de {@code BadCredentialsException} exprès : le message ne dit pas si
 * l'identifiant existe ni si le mot de passe etait bon, juste que c'est bloque pour un
 * moment. Transformee en 429 par {@code GlobalExceptionHandler} : pas 401, qui ferait penser
 * a un echec de plus, ni 403, qui ferait penser a un probleme de droits.
 */
public class TooManyAttemptsException extends RuntimeException implements LocalizedException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Trop de tentatives de connexion. Reessayez dans " + retryAfterSeconds + " secondes.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Temps restant en secondes. Sert aussi pour l'en-tete HTTP {@code Retry-After}. */
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
