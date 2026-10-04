package com.gescom.backend.tenancy;

import com.gescom.backend.entity.Company;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PrePersist;
import org.springframework.stereotype.Component;

/**
 * Met automatiquement l'entreprise en cours sur chaque entite creee qui est filtree par
 * entreprise.
 *
 * Sans ca, il faudrait modifier chaque service et mapper pour faire {@code setCompany(...)} :
 * une douzaine de fichiers, et autant de risques d'en oublier un, avec une ligne sans
 * entreprise creee sans aucune erreur. En le faisant une fois dans le cycle de vie JPA, on
 * est sur que c'est fait partout, pour le code actuel comme pour le futur.
 *
 * Le champ n'est rempli que s'il est vide : le back-office plateforme peut donc creer une
 * donnee pour une entreprise precise en la renseignant lui-meme.
 */
@Component
public class TenantEntityListener {

    /**
     * Reference statique vers l'instance geree par Spring.
     *
     * Les {@code @EntityListeners} sont crees par Hibernate, et selon la config ils ne passent
     * pas forcement par Spring. Passer par cette reference rend l'injection fiable dans tous
     * les cas.
     */
    private static TenantEntityListener instance;

    /**
     * On utilise directement l'EntityManager, sans repository : le callback est appele pendant
     * le flush, et a ce moment on veut faire le moins de choses possible. {@code getReference}
     * cree juste un proxy sans lire la base, alors que {@code CompanyRepository.getReferenceById}
     * chargerait vraiment l'entite (a cause de la verification de
     * {@link TenantAwareRepositoryImpl}), donc une requete en plein flush.
     */
    @PersistenceContext
    private EntityManager entityManager;

    @PostConstruct
    void register() {
        instance = this;
    }

    @PrePersist
    public void assignCompany(Object entity) {
        if (!(entity instanceof TenantOwned owned) || owned.getOwnerCompany() != null) {
            return;
        }
        Long companyId = TenantContext.getCompanyId();
        if (companyId == null || instance == null) {
            // Vue plateforme ou traitement hors requete : c'est a l'appelant de donner
            // l'entreprise. Sinon la contrainte NOT NULL en base bloquera.
            return;
        }
        Company company = instance.entityManager.getReference(Company.class, companyId);
        owned.setOwnerCompany(company);
    }
}
