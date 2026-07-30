package com.gescom.backend.exception;

/**
 * Exception de base pour toute violation d'une règle métier (ex : transition de statut invalide,
 * paiement négatif). Non vérifiée (RuntimeException) pour ne pas alourdir les signatures.
 * Mappée en HTTP 400 par {@link GlobalExceptionHandler}.
 *
 * Deux façons de la lever :
 *   - {@code new BusinessException("message")} — littéral, non traduit ;
 *   - {@link #of(String, String, Object...)} — clé de traduction et repli français, résolue dans
 *     la langue de l'appelant par {@link GlobalExceptionHandler}. À préférer dès que le message
 *     remonte jusqu'à l'utilisateur.
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
     * @param fallback   message français, servi si la clé est absente des catalogues
     * @param args       arguments de substitution ({@code {0}}, {@code {1}}…)
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
