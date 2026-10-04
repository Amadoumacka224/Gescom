package com.gescom.backend.tenancy;

/**
 * Entreprise concernee par la requete en cours.
 *
 * Le contexte est rempli par {@code JwtAuthenticationFilter}, juste apres
 * l'authentification : l'entreprise est lue sur l'utilisateur charge en base, jamais sur
 * une donnee envoyee par le client. Aucun claim du token ne contient l'id d'entreprise,
 * donc il n'y a rien a falsifier de ce cote.
 *
 * Une valeur null veut dire « pas de restriction », dans deux cas : le SUPER_ADMIN, qui doit
 * voir toutes les entreprises ; et les traitements en dehors d'une requete (connexion,
 * demarrage, taches planifiees) qui n'ont pas encore d'entreprise. Ces derniers ne
 * touchent pas aux donnees metier.
 *
 * Il faut absolument nettoyer a la fin de la requete ({@link #clear()}) : les threads du
 * serveur sont reutilises, et un contexte oublie passerait a la requete suivante, donc
 * peut-etre a un autre client.
 */
public final class TenantContext {

    /** Nom du filtre Hibernate declare dans {@code entity/package-info.java}. */
    public static final String FILTER_NAME = "tenantFilter";

    /** Nom du parametre du filtre. */
    public static final String FILTER_PARAM = "tenantCompanyId";

    private static final ThreadLocal<Long> CURRENT_COMPANY = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Limite la requete a une entreprise. */
    public static void setCompanyId(Long companyId) {
        CURRENT_COMPANY.set(companyId);
    }

    /** Entreprise en cours, ou null si la requete n'est pas limitee. */
    public static Long getCompanyId() {
        return CURRENT_COMPANY.get();
    }

    /** Vrai si la requete est limitee a une entreprise (donc pas la vue plateforme). */
    public static boolean isScoped() {
        return CURRENT_COMPANY.get() != null;
    }

    public static void clear() {
        CURRENT_COMPANY.remove();
    }

    /**
     * Execute un traitement sans filtre d'entreprise, puis remet le contexte d'avant.
     *
     * Utile quand un service filtre par entreprise doit lire une donnee commune (les
     * formules, une entreprise). Le contexte d'avant est remis dans un {@code finally} pour
     * qu'une exception ne laisse pas la requete sans filtre.
     */
    public static <T> T callUnscoped(java.util.function.Supplier<T> action) {
        Long previous = CURRENT_COMPANY.get();
        CURRENT_COMPANY.remove();
        try {
            return action.get();
        } finally {
            if (previous != null) {
                CURRENT_COMPANY.set(previous);
            }
        }
    }
}
