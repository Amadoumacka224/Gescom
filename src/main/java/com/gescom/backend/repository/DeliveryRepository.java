package com.gescom.backend.repository;

import com.gescom.backend.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByDeliveryNumber(String deliveryNumber);
    Optional<Delivery> findByOrderId(Long orderId);
    List<Delivery> findByStatus(Delivery.DeliveryStatus status);
    List<Delivery> findByScheduledDateBetween(LocalDateTime start, LocalDateTime end);
}
