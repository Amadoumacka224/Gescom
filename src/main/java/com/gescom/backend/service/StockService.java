package com.gescom.backend.service;

import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.repository.StockMovementRepository;
import com.gescom.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    public List<StockMovement> getAllMovements() {
        return stockMovementRepository.findAllOrderByCreatedAtDesc();
    }

    public Optional<StockMovement> getMovementById(Long id) {
        return stockMovementRepository.findById(id);
    }

    public List<StockMovement> getMovementsByProduct(Long productId) {
        return stockMovementRepository.findByProductId(productId);
    }

    public List<StockMovement> getMovementsByType(StockMovement.MovementType type) {
        return stockMovementRepository.findByType(type);
    }

    public List<StockMovement> getMovementsByDateRange(LocalDateTime start, LocalDateTime end) {
        return stockMovementRepository.findByCreatedAtBetween(start, end);
    }

    public StockMovement addStock(Long productId, Integer quantity, BigDecimal unitCost, String reason, String reference, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (quantity <= 0) {
            throw new RuntimeException("Quantity must be positive");
        }

        Integer previousStock = product.getStockQuantity();
        Integer newStock = previousStock + quantity;

        // Create stock movement
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

        // Update product stock
        product.setStockQuantity(newStock);
        productRepository.save(product);

        return savedMovement;
    }

    public StockMovement removeStock(Long productId, Integer quantity, String reason, String reference, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (quantity <= 0) {
            throw new RuntimeException("Quantity must be positive");
        }

        Integer previousStock = product.getStockQuantity();

        if (previousStock < quantity) {
            throw new RuntimeException("Insufficient stock. Available: " + previousStock + ", Requested: " + quantity);
        }

        Integer newStock = previousStock - quantity;

        // Create stock movement
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

        // Update product stock
        product.setStockQuantity(newStock);
        productRepository.save(product);

        return savedMovement;
    }

    public StockMovement adjustStock(Long productId, Integer newQuantity, String reason, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (newQuantity < 0) {
            throw new RuntimeException("Stock quantity cannot be negative");
        }

        Integer previousStock = product.getStockQuantity();
        Integer difference = newQuantity - previousStock;

        // Create stock movement
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

        // Update product stock
        product.setStockQuantity(newQuantity);
        productRepository.save(product);

        return savedMovement;
    }

    public StockMovement recordDamage(Long productId, Integer quantity, String reason, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (quantity <= 0) {
            throw new RuntimeException("Quantity must be positive");
        }

        Integer previousStock = product.getStockQuantity();

        if (previousStock < quantity) {
            throw new RuntimeException("Insufficient stock");
        }

        Integer newStock = previousStock - quantity;

        // Create stock movement
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

        // Update product stock
        product.setStockQuantity(newStock);
        productRepository.save(product);

        return savedMovement;
    }

    public List<Product> getLowStockProducts() {
        return productRepository.findByStockQuantityLessThanMinStockAlert();
    }

    public List<Product> getOutOfStockProducts() {
        return productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() <= 0)
                .toList();
    }

    public Map<String, Object> getStockStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<Product> allProducts = productRepository.findAll();
        List<Product> lowStock = productRepository.findByStockQuantityLessThanMinStockAlert();
        List<Product> outOfStock = allProducts.stream()
                .filter(p -> p.getStockQuantity() <= 0)
                .toList();

        // Calculate total stock value
        BigDecimal totalStockValue = allProducts.stream()
                .map(p -> p.getPurchasePrice().multiply(new BigDecimal(p.getStockQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate total stock quantity
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
                .orElseThrow(() -> new RuntimeException("Stock movement not found"));
        stockMovementRepository.delete(movement);
    }
}
