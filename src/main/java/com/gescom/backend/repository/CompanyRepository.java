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
 * Acces au parc d'entreprises clientes.
 *
 * {@link Company} n'est pas cloisonnee : elle est le cloisonnement. Ce repository n'est donc
 * jamais filtre, et son usage est reserve a l'espace plateforme, ou {@code @PreAuthorize}
 * tient lieu de controle d'acces.
 *
 * Les comptages sont agreges en base plutot que derives d'un {@code findAll} en memoire :
 * le tableau de bord proprietaire decrit la totalite du parc, pas une page.
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Long>,
        JpaSpecificationExecutor<Company> {

    /**
     * Lecture de l'entreprise avec verrou d'ecriture, utilisee comme point de serialisation de
     * l'attribution des numeros de documents.
     *
     * Il ne s'agit pas de modifier l'entreprise : on emprunte sa ligne pour que deux ventes
     * simultanees de la MEME entreprise ne puissent pas lire le meme « dernier numero ». La
     * seconde attend que la premiere ait valide.
     *
     * Le prix a payer est assume : toutes les creations de documents d'une meme entreprise se
     * suivent au lieu de se croiser. Sur un back-office ou quelques postes saisissent en
     * parallele, cela ne se voit pas — et cela evite d'ajouter une table de compteurs.
     * Les entreprises, elles, restent independantes : chacune a sa propre ligne.
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

    /** Repartition du parc par statut, en une passe — alimente les compteurs du tableau de bord. */
    @Query("SELECT c.status, COUNT(c) FROM Company c GROUP BY c.status")
    List<Object[]> countGroupedByStatus();

    /**
     * Essais arrivant a echeance, pour l'ecran d'alertes.
     *
     * Le statut est passe en parametre plutot qu'ecrit en dur : JPQL n'admet pas la notation
     * pointee d'un enum imbrique, et le lier evite d'avoir a le qualifier completement.
     */
    @Query("""
           SELECT c FROM Company c
           WHERE c.status = :status
             AND c.trialEndsAt IS NOT NULL
             AND c.trialEndsAt <= :deadline
           ORDER BY c.trialEndsAt ASC
           """)
    List<Company> findTrialsEndingBefore(Company.CompanyStatus status, LocalDateTime deadline);

    /** Derniers comptes crees — flux « nouveaux clients » du tableau de bord. */
    Page<Company> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Override
    Page<Company> findAll(Specification<Company> spec, Pageable pageable);
}
