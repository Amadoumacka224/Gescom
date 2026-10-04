package com.gescom.backend.tenancy;

import com.gescom.backend.entity.Company;

/**
 * Verifie qu'une entite appartient bien a l'entreprise de la requete en cours.
 *
 * Indispensable en plus du filtre Hibernate : le filtre modifie les requetes SQL, mais un
 * chargement par id ({@code em.find}) peut venir du cache sans aucun SQL, donc sans filtre.
 * Une entite d'une autre entreprise pourrait alors passer. C'est ce trou que bouche cette
 * classe, appelee par {@link TenantAwareRepositoryImpl}.
 */
public final class TenantGuard {

    private TenantGuard() {
    }

    /**
     * Vrai si l'entite est visible dans le contexte en cours.
     *
     * Les objets pas filtres par entreprise (formules, entreprises, entites sans marqueur)
     * passent toujours : leur acces est controle par {@code @PreAuthorize}, pas par le tenant.
     */
    public static boolean isVisible(Object entity) {
        if (!(entity instanceof TenantOwned owned)) {
            return true;
        }
        Long currentCompanyId = TenantContext.getCompanyId();
        if (currentCompanyId == null) {
            // Vue plateforme : le SUPER_ADMIN voit toutes les entreprises.
            return true;
        }
        Company company = owned.getOwnerCompany();
        // Un SUPER_ADMIN charge par l'ADMIN d'une entreprise n'a pas d'entreprise : aucune
        // entreprise ne doit pouvoir le voir.
        return company != null && currentCompanyId.equals(company.getId());
    }
}
