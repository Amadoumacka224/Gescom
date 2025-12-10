package com.gescom.backend.repository;

import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByCode(String code);
    List<Product> findByActiveTrue();
    List<Product> findByCategory(Category category);
    List<Product> findByCategoryId(Long categoryId);

    @Query("SELECT p FROM Product p WHERE p.stockQuantity < p.minStockAlert")
    List<Product> findByStockQuantityLessThanMinStockAlert();

    Boolean existsByCode(String code);
}
