package com.gescom.backend.tenancy;

import com.gescom.backend.entity.Company;

/**
 * Marque une entite appartenant a une entreprise cliente.
 *
 * Volontairement une interface et non une {@code @MappedSuperclass} : les entites du
 * projet portent toutes {@code @Data}, et faire heriter Lombok d'une classe de base
 * imposerait de reprendre equals/hashCode sur chacune. Ici, le getter et le setter
 * generes par {@code @Data} satisfont le contrat sans une ligne de code supplementaire —
 * l'entite n'a qu'a declarer son champ {@code company} et {@code implements TenantOwned}.
 *
 * Ce marqueur est ce sur quoi s'appuient {@link TenantEntityListener} pour affecter
 * l'entreprise a la creation et {@link TenantAwareRepositoryImpl} pour verifier
 * l'appartenance sur les chargements par identifiant.
 *
 * Le champ s'appelle {@code ownerCompany} et non {@code company} pour une raison concrete :
 * {@code Client} porte deja un champ {@code company}, qui designe la raison sociale du
 * client du magasin. Deux notions distinctes, un seul nom disponible — et le prefixe
 * « owner » dit justement ce qui les separe : ici, le proprietaire de la ligne.
 */
public interface TenantOwned {

    Company getOwnerCompany();

    void setOwnerCompany(Company ownerCompany);
}
