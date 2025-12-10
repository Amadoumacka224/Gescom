package com.gescom.backend.repository;

import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByProduct(Product product);

    List<StockMovement> findByProductId(Long productId);

    List<StockMovement> findByType(StockMovement.MovementType type);

    List<StockMovement> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<StockMovement> findByProductIdAndCreatedAtBetween(Long productId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT sm FROM StockMovement sm WHERE sm.product.id = :productId ORDER BY sm.createdAt DESC")
    List<StockMovement> findRecentMovementsByProduct(Long productId);

    @Query("SELECT sm FROM StockMovement sm ORDER BY sm.createdAt DESC")
    List<StockMovement> findAllOrderByCreatedAtDesc();
}
