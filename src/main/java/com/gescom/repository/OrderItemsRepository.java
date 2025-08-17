package com.gescom.repository;

import com.gescom.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemsRepository extends JpaRepository<OrderItem, Long> {
    
    /**
     * Trouve tous les items d'une commande
     */
    List<OrderItem> findByOrderId(Long orderId);
    
    /**
     * Trouve tous les items d'une commande avec product chargé
     */
    @Query("SELECT oi FROM OrderItem oi " +
           "LEFT JOIN FETCH oi.product p " +
           "WHERE oi.order.id = :orderId")
    List<OrderItem> findByOrderIdWithProduct(@Param("orderId") Long orderId);
    
    /**
     * Compte les items d'une commande
     */
    @Query("SELECT COUNT(oi) FROM OrderItem oi WHERE oi.order.id = :orderId")
    Long countByOrderId(@Param("orderId") Long orderId);
    
    /**
     * Supprime tous les items d'une commande
     */
    void deleteByOrderId(Long orderId);
    
    /**
     * Vérification directe en SQL pour debug
     */
    @Query(value = "SELECT * FROM order_items WHERE order_id = :orderId", nativeQuery = true)
    List<OrderItem> findByOrderIdNative(@Param("orderId") Long orderId);
}
