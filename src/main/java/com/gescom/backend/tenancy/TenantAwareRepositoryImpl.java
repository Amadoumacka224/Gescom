package com.gescom.backend.tenancy;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementation de base de tous les repositories, qui ferme la faille des acces par
 * identifiant.
 *
 * Le filtre Hibernate ({@code tenantFilter}) reecrit les requetes, mais {@code findById}
 * passe par {@code EntityManager.find} : Hibernate peut alors servir l'entite depuis le
 * cache de premier niveau, sans emettre de SQL et donc sans appliquer le moindre filtre.
 * Sans ce garde-fou, {@code GET /api/products/42} renverrait l'article 42 d'une autre
 * entreprise des lors qu'il aurait ete charge plus tot dans la meme transaction.
 *
 * La reponse retenue est de faire disparaitre l'entite plutot que de repondre 403 :
 * un identifiant appartenant a autrui doit etre indiscernable d'un identifiant inexistant,
 * faute de quoi l'API confirmerait l'existence de donnees d'un autre client.
 *
 * Enregistree globalement via {@code @EnableJpaRepositories(repositoryBaseClass = ...)}
 * dans {@code PersistenceConfig} : aucun repository n'a a s'en soucier.
 */
public class TenantAwareRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {

    private final EntityManager entityManager;

    public TenantAwareRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    @Override
    public Optional<T> findById(ID id) {
        return super.findById(id).filter(TenantGuard::isVisible);
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    /**
     * Volontairement resolu en chargement reel plutot qu'en proxy paresseux : verifier
     * l'appartenance impose de lire l'entite. Le surcout est negligeable — cette methode
     * sert a rattacher une cle etrangere, jamais a parcourir un volume — et le prix a payer
     * pour qu'un identifiant d'une autre entreprise ne puisse pas etre lie ici.
     */
    @Override
    public T getReferenceById(ID id) {
        return findById(id).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Entite introuvable ou hors du perimetre de l'entreprise courante : " + id));
    }

    @Override
    @Transactional
    public void delete(T entity) {
        if (!TenantGuard.isVisible(entity)) {
            // Suppression silencieusement ignoree : meme raisonnement que pour findById,
            // echouer bruyamment revelerait l'existence de la ligne visee.
            return;
        }
        super.delete(entity);
    }

    @Override
    @Transactional
    public void deleteById(ID id) {
        findById(id).ifPresent(super::delete);
    }

    /**
     * Interdit d'ecrire dans le perimetre d'une autre entreprise.
     *
     * Le cas vise n'est pas la creation — {@link TenantEntityListener} affecte alors
     * l'entreprise courante — mais la mise a jour d'une entite construite a partir d'un
     * identifiant etranger, qui contournerait le cloisonnement en ecriture.
     */
    @Override
    @Transactional
    public <S extends T> S save(S entity) {
        assertWritable(entity);
        return super.save(entity);
    }

    @Override
    @Transactional
    public <S extends T> S saveAndFlush(S entity) {
        assertWritable(entity);
        return super.saveAndFlush(entity);
    }

    private void assertWritable(Object entity) {
        if (!(entity instanceof TenantOwned owned) || !TenantContext.isScoped()) {
            return;
        }
        // ownerCompany null a la creation : le listener la renseignera au flush.
        if (owned.getOwnerCompany() != null && !TenantGuard.isVisible(owned)) {
            throw new TenantViolationException();
        }
    }

    /** Expose l'EntityManager aux sous-classes eventuelles de repositories personnalises. */
    protected EntityManager entityManager() {
        return entityManager;
    }
}
