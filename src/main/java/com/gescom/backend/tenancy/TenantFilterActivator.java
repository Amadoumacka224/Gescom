package com.gescom.backend.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Active le filtre Hibernate de cloisonnement avant tout acces au package repository.
 *
 * L'interception se fait a ce niveau parce que c'est le seul point de passage commun aux
 * trois familles de methodes d'un repository Spring Data : celles heritees de
 * {@code JpaRepository}, les methodes derivees du nom ({@code findByActiveTrue}) et les
 * {@code @Query}. Poser le filtre plus haut, dans un filtre servlet par exemple, ne
 * fonctionnerait pas : la session Hibernate n'est pas encore liee au thread a ce moment.
 *
 * L'activation est repetee a chaque appel plutot que faite une fois par requete, et c'est
 * deliberé : {@code enableFilter} est idempotent et bon marche, la ou une activation
 * unique dependrait de la duree de vie exacte de la session — fragile des qu'un
 * {@code @Transactional} en ouvre une nouvelle.
 */
@Aspect
@Component
public class TenantFilterActivator {

    private static final Logger log = LoggerFactory.getLogger(TenantFilterActivator.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.gescom.backend.repository..*(..))")
    public void applyTenantFilter() {
        Long companyId = TenantContext.getCompanyId();
        try {
            Session session = entityManager.unwrap(Session.class);
            if (companyId == null) {
                // Vue plateforme (SUPER_ADMIN) ou traitement hors requete : aucune restriction.
                // Le filtre est explicitement retire, car la session peut etre reutilisee.
                session.disableFilter(TenantContext.FILTER_NAME);
            } else {
                session.enableFilter(TenantContext.FILTER_NAME)
                       .setParameter(TenantContext.FILTER_PARAM, companyId);
            }
        } catch (IllegalStateException e) {
            // Aucune session disponible (appel hors contexte de persistance) : il n'y a
            // alors rien a filtrer. On trace sans interrompre l'appel.
            log.debug("Filtre de cloisonnement non applique, session absente : {}", e.getMessage());
        }
    }
}
