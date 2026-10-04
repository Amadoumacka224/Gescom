package com.gescom.backend.exception;

/**
 * Exception dont le message pour l'utilisateur est une clé de traduction et pas un texte
 * en dur.
 *
 * La traduction se fait à un seul endroit, {@link GlobalExceptionHandler}, avec la langue de
 * la requête : c'est le seul moment où on sait dans quelle langue répondre. Les services
 * n'ont donc pas besoin de connaître la langue de l'appelant ni d'utiliser le
 * {@code MessageSource}.
 *
 * Le message passé à {@code super(...)} reste en français : il sert si la clé n'existe pas,
 * et c'est lui qu'on voit dans les logs et les tests.
 */
public interface LocalizedException {

    /** Clé dans {@code i18n/messages*.properties}, ou {@code null} pour garder le texte tel quel. */
    String getMessageKey();

    /** Valeurs à insérer ({@code {0}}, {@code {1}}…), jamais {@code null}. */
    Object[] getMessageArgs();
}
