package com.gescom.backend.repository;

import com.gescom.backend.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     *
     * {@code createdById} porte le cloisonnement caissier, rapporté au créateur de la VENTE :
     * une livraison suit la commande dont elle découle, pas l'opérateur qui l'a planifiée.
     * Nul, il désactive le filtre (vue ADMIN).
     */
    @Query("SELECT DISTINCT d FROM Delivery d " +
           "LEFT JOIN FETCH d.order o " +
           "LEFT JOIN FETCH o.client " +
           "LEFT JOIN FETCH o.createdBy u " +
           "LEFT JOIN FETCH o.items it " +
           "LEFT JOIN FETCH it.product " +
           "WHERE (:createdById IS NULL OR u.id = :createdById) " +
           "ORDER BY d.id DESC")
    List<Delivery> findAllWithDetails(@Param("createdById") Long createdById);

    Optional<Delivery> findByDeliveryNumber(String deliveryNumber);
    Optional<Delivery> findByOrderId(Long orderId);

    // Mêmes listes filtrées, même `createdById` optionnel : aucune liste de livraisons ne doit
    // offrir de contournement au cloisonnement caissier.
    @Query("SELECT d FROM Delivery d WHERE d.status = :status "
            + "AND (:createdById IS NULL OR d.order.createdBy.id = :createdById)")
    List<Delivery> findByStatus(@Param("status") Delivery.DeliveryStatus status,
                                @Param("createdById") Long createdById);

    @Query("SELECT d FROM Delivery d WHERE d.scheduledDate >= :start AND d.scheduledDate <= :end "
            + "AND (:createdById IS NULL OR d.order.createdBy.id = :createdById)")
    List<Delivery> findByScheduledDateBetween(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end,
                                              @Param("createdById") Long createdById);
}
