package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.DuplicateResourceException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ActivityLogService activityLogService;

    public ProductService(ProductRepository productRepository, ActivityLogService activityLogService) {
        this.productRepository = productRepository;
        this.activityLogService = activityLogService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }

    private void logActivity(ActivityLog.ActionType actionType, String entity, Long entityId, String description) {
        try {
            Long userId = getCurrentUserId();
            if (userId != null) {
                activityLogService.logActivity(userId, actionType, entity, entityId, description, null, null);
            }
        } catch (Exception e) {
            log.warn("Échec du log d'activité: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Product> getActiveProducts() {
        return productRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductByCode(String code) {
        return productRepository.findByCode(code);
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    @Transactional(readOnly = true)
    public List<Product> getLowStockProducts() {
        return productRepository.findByStockQuantityLessThanMinStockAlert();
    }

    public synchronized Product createProduct(Product product) {
        if (product.getCode() == null || product.getCode().isEmpty()) {
            product.setCode(generateProductCode());
        }

        if (productRepository.existsByCode(product.getCode())) {
            throw new DuplicateResourceException("Produit", "code", product.getCode());
        }
        Product savedProduct = productRepository.save(product);

        logActivity(ActivityLog.ActionType.CREATE, "Product", savedProduct.getId(),
            "Création du produit " + savedProduct.getName() + " (" + savedProduct.getCode() + ")");

        return savedProduct;
    }

    private String generateProductCode() {
        long count = productRepository.count();
        String code;
        int attempt = (int) count + 1;
        do {
            code = String.format("PROD%04d", attempt);
            attempt++;
        } while (productRepository.existsByCode(code));
        return code;
    }

    public Product updateProduct(Long id, Product productDetails) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", id));

        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setPurchasePrice(productDetails.getPurchasePrice());
        product.setSellingPrice(productDetails.getSellingPrice());
        product.setCategory(productDetails.getCategory());
        product.setUnit(productDetails.getUnit());
        product.setStockQuantity(productDetails.getStockQuantity());
        product.setMinStockAlert(productDetails.getMinStockAlert());
        product.setBarcode(productDetails.getBarcode());
        product.setImageUrl(productDetails.getImageUrl());
        product.setActive(productDetails.getActive());

        Product savedProduct = productRepository.save(product);

        logActivity(ActivityLog.ActionType.UPDATE, "Product", savedProduct.getId(),
            "Modification du produit " + savedProduct.getName());

        return savedProduct;
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", id));
        String productName = product.getName();
        productRepository.delete(product);

        logActivity(ActivityLog.ActionType.DELETE, "Product", id,
            "Suppression du produit " + productName);
    }

    public void updateStock(Long id, Integer quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", id));
        int oldStock = product.getStockQuantity();
        int newStock = oldStock + quantity;
        if (newStock < 0) {
            throw new BusinessException("Le stock ne peut pas devenir négatif. Stock actuel: " + oldStock);
        }
        product.setStockQuantity(newStock);
        productRepository.save(product);

        ActivityLog.ActionType actionType = quantity > 0 ? ActivityLog.ActionType.STOCK_IN : ActivityLog.ActionType.STOCK_OUT;
        logActivity(actionType, "Product", id,
            "Mise à jour du stock du produit " + product.getName() + ": " + oldStock + " -> " + newStock);
    }

    @Transactional(readOnly = true)
    public boolean checkStock(Long id, Integer quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", id));
        return product.getStockQuantity() >= quantity;
    }
}
