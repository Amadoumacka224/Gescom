package com.gescom.repository;


import com.gescom.entity.Order;
import com.gescom.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public interface OrderRepository extends JpaRepository<Order, Long> {

    // ===== MÉTHODES POUR ADMIN =====

    /**
     * Calcule le CA total depuis une date
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderDate >= :startDate AND o.status != 'CANCELLED'")
    BigDecimal getTotalRevenueFromDate(@Param("startDate") LocalDateTime startDate);

    /**
     * Compte les commandes depuis une date
     */
    Long countByOrderDateAfter(LocalDateTime startDate);



    /**
     * Compte les commandes par statut depuis une date
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.orderDate >= :startDate")
    Long countByStatusAndOrderDateAfter(@Param("status") Order.OrderStatus status, @Param("startDate") LocalDateTime startDate);



    /**
     * Top vendeurs par CA
     */
    @Query("SELECT new map(" +
            "CONCAT(u.firstName, ' ', u.lastName) as name, " +
            "COALESCE(SUM(o.totalAmount), 0) as revenue, " +
            "COUNT(o) as orders) " +
            "FROM Order o " +
            "JOIN o.user u " +
            "WHERE o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY u.id, u.firstName, u.lastName " +
            "ORDER BY SUM(o.totalAmount) DESC")
    List<Map<String, Object>> getTopSellersByRevenue(@Param("startDate") LocalDateTime startDate, Pageable pageable);


    /**
     * CA par jour
     */
    @Query("SELECT new map(" +
            "FUNCTION('DATE', o.orderDate) as date, " +
            "COALESCE(SUM(o.totalAmount), 0) as revenue) " +
            "FROM Order o " +
            "WHERE o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY FUNCTION('DATE', o.orderDate) " +
            "ORDER BY FUNCTION('DATE', o.orderDate)")
    List<Map<String, Object>> getRevenueByDay(@Param("startDate") LocalDateTime startDate);


    /**
     * Nombre de commandes par statut
     */
    @Query("SELECT new map(" +
            "o.status as status, " +
            "COUNT(o) as count) " +
            "FROM Order o " +
            "WHERE o.orderDate >= :startDate " +
            "GROUP BY o.status")
    List<Map<String, Object>> getOrderCountByStatus(@Param("startDate") LocalDateTime startDate);

    /**
     * Top produits par quantité
     */
    @Query(value = "SELECT new map(" +
            "p.name as name, " +
            "SUM(oi.quantity) as quantity, " +
            "COALESCE(SUM(oi.totalPrice), 0) as revenue) " +
            "FROM OrderItem oi JOIN oi.product p JOIN oi.order o " +
            "WHERE o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY p.id, p.name " +
            "ORDER BY SUM(oi.quantity) DESC")
    Page<Map<String, Object>> getTopProductsByQuantity(@Param("startDate") LocalDateTime startDate, Pageable pageable);
    /**
     * Nombre de commandes par jour
     */
    @Query("SELECT new map(" +
            "DATE(o.orderDate) as date, " +
            "COUNT(o) as count) " +
            "FROM Order o " +
            "WHERE o.orderDate >= :startDate " +
            "GROUP BY DATE(o.orderDate) " +
            "ORDER BY DATE(o.orderDate)")
    List<Map<String, Object>> getOrderCountByDay(@Param("startDate") LocalDateTime startDate);

    /**
     * Top produits par CA
     */
    @Query("SELECT new map(" +
            "p.name as name, " +
            "SUM(oi.quantity) as quantity, " +
            "COALESCE(SUM(oi.totalPrice), 0) as revenue) " +
            "FROM OrderItem oi JOIN oi.product p JOIN oi.order o " +
            "WHERE o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY p.id, p.name " +
            "ORDER BY SUM(oi.totalPrice) DESC")
    List<Map<String, Object>> getTopProductsByRevenue(@Param("startDate") LocalDateTime startDate, Pageable pageable);

    // ===== MÉTHODES POUR MANAGER =====

    /**
     * CA de l'équipe
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.user IN :team AND o.orderDate >= :startDate AND o.status != 'CANCELLED'")
    BigDecimal getTeamRevenueFromDate(@Param("team") List<User> team, @Param("startDate") LocalDateTime startDate);

    /**
     * Nombre de commandes de l'équipe
     */
    Long countByUserInAndOrderDateAfter(List<User> users, LocalDateTime startDate);

    /**
     * Performance de l'équipe détaillée
     */
    @Query("SELECT new map(" +
            "CONCAT(u.firstName, ' ', u.lastName) as name, " +
            "COALESCE(SUM(o.totalAmount), 0) as revenue, " +
            "COUNT(o) as orders, " +
            "u.personalTarget as target, " +
            "CASE WHEN u.personalTarget > 0 THEN (COALESCE(SUM(o.totalAmount), 0) * 100 / u.personalTarget) ELSE 0 END as achievement, " +
            "(SELECT COUNT(DISTINCT c) FROM Client c WHERE c.assignedUser = u) as clients) " +
            "FROM Order o RIGHT JOIN o.user u " +
            "WHERE u IN :team AND (o.orderDate >= :startDate OR o IS NULL) AND (o.status != 'CANCELLED' OR o IS NULL) " +
            "GROUP BY u.id, u.firstName, u.lastName, u.personalTarget " +
            "ORDER BY SUM(o.totalAmount) DESC")
    List<Map<String, Object>> getTeamPerformance(@Param("team") List<User> team, @Param("startDate") LocalDateTime startDate);

    /**
     * CA par membre de l'équipe
     */
    @Query("SELECT new map(" +
            "CONCAT(u.firstName, ' ', u.lastName) as name, " +
            "COALESCE(SUM(o.totalAmount), 0) as revenue) " +
            "FROM Order o RIGHT JOIN o.user u " +
            "WHERE u IN :team AND (o.orderDate >= :startDate OR o IS NULL) AND (o.status != 'CANCELLED' OR o IS NULL) " +
            "GROUP BY u.id, u.firstName, u.lastName " +
            "ORDER BY SUM(o.totalAmount) DESC")
    List<Map<String, Object>> getRevenueByTeamMember(@Param("team") List<User> team, @Param("startDate") LocalDateTime startDate);

    /**
     * Tendances de l'équipe (évolution sur plusieurs périodes)
     */
    @Query("SELECT new map(" +
            "DATE(o.orderDate) as date, " +
            "CONCAT(u.firstName, ' ', u.lastName) as name, " +
            "COALESCE(SUM(o.totalAmount), 0) as revenue) " +
            "FROM Order o JOIN o.user u " +
            "WHERE u IN :team AND o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY DATE(o.orderDate), u.id, u.firstName, u.lastName " +
            "ORDER BY DATE(o.orderDate), u.firstName")
    List<Map<String, Object>> getTeamTrends(@Param("team") List<User> team, @Param("startDate") LocalDateTime startDate);


    // ===== MÉTHODES POUR USER =====

    /**
     * CA personnel depuis une date
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.user = :user AND o.orderDate >= :startDate AND o.status != 'CANCELLED'")
    BigDecimal getUserRevenueFromDate(@Param("user") User user, @Param("startDate") LocalDateTime startDate);

    /**
     * Nombre de commandes personnelles
     */
    Long countByUserAndOrderDateAfter(User user, LocalDateTime startDate);

    /**
     * Top produits personnels
     */
    @Query("SELECT new map(" +
            "p.name as name, " +
            "SUM(oi.quantity) as quantity, " +
            "COALESCE(SUM(oi.totalPrice), 0) as revenue) " +
            "FROM OrderItem oi JOIN oi.product p JOIN oi.order o " +
            "WHERE o.user = :user AND o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY p.id, p.name " +
            "ORDER BY SUM(oi.quantity) DESC")
    List<Map<String, Object>> getUserTopProducts(@Param("user") User user, @Param("startDate") LocalDateTime startDate, Pageable pageable);

    /**
     * Dernières commandes personnelles
     */
    List<Order> findTop10ByUserOrderByOrderDateDesc(User user);

    /**
     * CA personnel par jour
     */
    @Query("SELECT new map(" +
            "DATE(o.orderDate) as date, " +
            "COALESCE(SUM(o.totalAmount), 0) as revenue) " +
            "FROM Order o " +
            "WHERE o.user = :user AND o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY DATE(o.orderDate) " +
            "ORDER BY DATE(o.orderDate)")
    List<Map<String, Object>> getUserRevenueByDay(@Param("user") User user, @Param("startDate") LocalDateTime startDate);

    /**
     * Distribution des clients personnels (par type, statut, etc.)
     */
    @Query("SELECT new map(" +
            "c.clientType as category, " +
            "COUNT(DISTINCT c) as count) " +
            "FROM Order o JOIN o.client c " +
            "WHERE o.user = :user AND o.orderDate >= :startDate " +
            "GROUP BY c.clientType")
    List<Map<String, Object>> getUserClientDistribution(@Param("user") User user, @Param("startDate") LocalDateTime startDate);

    // ===== MÉTHODES COMMUNES =====

    /**
     * Trouve les commandes par statut
     */
    List<Order> findByStatus(Order.OrderStatus status);

    /**
     * Trouve les commandes d'un client
     */
    List<Order> findByClientIdOrderByOrderDateDesc(Long clientId);

    /**
     * CA total par mois (pour graphiques annuels)
     */
    @Query("SELECT new map(" +
            "YEAR(o.orderDate) as year, " +
            "MONTH(o.orderDate) as month, " +
            "COALESCE(SUM(o.totalAmount), 0) as revenue) " +
            "FROM Order o " +
            "WHERE o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY YEAR(o.orderDate), MONTH(o.orderDate) " +
            "ORDER BY YEAR(o.orderDate), MONTH(o.orderDate)")
    List<Map<String, Object>> getRevenueByMonth(@Param("startDate") LocalDateTime startDate);

    /**
     * Trouve une commande avec ses OrderItems et produits
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.orderItems oi " +
           "LEFT JOIN FETCH oi.product p " +
           "LEFT JOIN FETCH o.client c " +
           "LEFT JOIN FETCH o.user u " +
           "WHERE o.id = :id")
    Optional<Order> findByIdWithOrderItems(@Param("id") Long id);

    /**
     * Trouve une commande avec toutes ses relations chargées
     */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.user " +
           "LEFT JOIN FETCH o.invoice " +
           "WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    /**
     * Vérifie si un numéro de commande existe déjà
     */
    boolean existsByOrderNumber(String orderNumber);

    /**
     * Compte les commandes avec un pattern de numéro
     */
    long countByOrderNumberLike(String pattern);

    /**
     * Trouve le prochain numéro de commande disponible pour un mois donné
     */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(o.orderNumber, LENGTH(o.orderNumber) - 3) AS integer)), 0) + 1 " +
           "FROM Order o WHERE o.orderNumber LIKE :pattern")
    int findNextOrderNumberForMonth(@Param("pattern") String pattern);

}
