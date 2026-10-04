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
 * Active le filtre Hibernate par entreprise avant chaque appel aux repositories.
 *
 * On le fait a ce niveau parce que c'est le seul endroit par ou passent les trois types de
 * methodes d'un repository Spring Data : celles de {@code JpaRepository}, les methodes
 * derivees du nom ({@code findByActiveTrue}) et les {@code @Query}. Plus haut, par exemple
 * dans un filtre servlet, ca ne marcherait pas : la session Hibernate n'est pas encore liee
 * au thread a ce moment-la.
 *
 * On l'active a chaque appel plutot qu'une fois par requete, exprès : {@code enableFilter}
 * ne coute rien et peut etre appele plusieurs fois, alors qu'une seule activation
 * dependrait de la duree de vie de la session, ce qui casse des qu'un
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
                // Vue plateforme (SUPER_ADMIN) ou traitement hors requete : pas de restriction.
                // On desactive le filtre explicitement, car la session peut etre reutilisee.
                session.disableFilter(TenantContext.FILTER_NAME);
            } else {
                session.enableFilter(TenantContext.FILTER_NAME)
                       .setParameter(TenantContext.FILTER_PARAM, companyId);
            }
        } catch (IllegalStateException e) {
            // Pas de session (appel en dehors d'un contexte de persistance) : rien a filtrer.
            // On logge sans bloquer l'appel.
            log.debug("Filtre de cloisonnement non applique, session absente : {}", e.getMessage());
        }
    }
}
