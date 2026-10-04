/**
 * Entites JPA du projet.
 *
 * <p>Le filtre {@code tenantFilter} declare ici est la piece principale de la separation
 * entre entreprises : chaque entite qui a {@code @Filter(name = "tenantFilter")} a toutes
 * ses requetes completees par {@code AND company_id = :tenantCompanyId}. Il est active par
 * {@code TenantFilterActivator} avant chaque appel aux repositories, et laisse inactif
 * exprès pour le SUPER_ADMIN, qui voit tout.
 *
 * <p>Le filtre marche pour les requetes (JPQL, methodes derivees, criteria) mais <em>pas</em>
 * pour les chargements par id, que Hibernate peut servir depuis son cache sans passer par
 * le SQL filtre. Ce trou est bouche a part par {@code TenantAwareRepositoryImpl} ; il faut
 * les deux, l'un sans l'autre ne suffit pas.
 */
@FilterDef(
        name = TenantContext.FILTER_NAME,
        parameters = @ParamDef(name = TenantContext.FILTER_PARAM, type = Long.class)
)
package com.gescom.backend.entity;

import com.gescom.backend.tenancy.TenantContext;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
