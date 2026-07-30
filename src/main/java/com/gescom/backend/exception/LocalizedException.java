package com.gescom.backend.exception;

/**
 * Exception dont le message destiné à l'utilisateur est porté par une clé de traduction
 * plutôt que par un littéral.
 *
 * La résolution a lieu en un seul endroit, {@link GlobalExceptionHandler}, avec la locale de
 * la requête courante : c'est le seul moment où l'on sait dans quelle langue répondre. Les
 * services, eux, n'ont pas à connaître la langue de l'appelant et ne dépendent donc pas du
 * {@code MessageSource}.
 *
 * Le message passé à {@code super(...)} reste renseigné en français : il sert de repli si la
 * clé manque dans les catalogues, et c'est lui qu'on retrouve dans les journaux et les tests.
 */
public interface LocalizedException {

    /** Clé dans {@code i18n/messages*.properties}, ou {@code null} pour s'en tenir au littéral. */
    String getMessageKey();

    /** Arguments de substitution ({@code {0}}, {@code {1}}…), jamais {@code null}. */
    Object[] getMessageArgs();
}
