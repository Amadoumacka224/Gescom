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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public StockMovement addStock(Long productId, Integer quantity, BigDecimal unitCost, String reason, String reference, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", productId));

        if (quantity <= 0) {
            throw new BusinessException("La quantité doit être positive");
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
                .orElseThrow(() -> new ResourceNotFoundException("Produit", productId));

        if (quantity <= 0) {
            throw new BusinessException("La quantité doit être positive");
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

    public StockMovement adjustStock(Long productId, Integer newQuantity, String reason, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", productId));

        if (newQuantity < 0) {
            throw new BusinessException("La quantité de stock ne peut pas être négative");
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

    public StockMovement recordDamage(Long productId, Integer quantity, String reason, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", productId));

        if (quantity <= 0) {
            throw new BusinessException("La quantité doit être positive");
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

    public void deleteMovement(Long id) {
        StockMovement movement = stockMovementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mouvement de stock", id));

        // Inverser l'effet du mouvement sur le stock du produit
        Product product = movement.getProduct();
        if (product != null) {
            int currentStock = product.getStockQuantity();
            switch (movement.getType()) {
                case STOCK_IN:
                    // L'entrée avait ajouté du stock, on le retire
                    product.setStockQuantity(currentStock - movement.getQuantity());
                    break;
                case STOCK_OUT:
                case DAMAGE:
                    // La sortie avait retiré du stock, on le restaure
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
