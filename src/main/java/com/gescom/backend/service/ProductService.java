package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.User;
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

/**
 * Service métier du catalogue produits (CRUD + suivi de stock simple).
 * Garantit l'unicité du code produit, génère un code automatique si absent et journalise
 * chaque opération. Note : les mouvements de stock relèvent de {@link StockService} ;
 * updateStock() est un ajustement rapide par delta, mais il y délègue afin que la
 * correction reste tracée dans le grand livre des mouvements.
 */
@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ActivityLogService activityLogService;
    private final StockService stockService;

    public ProductService(ProductRepository productRepository,
                          ActivityLogService activityLogService,
                          StockService stockService) {
        this.productRepository = productRepository;
        this.activityLogService = activityLogService;
        this.stockService = stockService;
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

    /**
     * Recherche un produit par code-barres (scan à la caisse). Le code-barres n'étant pas unique
     * en base, on privilégie un produit actif ; à défaut on retourne la première correspondance,
     * ce qui permet à l'appelant de distinguer « produit désactivé » de « code-barres inconnu ».
     */
    @Transactional(readOnly = true)
    public Optional<Product> getProductByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        List<Product> matches = productRepository.findByBarcode(barcode.trim());
        return matches.stream().filter(Product::getActive).findFirst()
                .or(() -> matches.stream().findFirst());
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    @Transactional(readOnly = true)
    public List<Product> getLowStockProducts() {
        return productRepository.findByStockQuantityLessThanMinStockAlert();
    }

    // synchronized : sérialise les créations pour éviter que deux produits simultanés
    // ne se voient attribuer le même code auto-généré (PRODxxxx).
    public synchronized Product createProduct(Product product) {
        if (product.getCode() == null || product.getCode().isEmpty()) {
            product.setCode(generateProductCode());
        }

        if (productRepository.existsByCode(product.getCode())) {
            throw new DuplicateResourceException("product", "code", product.getCode());
        }
        Product savedProduct = productRepository.save(product);

        logActivity(ActivityLog.ActionType.CREATE, "Product", savedProduct.getId(),
            "Création du produit " + savedProduct.getName() + " (" + savedProduct.getCode() + ")");

        return savedProduct;
    }

    /**
     * Génère un code unique de la forme PROD0001. On part du nombre de produits existants
     * et on incrémente tant que le code est déjà pris (robuste aux suppressions/trous).
     */
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

    /**
     * Met à jour la fiche produit. Le stock n'en fait délibérément PAS partie : il appartient au
     * grand livre des mouvements (vente à la confirmation, retour client, ajustement) et ne se
     * réécrit que par une opération tracée — {@link #updateStock}, {@link StockService}.
     *
     * L'écrire ici resynchroniserait la fiche sur la valeur qu'affichait le formulaire à son
     * ouverture : une simple correction de prix ramènerait le stock à ce qu'il valait avant les
     * ventes de l'intervalle, sans le moindre mouvement pour l'expliquer. Le champ éventuellement
     * envoyé par un appelant est donc ignoré, et non refusé : la fiche reste modifiable même
     * depuis un client qui poste encore le produit entier.
     */
    public Product updateProduct(Long id, Product productDetails) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("product", id));

        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setPurchasePrice(productDetails.getPurchasePrice());
        product.setSellingPrice(productDetails.getSellingPrice());
        product.setCategory(productDetails.getCategory());
        product.setUnit(productDetails.getUnit());
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
                .orElseThrow(() -> new ResourceNotFoundException("product", id));
        String productName = product.getName();
        productRepository.delete(product);

        logActivity(ActivityLog.ActionType.DELETE, "Product", id,
            "Suppression du produit " + productName);
    }

    /**
     * Ajustement direct du stock par delta signé (positif = entrée, négatif = sortie).
     * Délègue à {@link StockService} afin que la correction figure dans le grand livre des
     * mouvements : sans cela le registre repartirait d'un solde inexpliqué et le stock ne
     * serait plus reconstituable à partir des mouvements.
     */
    public void updateStock(Long id, Integer quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("product", id));

        // Un delta nul ne change rien : ni mouvement, ni entrée de journal.
        if (quantity == 0) {
            return;
        }

        int oldStock = product.getStockQuantity();
        Long userId = getCurrentUserId();
        String reason = "Ajustement manuel du stock";

        if (quantity > 0) {
            stockService.addStock(id, quantity, null, reason, null, userId);
        } else {
            // removeStock lève InsufficientStockException (400) si le stock deviendrait négatif.
            stockService.removeStock(id, -quantity, reason, null, userId);
        }

        ActivityLog.ActionType actionType = quantity > 0 ? ActivityLog.ActionType.STOCK_IN : ActivityLog.ActionType.STOCK_OUT;
        logActivity(actionType, "Product", id,
            "Mise à jour du stock du produit " + product.getName() + ": " + oldStock + " -> " + (oldStock + quantity));
    }

    @Transactional(readOnly = true)
    public boolean checkStock(Long id, Integer quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("product", id));
        return product.getStockQuantity() >= quantity;
    }
}
