package com.gescom.backend.tenancy;

import com.gescom.backend.entity.Company;

/**
 * Verifie l'appartenance d'une entite a l'entreprise de la requete courante.
 *
 * Complement indispensable du filtre Hibernate : celui-ci reecrit les requetes SQL, mais
 * un chargement par identifiant ({@code em.find}) peut etre servi depuis le cache de
 * premier niveau sans qu'aucun SQL ne soit emis — donc sans filtre. Une entite d'une autre
 * entreprise remonterait alors intacte. C'est ce trou que ferme cette classe, appelee par
 * {@link TenantAwareRepositoryImpl}.
 */
public final class TenantGuard {

    private TenantGuard() {
    }

    /**
     * Vrai si l'entite est visible dans le contexte courant.
     *
     * Les objets non cloisonnes (formules, entreprises, entites sans marqueur) passent
     * toujours : leur controle d'acces releve de {@code @PreAuthorize}, pas du tenant.
     */
    public static boolean isVisible(Object entity) {
        if (!(entity instanceof TenantOwned owned)) {
            return true;
        }
        Long currentCompanyId = TenantContext.getCompanyId();
        if (currentCompanyId == null) {
            // Vue plateforme : le SUPER_ADMIN voit l'ensemble du parc.
            return true;
        }
        Company company = owned.getOwnerCompany();
        // Un SUPER_ADMIN charge par un ADMIN d'entreprise n'a pas d'entreprise : il ne doit
        // etre visible d'aucun tenant.
        return company != null && currentCompanyId.equals(company.getId());
    }
}
