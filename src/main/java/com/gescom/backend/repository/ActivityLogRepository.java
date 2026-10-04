package com.gescom.backend.repository;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Le journal d'activité est la table qui grossit sans limite, donc ses listes sont paginées.
 * L'auteur (`user`) est chargé dans la même requête avec {@link EntityGraph} : le mapper le
 * lit pour chaque ligne, sinon on aurait un N+1 par page.
 */
@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long>,
        JpaSpecificationExecutor<ActivityLog> {

    // Filtres combinés (utilisateur, action, entité, période, recherche) : faits avec une
    // Specification plutôt qu'en JPQL, car un filtre absent n'apparaît pas du tout dans la
    // requête. Pas de « :param IS NULL » sur un enum, que Postgres n'aime pas.
    //
    // `ownerCompany` est chargé avec `user` : le journal global du back-office affiche
    // l'entreprise sur chaque ligne, et l'export prend des milliers de lignes d'un coup. En
    // lazy, ça faisait une requête par ligne.
    @Override
    @EntityGraph(attributePaths = {"user", "ownerCompany"})
    Page<ActivityLog> findAll(Specification<ActivityLog> spec, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<ActivityLog> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<ActivityLog> findByActionType(ActivityLog.ActionType actionType, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<ActivityLog> findByEntity(String entity, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<ActivityLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("SELECT al FROM ActivityLog al WHERE al.user.role = 'CAISSIER'")
    Page<ActivityLog> findCaissierActivities(Pageable pageable);

    // --- Stats de l'écran Historique -----------------------------------------
    // Calculées en base : elles portent sur tout le journal, pas sur la page affichée.

    long countByCreatedAtGreaterThanEqual(LocalDateTime start);

    long countByActionType(ActivityLog.ActionType actionType);

    @Query("SELECT COUNT(DISTINCT al.user.id) FROM ActivityLog al WHERE al.createdAt >= :start")
    long countDistinctUsersSince(LocalDateTime start);

    @Query("SELECT DISTINCT al.actionType FROM ActivityLog al")
    List<ActivityLog.ActionType> findDistinctActionTypes();

    @Query("SELECT DISTINCT al.entity FROM ActivityLog al WHERE al.entity IS NOT NULL ORDER BY al.entity")
    List<String> findDistinctEntities();

    // --- Lectures non paginées -----------------------------------------------

    List<ActivityLog> findByUser(User user);

    List<ActivityLog> findByUserId(Long userId);

    List<ActivityLog> findByActionType(ActivityLog.ActionType actionType);

    List<ActivityLog> findByEntity(String entity);

    List<ActivityLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<ActivityLog> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    List<ActivityLog> findByActionTypeAndCreatedAtBetween(ActivityLog.ActionType actionType, LocalDateTime start, LocalDateTime end);

    @Query("SELECT al FROM ActivityLog al WHERE al.user.id = :userId ORDER BY al.createdAt DESC")
    List<ActivityLog> findRecentByUser(Long userId);

    @Query("SELECT al FROM ActivityLog al ORDER BY al.createdAt DESC")
    List<ActivityLog> findAllOrderByCreatedAtDesc();

    @Query("SELECT al FROM ActivityLog al WHERE al.user.role = 'CAISSIER' ORDER BY al.createdAt DESC")
    List<ActivityLog> findCaissierActivities();
}
