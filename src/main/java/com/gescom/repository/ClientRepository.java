package com.gescom.repository;

import com.gescom.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gescom.entity.Order;
import com.gescom.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    // ===== MÉTHODES POUR MANAGER =====

    /**
     * Compte les clients assignés à l'équipe
     */
    Long countByAssignedUserIn(List<User> users);

    /**
     * Compte les nouveaux clients de l'équipe sur une période
     */
    Long countByAssignedUserInAndCreatedAtAfter(List<User> users, LocalDateTime startDate);

    // ===== MÉTHODES POUR USER =====

    /**
     * Compte les clients assignés à un utilisateur
     */
    Long countByAssignedUser(User user);

    /**
     * Compte les clients actifs d'un utilisateur (ayant commandé récemment)
     */
    @Query("SELECT COUNT(DISTINCT c) FROM Client c JOIN Order o ON o.client = c " +
            "WHERE c.assignedUser = :user AND o.orderDate >= :startDate")
    Long countActiveClientsByUser(@Param("user") User user, @Param("startDate") LocalDateTime startDate);

    /**
     * Clients en attente de relance
     */
    @Query("SELECT c FROM Client c WHERE c.assignedUser = :user AND c.followUpDate <= CURRENT_TIMESTAMP AND c.status = 'ACTIVE'")
    List<Client> findPendingFollowUpsByUser(@Param("user") User user);

    // ===== MÉTHODES POUR ADMIN =====

    /**
     * Nouveaux clients par jour
     */
    @Query("SELECT new map(" +
            "DATE(c.createdAt) as date, " +
            "COUNT(c) as count) " +
            "FROM Client c " +
            "WHERE c.createdAt >= :startDate " +
            "GROUP BY DATE(c.createdAt) " +
            "ORDER BY DATE(c.createdAt)")
    List<Map<String, Object>> getNewClientsByDay(@Param("startDate") LocalDateTime startDate);

    // ===== MÉTHODES COMMUNES =====

    /**
     * Trouve un client par email
     */
    Optional<Client> findByEmail(String email);

    /**
     * Trouve les clients par statut
     */
    List<Client> findByStatus(Client.ClientStatus status);

    /**
     * Trouve les clients par type
     */
    List<Client> findByClientType(Client.ClientType clientType);

    /**
     * Trouve les clients assignés à un utilisateur
     */
    List<Client> findByAssignedUser(User assignedUser);

    /**
     * Trouve les clients par ville
     */
    List<Client> findByCity(String city);

    /**
     * Clients les plus récents
     */
    List<Client> findTop10ByOrderByCreatedAtDesc();

    /**
     * Recherche de clients par nom ou email
     */
    @Query("SELECT c FROM Client c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Client> searchByNameOrEmail(@Param("search") String search);

    /**
     * Clients par commercial avec statistiques
     */
    @Query("SELECT new map(" +
            "CONCAT(u.firstName, ' ', u.lastName) as commercial, " +
            "COUNT(c) as totalClients, " +
            "SUM(CASE WHEN c.status = 'ACTIVE' THEN 1 ELSE 0 END) as activeClients, " +
            "SUM(CASE WHEN c.createdAt >= :startDate THEN 1 ELSE 0 END) as newClients) " +
            "FROM Client c JOIN c.assignedUser u " +
            "GROUP BY u.id, u.firstName, u.lastName " +
            "ORDER BY COUNT(c) DESC")
    List<Map<String, Object>> getClientStatsByUser(@Param("startDate") LocalDateTime startDate);

    /**
     * Répartition des clients par type
     */
    @Query("SELECT new map(" +
            "c.clientType as type, " +
            "COUNT(c) as count) " +
            "FROM Client c " +
            "GROUP BY c.clientType")
    List<Map<String, Object>> getClientDistributionByType();

    /**
     * Répartition des clients par statut
     */
    @Query("SELECT new map(" +
            "c.status as status, " +
            "COUNT(c) as count) " +
            "FROM Client c " +
            "GROUP BY c.status")
    List<Map<String, Object>> getClientDistributionByStatus();

    /**
     * Top clients par CA
     */
    @Query("SELECT new map(" +
            "c.name as name, " +
            "c.email as email, " +
            "COALESCE(SUM(o.totalAmount), 0) as revenue, " +
            "COUNT(o) as orders) " +
            "FROM Client c " +
            "LEFT JOIN c.orders o " +
            "WITH o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY c.id, c.name, c.email " +
            "ORDER BY SUM(o.totalAmount) DESC")
    List<Map<String, Object>> getTopClientsByRevenue(@Param("startDate") LocalDateTime startDate);


    /**
     * Clients inactifs (sans commande depuis X jours)
     */
    @Query("SELECT c FROM Client c WHERE c.status = 'ACTIVE' AND " +
            "(SELECT MAX(o.orderDate) FROM Order o WHERE o.client = c) < :cutoffDate")
    List<Client> findInactiveClients(@Param("cutoffDate") LocalDateTime cutoffDate);
}
