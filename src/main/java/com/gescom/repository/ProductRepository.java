package com.gescom.repository;

import com.gescom.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gescom.entity.Order;
import com.gescom.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.awt.print.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
public interface ProductRepository  extends JpaRepository<Product, Long> {


    /**
     * Produits avec stock faible (utilisé dans le dashboard admin)
     */
    List<Product> findByStockLessThan(Integer threshold);

    /**
     * Produits en rupture de stock
     */
    @Query("SELECT p FROM Product p WHERE p.stock <= 0 AND p.isActive = true")
    List<Product> findOutOfStockProducts();

    /**
     * Produits avec stock critique (en dessous du stock minimum)
     */
    @Query("SELECT p FROM Product p WHERE p.stock <= p.minStock AND p.isActive = true")
    List<Product> findLowStockProducts();

    // ===== MÉTHODES DE RECHERCHE =====

    /**
     * Trouve un produit par référence
     */
    Optional<Product> findByReference(String reference);

    /**
     * Trouve un produit par code-barres
     */
    Optional<Product> findByBarCode(String barCode);

    /**
     * Trouve les produits par catégorie
     */
    List<Product> findByCategory(String category);

    /**
     * Trouve les produits par marque
     */
    List<Product> findByBrand(String brand);

    /**
     * Trouve les produits actifs
     */
    List<Product> findByIsActiveTrue();

    /**
     * Trouve les produits mis en avant
     */
    List<Product> findByIsFeaturedTrue();

    /**
     * Recherche de produits par nom ou référence
     */
    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.reference) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Product> searchByNameOrReference(@Param("search") String search);

    // ===== STATISTIQUES PRODUITS =====

    /**
     * Nombre de produits par catégorie
     */
    @Query("SELECT new map(" +
            "p.category as category, " +
            "COUNT(p) as count) " +
            "FROM Product p WHERE p.isActive = true " +
            "GROUP BY p.category " +
            "ORDER BY COUNT(p) DESC")
    List<Map<String, Object>> getProductCountByCategory();

    /**
     * Valeur du stock par catégorie
     */
    @Query("SELECT new map(" +
            "p.category as category, " +
            "SUM(p.stock * p.unitPrice) as value, " +
            "SUM(p.stock) as quantity) " +
            "FROM Product p WHERE p.isActive = true " +
            "GROUP BY p.category " +
            "ORDER BY SUM(p.stock * p.unitPrice) DESC")
    List<Map<String, Object>> getStockValueByCategory();

    /**
     * Top produits les plus vendus (basé sur les commandes)
     */
    @Query("SELECT new map(" +
            "p.name as name, " +
            "p.reference as reference, " +
            "SUM(oi.quantity) as totalSold, " +
            "COALESCE(SUM(oi.totalPrice), 0) as revenue) " +
            "FROM Product p JOIN OrderItem oi ON oi.product = p JOIN Order o ON oi.order = o " +
            "WHERE o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY p.id, p.name, p.reference " +
            "ORDER BY SUM(oi.quantity) DESC")
    List<Map<String, Object>> getTopSellingProducts(@Param("startDate") LocalDateTime startDate);

    /**
     * Produits les plus rentables
     */
    @Query("SELECT new map(" +
            "p.name as name, " +
            "p.reference as reference, " +
            "p.unitPrice - COALESCE(p.purchasePrice, 0) as margin, " +
            "CASE WHEN p.purchasePrice > 0 THEN ((p.unitPrice - p.purchasePrice) * 100 / p.purchasePrice) ELSE 0 END as marginPercent) " +
            "FROM Product p WHERE p.isActive = true AND p.purchasePrice IS NOT NULL " +
            "ORDER BY (p.unitPrice - p.purchasePrice) DESC")
    List<Map<String, Object>> getMostProfitableProducts();

    /**
     * Rotation des stocks (produits qui bougent le plus)
     */
    @Query("SELECT new map(" +
            "p.name as name, " +
            "p.reference as reference, " +
            "p.stock as currentStock, " +
            "SUM(oi.quantity) as sold, " +
            "CASE WHEN p.stock > 0 THEN (SUM(oi.quantity) / p.stock) ELSE 0 END as turnoverRatio) " +
            "FROM Product p LEFT JOIN OrderItem oi ON oi.product = p " +
            "LEFT JOIN Order o ON oi.order = o " +
            "WHERE p.isActive = true AND (o.orderDate >= :startDate OR o IS NULL) AND (o.status != 'CANCELLED' OR o IS NULL) " +
            "GROUP BY p.id, p.name, p.reference, p.stock " +
            "ORDER BY (SUM(oi.quantity) / NULLIF(p.stock, 0)) DESC")
    List<Map<String, Object>> getProductTurnover(@Param("startDate") LocalDateTime startDate);

    /**
     * Évolution des ventes par produit
     */
    @Query("SELECT new map(" +
            "p.name as productName, " +
            "DATE(o.orderDate) as date, " +
            "SUM(oi.quantity) as quantity, " +
            "SUM(oi.totalPrice) as revenue) " +
            "FROM Product p JOIN OrderItem oi ON oi.product = p JOIN Order o ON oi.order = o " +
            "WHERE o.orderDate >= :startDate AND o.status != 'CANCELLED' " +
            "GROUP BY p.id, p.name, DATE(o.orderDate) " +
            "ORDER BY p.name, DATE(o.orderDate)")
    List<Map<String, Object>> getProductSalesTrend(@Param("startDate") LocalDateTime startDate);

    // ===== GESTION STOCK =====

    /**
     * Produits nécessitant un réapprovisionnement
     */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.stock <= p.minStock AND p.minStock > 0")
    List<Product> findProductsNeedingRestock();

    /**
     * Valeur totale du stock
     */
    @Query("SELECT COALESCE(SUM(p.stock * p.unitPrice), 0) FROM Product p WHERE p.isActive = true")
    Long getTotalStockValue();

    /**
     * Nombre total d'articles en stock
     */
    @Query("SELECT COALESCE(SUM(p.stock), 0) FROM Product p WHERE p.isActive = true")
    Long getTotalStockQuantity();
}
