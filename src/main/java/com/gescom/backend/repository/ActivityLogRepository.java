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
 * Le journal d'activité est le registre de l'application qui croît sans borne : ses lectures
 * de liste sont donc paginées. L'auteur (`user`) est chargé dans la même requête via
 * {@link EntityGraph} — le mapper le déréférence à chaque ligne, ce qui déclencherait sinon
 * un N+1 par page.
 */
@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long>,
        JpaSpecificationExecutor<ActivityLog> {

    // Filtrage combiné (utilisateur, action, entité, période, recherche) : construit en
    // Specification plutôt qu'en JPQL, car un critère absent ne participe alors pas du tout
    // à la requête — pas de « :param IS NULL » à faire avaler à Postgres sur un enum.
    //
    // `ownerCompany` accompagne `user` dans le graphe : le journal consolidé du back-office
    // affiche l'entreprise d'origine à chaque ligne, et son export en tire des milliers de
    // lignes d'un coup — chargée paresseusement, elle valait une requête par ligne.
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

    // --- Indicateurs de l'écran Historique -----------------------------------
    // Agrégés en base : ils décrivent la totalité du journal, jamais la seule page affichée.

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
