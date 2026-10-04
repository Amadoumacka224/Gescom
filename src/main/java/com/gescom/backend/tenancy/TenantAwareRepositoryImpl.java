package com.gescom.backend.tenancy;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementation de base de tous les repositories, qui bouche le trou des acces par id.
 *
 * Le filtre Hibernate ({@code tenantFilter}) modifie les requetes, mais {@code findById}
 * passe par {@code EntityManager.find} : Hibernate peut alors renvoyer l'entite depuis son
 * cache, sans faire de SQL et donc sans appliquer de filtre. Sans cette classe,
 * {@code GET /api/products/42} pourrait renvoyer l'article 42 d'une autre entreprise s'il
 * avait deja ete charge avant dans la meme transaction.
 *
 * On fait comme si l'entite n'existait pas plutot que de repondre 403 : un id d'une autre
 * entreprise doit etre impossible a distinguer d'un id qui n'existe pas, sinon l'API
 * confirmerait que les donnees d'un autre client existent.
 *
 * Activee pour tous les repositories avec {@code @EnableJpaRepositories(repositoryBaseClass = ...)}
 * dans {@code PersistenceConfig} : aucun repository n'a besoin de s'en occuper.
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
     * On charge vraiment l'entite au lieu d'un proxy lazy, exprès : pour verifier a quelle
     * entreprise elle appartient, il faut la lire. Ca ne coute presque rien (cette methode
     * sert a remplir une cle etrangere, pas a parcourir beaucoup de lignes), et c'est ce qui
     * empeche de lier ici un id d'une autre entreprise.
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
            // Suppression ignoree sans rien dire : meme raison que pour findById, une erreur
            // reveleraient que la ligne existe.
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
     * Interdit de modifier des donnees d'une autre entreprise.
     *
     * Ca ne concerne pas la creation ({@link TenantEntityListener} met alors l'entreprise
     * courante), mais la modification d'une entite construite a partir d'un id d'une autre
     * entreprise, qui permettrait de contourner le filtrage en ecriture.
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
        // ownerCompany null a la creation : le listener la remplira au flush.
        if (owned.getOwnerCompany() != null && !TenantGuard.isVisible(owned)) {
            throw new TenantViolationException();
        }
    }

    /** Donne acces a l'EntityManager pour d'eventuels repositories personnalises. */
    protected EntityManager entityManager() {
        return entityManager;
    }
}
