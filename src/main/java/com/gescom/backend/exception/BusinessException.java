package com.gescom.backend.exception;

/**
 * Exception de base quand une règle métier n'est pas respectée (ex : changement de statut
 * interdit, paiement négatif). C'est une RuntimeException pour ne pas avoir de throws partout.
 * Transformée en HTTP 400 par {@link GlobalExceptionHandler}.
 *
 * Deux façons de la lancer :
 *   - {@code new BusinessException("message")} : texte direct, pas traduit ;
 *   - {@link #of(String, String, Object...)} : clé de traduction + texte français de secours,
 *     traduit dans la langue de l'appelant par {@link GlobalExceptionHandler}. À utiliser
 *     dès que le message est affiché à l'utilisateur.
 */
public class BusinessException extends RuntimeException implements LocalizedException {

    private static final Object[] NO_ARGS = new Object[0];

    private final String messageKey;
    private final Object[] messageArgs;

    public BusinessException(String message) {
        this(null, NO_ARGS, message);
    }

    /**
     * @param messageKey clé dans {@code i18n/messages*.properties}
     * @param fallback   message en français, utilisé si la clé n'existe pas
     * @param args       valeurs à insérer ({@code {0}}, {@code {1}}…)
     */
    public static BusinessException of(String messageKey, String fallback, Object... args) {
        return new BusinessException(messageKey, args, fallback);
    }

    protected BusinessException(String messageKey, Object[] messageArgs, String fallback) {
        super(fallback);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs == null ? NO_ARGS : messageArgs;
    }

    @Override
    public String getMessageKey() {
        return messageKey;
    }

    @Override
    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
