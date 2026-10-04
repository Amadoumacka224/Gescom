package com.gescom.backend.repository;

import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * {@code JpaSpecificationExecutor} sert pour la recherche paginée du catalogue : les filtres
 * de l'écran Produits sont tous facultatifs et combinables, impossible à faire avec une
 * méthode dérivée. Le filtrage par entreprise marche toujours, le filtre Hibernate
 * s'applique aussi aux requêtes par critères.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByCode(String code);

    /**
     * Produits qui ont ce code-barres. Renvoie une liste car barcode n'est pas unique en base :
     * le service prendra de préférence un produit actif s'il y en a plusieurs.
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
     * Lecture avec verrou pessimiste, à utiliser quand on modifie le stock pour empêcher deux
     * transactions de lire / écrire en même temps.
     * À appeler dans une transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(Long id);

    /**
     * Compteurs en haut du catalogue, calculés en base.
     *
     * Avant, l'écran les calculait à partir de tout le catalogue chargé dans le navigateur.
     * Comme on ne le charge plus en entier, il faut les calculer en base. Une seule requête
     * pour les quatre plutôt que quatre COUNT : ils portent sur les mêmes produits et doivent
     * être cohérents, ce que quatre requêtes séparées ne garantissent pas.
     *
     * COALESCE partout : sur un catalogue VIDE, SUM renvoie NULL et pas 0. La projection
     * utilise des long primitifs, et Spring Data plantait avec « Null return value from
     * advice ». Ça arrivait pour toute nouvelle entreprise.
     */
    @Query("""
           SELECT COUNT(p) AS total,
                  COALESCE(SUM(CASE WHEN p.stockQuantity = 0 THEN 1 ELSE 0 END), 0) AS outOfStock,
                  COALESCE(SUM(CASE WHEN p.stockQuantity > 0 AND p.stockQuantity < p.minStockAlert THEN 1 ELSE 0 END), 0) AS lowStock,
                  COALESCE(SUM(p.stockQuantity * p.purchasePrice), 0) AS stockValue
           FROM Product p
           """)
    CatalogSummaryView catalogSummary();

    /**
     * Produits avec le plus de stock, pour le tableau de bord.
     *
     * Ce n'est PAS un classement des meilleures ventes (pour ça il faudrait additionner les
     * OrderItem). Le nom le dit, et l'écran aussi.
     */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity > 0 ORDER BY p.stockQuantity DESC")
    List<Product> findTopStocked(Pageable pageable);

    /** Produits sous leur seuil d'alerte, du plus urgent au moins urgent. */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity < p.minStockAlert ORDER BY p.stockQuantity ASC")
    List<Product> findMostDepleted(Pageable pageable);

    /**
     * Projection par interface plutôt qu'avec un constructeur : le type renvoyé par un calcul
     * JPQL (COUNT, SUM sur Integer × BigDecimal) dépend du dialecte, et avec un constructeur
     * il est fixé, ce qui peut faire planter au démarrage. Avec une interface, Spring Data
     * fait la conversion.
     */
    interface CatalogSummaryView {
        long getTotal();
        long getOutOfStock();
        long getLowStock();
        BigDecimal getStockValue();
    }
}
