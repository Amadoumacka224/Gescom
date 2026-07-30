package com.gescom.backend.repository;

import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByCode(String code);

    /**
     * Produits portant ce code-barres. Renvoie une liste car la colonne barcode n'est pas unique
     * en base : le service privilégiera un produit actif s'il en existe plusieurs.
     */
    List<Product> findByBarcode(String barcode);
    List<Product> findByActiveTrue();
    List<Product> findByCategory(Category category);
    List<Product> findByCategoryId(Long categoryId);

    @Query("SELECT p FROM Product p WHERE p.stockQuantity < p.minStockAlert")
    List<Product> findByStockQuantityLessThanMinStockAlert();

    List<Product> findByStockQuantityLessThanEqual(Integer quantity);

    Boolean existsByCode(String code);

    /**
     * Lecture avec verrou pessimiste — à utiliser dans les opérations qui modifient
     * le stock pour empêcher deux transactions simultanées de lire/écrire en parallèle.
     * Doit être appelé dans un contexte transactionnel.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(Long id);
}
