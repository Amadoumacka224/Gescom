package com.gescom.backend.repository;

import com.gescom.backend.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Catalogue commercial. Table de reference partagee par tout le parc : elle n'est pas
 * cloisonnee et n'est modifiable que depuis l'espace plateforme.
 */
@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByCode(String code);

    boolean existsByCode(String code);

    List<Plan> findAllByOrderBySortOrderAsc();

    /** Formules encore proposables a la souscription. */
    List<Plan> findByActiveTrueOrderBySortOrderAsc();
}
