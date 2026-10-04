package com.gescom.backend.repository;

import com.gescom.backend.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Formules d'abonnement. Table commune a toutes les entreprises : pas filtree par
 * entreprise, et modifiable seulement depuis l'espace plateforme.
 */
@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByCode(String code);

    boolean existsByCode(String code);

    List<Plan> findAllByOrderBySortOrderAsc();

    /** Formules auxquelles on peut encore souscrire. */
    List<Plan> findByActiveTrueOrderBySortOrderAsc();
}
