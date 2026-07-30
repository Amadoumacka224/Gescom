package com.gescom.backend.repository;

import com.gescom.backend.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    /**
     * Toutes les livraisons avec la commande associée (client, créateur, lignes, produits) chargée
     * en une seule requête, pour éviter le N+1 au mapping — chaque DeliveryResponse embarque un
     * OrderResponse complet. DISTINCT à cause du JOIN FETCH sur la collection de lignes.
     */
    @Query("SELECT DISTINCT d FROM Delivery d " +
           "LEFT JOIN FETCH d.order o " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy " +
           "LEFT JOIN FETCH o.items it " +
           "LEFT JOIN FETCH it.product " +
           "ORDER BY d.id DESC")
    List<Delivery> findAllWithDetails();

    Optional<Delivery> findByDeliveryNumber(String deliveryNumber);
    Optional<Delivery> findByOrderId(Long orderId);
    List<Delivery> findByStatus(Delivery.DeliveryStatus status);
    List<Delivery> findByScheduledDateBetween(LocalDateTime start, LocalDateTime end);
}
