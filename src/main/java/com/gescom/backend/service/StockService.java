package com.gescom.backend.service;

import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.InsufficientStockException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.repository.StockMovementRepository;
import com.gescom.backend.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service de gestion du stock et de sa traçabilité.
 * Chaque opération (entrée, sortie, ajustement, casse) modifie la quantité du produit ET
 * enregistre un StockMovement horodaté conservant l'état avant/après — ce qui rend tout
 * mouvement auditable et réversible (cf. deleteMovement, qui annule l'effet d'un mouvement).
 */
@Service
@Transactional
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public StockService(StockMovementRepository stockMovementRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository) {
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> getAllMovements(Pageable pageable) {
        return stockMovementRepository.findAll(pageable);
    }

    /**
     * Page du grand livre, filtrée sur les critères fournis (tous optionnels).
     * Le filtrage est fait en base : sur une liste paginée, filtrer les lignes reçues ne
     * chercherait que dans la page affichée.
     */
    @Transactional(readOnly = true)
    public Page<StockMovement> searchMovements(StockMovement.MovementType type,
                                               Long productId,
                                               LocalDateTime start,
                                               LocalDateTime end,
                                               String search,
                                               Pageable pageable) {
        Specification<StockMovement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                var product = root.join("product", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(product.get("name")), like),
                        cb.like(cb.lower(product.get("code")), like),
                        cb.like(cb.lower(root.get("reference")), like),
                        cb.like(cb.lower(root.get("reason")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return stockMovementRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> getMovementsByProduct(Long productId, Pageable pageable) {
        return stockMovementRepository.findByProductId(productId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> getMovementsByType(StockMovement.MovementType type, Pageable pageable) {
        return stockMovementRepository.findByType(type, pageable);
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> getMovementsByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return stockMovementRepository.findByCreatedAtBetween(start, end, pageable);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getAllMovements() {
        return stockMovementRepository.findAllOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<StockMovement> getMovementById(Long id) {
        return stockMovementRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsByProduct(Long productId) {
        return stockMovementRepository.findByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsByType(StockMovement.MovementType type) {
        return stockMovementRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsByDateRange(LocalDateTime start, LocalDateTime end) {
        return stockMovementRepository.findByCreatedAtBetween(start, end);
    }

    /** Entrée de stock (réapprovisionnement). Trace un mouvement STOCK_IN avant/après et met à jour le produit. */
    public StockMovement addStock(Long productId, Integer quantity, BigDecimal unitCost, String reason, String reference, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("product", productId));

        if (quantity <= 0) {
            throw BusinessException.of("stock.quantity.positive", "La quantité doit être positive");
        }

        Integer previousStock = product.getStockQuantity();
        Integer newStock = previousStock + quantity;

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(StockMovement.MovementType.STOCK_IN);
        movement.setQuantity(quantity);
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setUnitCost(unitCost);
        movement.setReason(reason);
        movement.setReference(reference);

        if (userId != null) {
            userRepository.findById(userId).ifPresent(movement::setUser);
        }

        StockMovement savedMovement = stockMovementRepository.save(movement);

        product.setStockQuantity(newStock);
        productRepository.save(product);

        return savedMovement;
    }

    public StockMovement removeStock(Long productId, Integer quantity, String reason, String reference, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("product", productId));

        if (quantity <= 0) {
            throw BusinessException.of("stock.quantity.positive", "La quantité doit être positive");
        }

        Integer previousStock = product.getStockQuantity();

        if (previousStock < quantity) {
            throw new InsufficientStockException(product.getName(), previousStock, quantity);
        }

        Integer newStock = previousStock - quantity;

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(StockMovement.MovementType.STOCK_OUT);
        movement.setQuantity(quantity);
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setReason(reason);
        movement.setReference(reference);

        if (userId != null) {
            userRepository.findById(userId).ifPresent(movement::setUser);
        }

        StockMovement savedMovement = stockMovementRepository.save(movement);

        product.setStockQuantity(newStock);
        productRepository.save(product);

        return savedMovement;
    }

    /**
     * Fixe le stock à une valeur absolue (ex : après un inventaire physique).
     * Contrairement à add/removeStock qui raisonnent en delta, on enregistre ici la
     * quantité finale constatée et la différence est calculée automatiquement.
     */
    public StockMovement adjustStock(Long productId, Integer newQuantity, String reason, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("product", productId));

        if (newQuantity < 0) {
            throw BusinessException.of("stock.quantity.notNegative",
                    "La quantité de stock ne peut pas être négative");
        }

        Integer previousStock = product.getStockQuantity();
        Integer difference = newQuantity - previousStock;

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(StockMovement.MovementType.ADJUSTMENT);
        movement.setQuantity(Math.abs(difference));
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newQuantity);
        movement.setReason(reason);

        if (userId != null) {
            userRepository.findById(userId).ifPresent(movement::setUser);
        }

        StockMovement savedMovement = stockMovementRepository.save(movement);

        product.setStockQuantity(newQuantity);
        productRepository.save(product);

        return savedMovement;
    }

    /**
     * Retour client : la marchandise revient dans notre stock (le client rend un produit).
     * Trace un mouvement RETURN et augmente la quantité — même logique qu'une entrée, avec
     * un type dédié pour la traçabilité.
     */
    public StockMovement returnStock(Long productId, Integer quantity, String reason, String reference, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("product", productId));

        if (quantity <= 0) {
            throw BusinessException.of("stock.quantity.positive", "La quantité doit être positive");
        }

        Integer previousStock = product.getStockQuantity();
        Integer newStock = previousStock + quantity;

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(StockMovement.MovementType.RETURN);
        movement.setQuantity(quantity);
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setReason(reason);
        movement.setReference(reference);

        if (userId != null) {
            userRepository.findById(userId).ifPresent(movement::setUser);
        }

        StockMovement savedMovement = stockMovementRepository.save(movement);

        product.setStockQuantity(newStock);
        productRepository.save(product);

        return savedMovement;
    }

    public StockMovement recordDamage(Long productId, Integer quantity, String reason, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("product", productId));

        if (quantity <= 0) {
            throw BusinessException.of("stock.quantity.positive", "La quantité doit être positive");
        }

        Integer previousStock = product.getStockQuantity();

        if (previousStock < quantity) {
            throw new InsufficientStockException(product.getName(), previousStock, quantity);
        }

        Integer newStock = previousStock - quantity;

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(StockMovement.MovementType.DAMAGE);
        movement.setQuantity(quantity);
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setReason(reason);

        if (userId != null) {
            userRepository.findById(userId).ifPresent(movement::setUser);
        }

        StockMovement savedMovement = stockMovementRepository.save(movement);

        product.setStockQuantity(newStock);
        productRepository.save(product);

        return savedMovement;
    }

    @Transactional(readOnly = true)
    public List<Product> getLowStockProducts() {
        return productRepository.findByStockQuantityLessThanMinStockAlert();
    }

    @Transactional(readOnly = true)
    public List<Product> getOutOfStockProducts() {
        return productRepository.findByStockQuantityLessThanEqual(0);
    }

    /**
     * Agrège les indicateurs de stock pour le tableau de bord : nombre de produits,
     * ruptures, alertes seuil bas, et valeur totale du stock (au prix d'achat).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStockStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<Product> allProducts = productRepository.findAll();
        List<Product> lowStock = productRepository.findByStockQuantityLessThanMinStockAlert();
        List<Product> outOfStock = productRepository.findByStockQuantityLessThanEqual(0);

        BigDecimal totalStockValue = allProducts.stream()
                .map(p -> p.getPurchasePrice().multiply(new BigDecimal(p.getStockQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Integer totalStockQuantity = allProducts.stream()
                .mapToInt(Product::getStockQuantity)
                .sum();

        stats.put("totalProducts", allProducts.size());
        stats.put("lowStockCount", lowStock.size());
        stats.put("outOfStockCount", outOfStock.size());
        stats.put("totalStockValue", totalStockValue);
        stats.put("totalStockQuantity", totalStockQuantity);
        stats.put("lowStockProducts", lowStock);
        stats.put("outOfStockProducts", outOfStock);

        return stats;
    }

    /**
     * Supprime un mouvement de stock en annulant son effet sur la quantité du produit,
     * afin que le stock reste cohérent avec l'historique restant.
     */
    public void deleteMovement(Long id) {
        StockMovement movement = stockMovementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("stockMovement", id));

        // Inverser l'effet du mouvement sur le stock du produit
        Product product = movement.getProduct();
        if (product != null) {
            int currentStock = product.getStockQuantity();
            switch (movement.getType()) {
                case STOCK_IN:
                case RETURN:
                    // L'entrée (réapprovisionnement ou retour client) avait ajouté du stock,
                    // on le retire.
                    product.setStockQuantity(currentStock - movement.getQuantity());
                    break;
                case STOCK_OUT:
                case DAMAGE:
                    // La sortie (vente, casse) avait retiré du stock, on le restaure.
                    product.setStockQuantity(currentStock + movement.getQuantity());
                    break;
                case ADJUSTMENT:
                    // On revient au stock précédent
                    product.setStockQuantity(movement.getPreviousStock());
                    break;
            }
            productRepository.save(product);
        }

        stockMovementRepository.delete(movement);
    }
}
