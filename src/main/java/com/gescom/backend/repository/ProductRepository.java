package com.gescom.backend.repository;

import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * {@code JpaSpecificationExecutor} porte la recherche paginée du catalogue : les critères de
 * l'écran Produits sont tous facultatifs et se combinent, ce qu'une méthode dérivée par nom ne
 * sait pas exprimer. Le cloisonnement par entreprise reste assuré — le filtre Hibernate couvre
 * aussi les requêtes par critères.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
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

    /**
     * Compteurs d'en-tête du catalogue, agrégés en base.
     *
     * L'écran les déduisait de la liste complète chargée dans le navigateur ; celle-ci n'étant
     * plus rapatriée en entier, il faut les calculer là où sont les données. Une seule requête
     * pour les quatre, plutôt que quatre COUNT : ils décrivent le même ensemble et doivent être
     * cohérents entre eux, ce que quatre requêtes séparées ne garantissent pas.
     *
     * COALESCE sur la valeur de stock : SUM ne rend pas 0 mais NULL sur un catalogue vide.
     */
    @Query("""
           SELECT COUNT(p) AS total,
                  SUM(CASE WHEN p.stockQuantity = 0 THEN 1 ELSE 0 END) AS outOfStock,
                  SUM(CASE WHEN p.stockQuantity > 0 AND p.stockQuantity < p.minStockAlert THEN 1 ELSE 0 END) AS lowStock,
                  COALESCE(SUM(p.stockQuantity * p.purchasePrice), 0) AS stockValue
           FROM Product p
           """)
    CatalogSummaryView catalogSummary();

    /**
     * Projection par interface plutôt qu'expression constructeur : les types rendus par un
     * agrégat JPQL (COUNT, SUM sur un produit Integer × BigDecimal) dépendent du dialecte, et
     * une expression constructeur les fige au risque d'échouer au démarrage. L'interface laisse
     * Spring Data convertir.
     */
    interface CatalogSummaryView {
        long getTotal();
        long getOutOfStock();
        long getLowStock();
        BigDecimal getStockValue();
    }
}
