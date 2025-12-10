package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ActivityLogService activityLogService;

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
            // Don't fail business operation if logging fails
        }
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getActiveProducts() {
        return productRepository.findByActiveTrue();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Optional<Product> getProductByCode(String code) {
        return productRepository.findByCode(code);
    }

    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    public List<Product> getLowStockProducts() {
        return productRepository.findByStockQuantityLessThanMinStockAlert();
    }

    public Product createProduct(Product product) {
        // Generate product code automatically
        if (product.getCode() == null || product.getCode().isEmpty()) {
            product.setCode(generateProductCode());
        }

        if (productRepository.existsByCode(product.getCode())) {
            throw new RuntimeException("Product code already exists");
        }
        Product savedProduct = productRepository.save(product);

        // Log activity
        logActivity(ActivityLog.ActionType.CREATE, "Product", savedProduct.getId(),
            "Création du produit " + savedProduct.getName() + " (" + savedProduct.getCode() + ")");

        return savedProduct;
    }

    private String generateProductCode() {
        // Get the last product to determine the next code number
        List<Product> allProducts = productRepository.findAll();
        int maxNumber = 0;

        for (Product p : allProducts) {
            String code = p.getCode();
            if (code != null && code.startsWith("PROD")) {
                try {
                    int number = Integer.parseInt(code.substring(4));
                    if (number > maxNumber) {
                        maxNumber = number;
                    }
                } catch (NumberFormatException e) {
                    // Ignore codes that don't follow PROD#### format
                }
            }
        }

        // Generate new code: PROD0001, PROD0002, etc.
        return String.format("PROD%04d", maxNumber + 1);
    }

    public Product updateProduct(Long id, Product productDetails) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Don't allow code modification during update
        // product.setCode(productDetails.getCode());
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

        // Log activity
        logActivity(ActivityLog.ActionType.UPDATE, "Product", savedProduct.getId(),
            "Modification du produit " + savedProduct.getName());

        return savedProduct;
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        String productName = product.getName();
        productRepository.delete(product);

        // Log activity
        logActivity(ActivityLog.ActionType.DELETE, "Product", id,
            "Suppression du produit " + productName);
    }

    public void updateStock(Long id, Integer quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        int oldStock = product.getStockQuantity();
        product.setStockQuantity(oldStock + quantity);
        productRepository.save(product);

        // Log activity
        ActivityLog.ActionType actionType = quantity > 0 ? ActivityLog.ActionType.STOCK_IN : ActivityLog.ActionType.STOCK_OUT;
        logActivity(actionType, "Product", id,
            "Mise à jour du stock du produit " + product.getName() + ": " + oldStock + " -> " + product.getStockQuantity());
    }

    public boolean checkStock(Long id, Integer quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return product.getStockQuantity() >= quantity;
    }
}
