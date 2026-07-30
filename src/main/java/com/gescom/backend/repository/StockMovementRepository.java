package com.gescom.backend.repository;

import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.StockMovement;
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
 * Grand livre des mouvements de stock : registre append-only, donc à croissance non bornée.
 * Ses lectures de liste sont paginées, et le produit (avec sa catégorie) comme l'auteur sont
 * chargés dans la même requête via {@link EntityGraph} — la réponse les imbrique tous les
 * trois, ce qui provoquerait sinon un N+1 par ligne de page.
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long>,
        JpaSpecificationExecutor<StockMovement> {

    @Override
    @EntityGraph(attributePaths = {"product", "product.category", "user"})
    Page<StockMovement> findAll(Pageable pageable);

    // Filtrage combiné (type, produit, période, recherche libre) : en Specification, pour que
    // les critères absents ne participent pas à la requête.
    @Override
    @EntityGraph(attributePaths = {"product", "product.category", "user"})
    Page<StockMovement> findAll(Specification<StockMovement> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.category", "user"})
    Page<StockMovement> findByProductId(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.category", "user"})
    Page<StockMovement> findByType(StockMovement.MovementType type, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.category", "user"})
    Page<StockMovement> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // --- Lectures non paginées -----------------------------------------------

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
