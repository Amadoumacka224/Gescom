/**
 * Entites JPA du domaine.
 *
 * <p>Le filtre {@code tenantFilter} declare ici est le mecanisme central du cloisonnement
 * multi-entreprises : chaque entite portant {@code @Filter(name = "tenantFilter")} voit
 * toutes ses requetes completees d'un {@code AND company_id = :tenantCompanyId}. Il est
 * active par {@code TenantFilterActivator} avant chaque appel au package repository, et
 * laisse volontairement inactif pour le SUPER_ADMIN, dont la vue est globale.
 *
 * <p>Le filtre couvre les requetes (JPQL, methodes derivees, criteria) mais <em>pas</em>
 * les chargements directs par identifiant, que Hibernate sert depuis le cache de premier
 * niveau sans passer par le SQL filtre. Cette faille est fermee separement par
 * {@code TenantAwareRepositoryImpl} ; les deux dispositifs sont complementaires et aucun
 * ne se suffit a lui-meme.
 */
@FilterDef(
        name = TenantContext.FILTER_NAME,
        parameters = @ParamDef(name = TenantContext.FILTER_PARAM, type = Long.class)
)
package com.gescom.backend.entity;

import com.gescom.backend.tenancy.TenantContext;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
