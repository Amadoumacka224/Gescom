package com.gescom.backend.tenancy;

import com.gescom.backend.entity.Company;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PrePersist;
import org.springframework.stereotype.Component;

/**
 * Affecte automatiquement l'entreprise courante a toute entite cloisonnee creee.
 *
 * Sans ce dispositif, il faudrait modifier chacun des services et mappers existants pour
 * y renseigner {@code setCompany(...)} — une douzaine de fichiers, et autant d'occasions
 * d'en oublier un, l'oubli produisant silencieusement une ligne non rattachee. Le poser
 * une fois au niveau du cycle de vie JPA garantit l'invariant pour tout code, present
 * comme futur.
 *
 * Le champ n'est renseigne que s'il est vide : cela laisse au back-office proprietaire la
 * possibilite de creer explicitement une donnee pour le compte d'une entreprise donnee.
 */
@Component
public class TenantEntityListener {

    /**
     * Reference statique vers l'instance geree par Spring.
     *
     * Les {@code @EntityListeners} sont instancies par le fournisseur de persistance, dont
     * la resolution via le conteneur Spring n'est pas garantie selon la configuration.
     * Passer par ce relai rend l'injection fiable quel que soit le mode d'instanciation.
     */
    private static TenantEntityListener instance;

    /**
     * L'EntityManager est sollicite directement, sans passer par un repository : le rappel
     * se declenche pendant le flush, moment ou l'on veut le geste le plus inerte possible.
     * {@code getReference} se contente de fabriquer un proxy et ne lit pas la base, la ou
     * {@code CompanyRepository.getReferenceById} declencherait un chargement reel — le
     * controle d'appartenance de {@link TenantAwareRepositoryImpl} l'impose — et donc une
     * requete au milieu d'un flush.
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
            // Vue plateforme ou traitement hors requete : c'est a l'appelant de designer
            // explicitement l'entreprise. La contrainte NOT NULL en base fera le reste.
            return;
        }
        Company company = instance.entityManager.getReference(Company.class, companyId);
        owned.setOwnerCompany(company);
    }
}
