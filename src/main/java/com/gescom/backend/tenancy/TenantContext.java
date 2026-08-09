package com.gescom.backend.tenancy;

/**
 * Entreprise a laquelle se rapporte la requete en cours.
 *
 * Le contexte est alimente par {@code JwtAuthenticationFilter}, dans la foulee immediate
 * de l'authentification : l'entreprise est lue sur l'utilisateur charge en base, jamais
 * sur une donnee fournie par le client. Aucun claim du jeton ne porte l'identifiant
 * d'entreprise, il n'y a donc rien a falsifier de ce cote.
 *
 * Une valeur nulle signifie « aucune restriction » et couvre deux situations :
 * le SUPER_ADMIN, proprietaire de la plateforme, qui doit voir l'ensemble du parc ; et
 * les traitements hors requete (connexion, demarrage, taches planifiees) qui n'ont pas
 * encore d'entreprise. Ces derniers ne manipulent pas de donnees metier.
 *
 * Le nettoyage en fin de requete ({@link #clear()}) n'est pas optionnel : les threads
 * du conteneur sont recycles, et un contexte oublie serait herite par la requete
 * suivante — donc par un autre client.
 */
public final class TenantContext {

    /** Nom du filtre Hibernate declare dans {@code entity/package-info.java}. */
    public static final String FILTER_NAME = "tenantFilter";

    /** Nom du parametre attendu par ce filtre. */
    public static final String FILTER_PARAM = "tenantCompanyId";

    private static final ThreadLocal<Long> CURRENT_COMPANY = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Restreint la requete a une entreprise. */
    public static void setCompanyId(Long companyId) {
        CURRENT_COMPANY.set(companyId);
    }

    /** Entreprise courante, ou null si la requete n'est pas cloisonnee. */
    public static Long getCompanyId() {
        return CURRENT_COMPANY.get();
    }

    /** Vrai si la requete est limitee a une entreprise (donc pas une vue plateforme). */
    public static boolean isScoped() {
        return CURRENT_COMPANY.get() != null;
    }

    public static void clear() {
        CURRENT_COMPANY.remove();
    }

    /**
     * Execute un traitement en vue plateforme, puis restaure le contexte precedent.
     *
     * Utile lorsqu'un service cloisonne doit lire une donnee transverse (le catalogue des
     * formules, une entreprise). Le contexte anterieur est restaure dans un {@code finally}
     * pour qu'une exception ne laisse pas la requete en vue globale.
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
