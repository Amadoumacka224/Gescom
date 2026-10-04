package com.gescom.backend.tenancy;

import com.gescom.backend.entity.Company;

/**
 * Indique qu'une entite appartient a une entreprise cliente.
 *
 * C'est une interface et pas une {@code @MappedSuperclass}, exprès : toutes les entites du
 * projet ont {@code @Data}, et avec une classe parente il faudrait refaire equals/hashCode
 * sur chacune a cause de Lombok. Ici, le getter et le setter generes par {@code @Data}
 * suffisent : l'entite declare juste son champ {@code company} et {@code implements TenantOwned}.
 *
 * C'est grace a cette interface que {@link TenantEntityListener} met l'entreprise a la
 * creation et que {@link TenantAwareRepositoryImpl} verifie l'entreprise lors des
 * chargements par id.
 *
 * Le champ s'appelle {@code ownerCompany} et pas {@code company} pour une raison simple :
 * {@code Client} a deja un champ {@code company}, qui est la raison sociale du client du
 * magasin. Deux choses differentes pour un seul nom, et « owner » dit justement la
 * difference : ici, c'est l'entreprise a qui appartient la ligne.
 */
public interface TenantOwned {

    Company getOwnerCompany();

    void setOwnerCompany(Company ownerCompany);
}
