package com.gescom.backend.repository;

import com.gescom.backend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Toutes les commandes avec client, créateur, lignes et produits chargés en une seule
     * requête (évite le N+1 lors du mapping des listes et des agrégats du tableau de bord).
     * DISTINCT car le LEFT JOIN FETCH sur la collection de lignes duplique les commandes.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "ORDER BY o.createdAt DESC")
    List<Order> findAllWithDetails();

    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByClientId(Long clientId);
    List<Order> findByCreatedById(Long userId);
    List<Order> findByStatus(Order.OrderStatus status);
    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Commandes d'un caissier sur une journée [start, end), lignes et client chargés
     * en une seule requête (évite le N+1 lors de l'agrégation du tableau de bord caisse).
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items " +
           "LEFT JOIN FETCH o.client " +
           "WHERE o.createdBy.id = :userId " +
           "AND o.createdAt >= :start AND o.createdAt < :end " +
           "ORDER BY o.createdAt DESC")
    List<Order> findDayOrdersForCashier(@Param("userId") Long userId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /**
     * Commandes de tous les caissiers sur une journée [start, end), lignes, client et créateur
     * chargés en une seule requête. Pendant « supervision des caisses » de
     * {@link #findDayOrdersForCashier} : évite de rapatrier toute la table pour n'en agréger
     * qu'un jour.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy " +
           "WHERE o.createdAt >= :start AND o.createdAt < :end " +
           "ORDER BY o.createdAt DESC")
    List<Order> findDayOrders(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);
}
