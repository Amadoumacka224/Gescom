package com.gescom.backend.repository;

import com.gescom.backend.entity.Company;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Acces aux entreprises clientes.
 *
 * {@link Company} n'est pas filtree par entreprise : c'est elle qui sert a filtrer. Ce
 * repository n'est donc jamais filtre, et il n'est utilise que dans l'espace plateforme,
 * ou c'est {@code @PreAuthorize} qui controle l'acces.
 *
 * Les comptages sont faits en base plutot qu'avec un {@code findAll} en memoire : le tableau
 * de bord du super admin porte sur toutes les entreprises, pas sur une page.
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Long>,
        JpaSpecificationExecutor<Company> {

    /**
     * Lit l'entreprise avec un verrou en ecriture, pour que la generation des numeros de
     * documents se fasse l'une apres l'autre.
     *
     * On ne modifie pas l'entreprise : on verrouille juste sa ligne pour que deux ventes en
     * meme temps dans la MEME entreprise ne lisent pas le meme « dernier numero ». La deuxieme
     * attend que la premiere ait fini.
     *
     * L'inconvenient : toutes les creations de documents d'une entreprise passent l'une apres
     * l'autre. Sur un back-office avec quelques postes, ca ne se voit pas, et ca evite une
     * table de compteurs en plus. Les entreprises entre elles ne se bloquent pas : chacune a
     * sa ligne.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Company c WHERE c.id = :id")
    Optional<Company> findByIdForUpdate(@Param("id") Long id);

    Optional<Company> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByEmailIgnoreCase(String email);

    List<Company> findByStatus(Company.CompanyStatus status);

    long countByStatus(Company.CompanyStatus status);

    long countByCreatedAtGreaterThanEqual(LocalDateTime start);

    /** Nombre d'entreprises par statut en une requete, pour les compteurs du tableau de bord. */
    @Query("SELECT c.status, COUNT(c) FROM Company c GROUP BY c.status")
    List<Object[]> countGroupedByStatus();

    /**
     * Essais qui arrivent a leur fin, pour l'ecran des alertes.
     *
     * Le statut est passe en parametre et pas ecrit en dur : JPQL n'accepte pas la notation
     * avec des points pour un enum imbrique, et en parametre on n'a pas a ecrire le nom complet.
     */
    @Query("""
           SELECT c FROM Company c
           WHERE c.status = :status
             AND c.trialEndsAt IS NOT NULL
             AND c.trialEndsAt <= :deadline
           ORDER BY c.trialEndsAt ASC
           """)
    List<Company> findTrialsEndingBefore(Company.CompanyStatus status, LocalDateTime deadline);

    /** Derniers comptes crees, pour le bloc « nouveaux clients » du tableau de bord. */
    Page<Company> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Override
    Page<Company> findAll(Specification<Company> spec, Pageable pageable);
}
