package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.DuplicateResourceException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.dto.product.ProductCatalogSummary;
import com.gescom.backend.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service du catalogue produits (CRUD + gestion simple du stock).
 * Vérifie que le code produit est unique, en génère un s'il n'y en a pas, et enregistre
 * chaque opération dans le journal. Les mouvements de stock sont gérés par
 * {@link StockService} ; updateStock() permet un ajustement rapide, mais passe par lui pour
 * que la correction apparaisse dans les mouvements de stock.
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

    /**
     * Une page du catalogue, filtrée et triée en base.
     *
     * Avant, l'écran Produits chargeait tout le catalogue puis filtrait, triait et découpait
     * dans le navigateur. Avec un vrai magasin, ça fait des Mo à charger pour afficher
     * cinquante lignes. Et surtout, filtrer une liste déjà coupée ne chercherait que dans la
     * page reçue : maintenant que la pagination est faite par le serveur, la recherche et le
     * tri doivent l'être aussi, sinon l'écran affiche des résultats faux.
     *
     * Tous les filtres sont facultatifs et combinables.
     */
    @Transactional(readOnly = true)
    public Page<Product> searchProducts(String search, Long categoryId, Boolean active, Pageable pageable) {
        return productRepository.findAll(buildFilter(search, categoryId, active), pageable);
    }

    /** Compteurs en haut de page, calculés en base sur tout le catalogue (voir {@link ProductCatalogSummary}). */
    @Transactional(readOnly = true)
    public ProductCatalogSummary getCatalogSummary() {
        ProductRepository.CatalogSummaryView view = productRepository.catalogSummary();
        return new ProductCatalogSummary(
                view.getTotal(),
                view.getOutOfStock(),
                view.getLowStock(),
                view.getStockValue() != null ? view.getStockValue() : BigDecimal.ZERO);
    }

    private Specification<Product> buildFilter(String search, Long categoryId, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                // Mêmes champs que l'ancienne recherche côté navigateur (nom, code, code-barres
                // et nom de la catégorie), pour que l'utilisateur trouve la même chose qu'avant.
                // LOWER des deux côtés : on ignore les majuscules, et la collation de la base ne
                // le garantit pas.
                String pattern = "%" + search.toLowerCase().trim() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("barcode"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("category").get("name"), "")), pattern)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
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
     * Cherche un produit par code-barres (scan à la caisse). Le code-barres n'est pas unique en
     * base, donc on prend de préférence un produit actif ; sinon on renvoie le premier trouvé,
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

    // synchronized : les créations passent l'une après l'autre, pour que deux produits créés
    // en même temps n'aient pas le même code automatique (PRODxxxx).
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
     * Génère un code unique du type PROD0001. On part du nombre de produits existants et on
     * augmente tant que le code est déjà pris (ça marche même s'il y a eu des suppressions).
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
     * Modifie la fiche produit. Le stock n'en fait PAS partie, exprès : il dépend des mouvements
     * (vente à la confirmation, retour client, ajustement) et ne change que par une opération
     * enregistrée ({@link #updateStock}, {@link StockService}).
     *
     * Si on l'écrivait ici, on remettrait la valeur affichée dans le formulaire à son
     * ouverture : une simple correction de prix remettrait le stock d'avant les ventes faites
     * entre-temps, sans aucun mouvement pour l'expliquer. Si un appelant envoie quand même ce
     * champ, on l'ignore au lieu de refuser : la fiche reste modifiable même depuis un client
     * qui envoie encore le produit complet.
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
     * Ajustement direct du stock avec une variation (positif = entrée, négatif = sortie).
     * Passe par {@link StockService} pour que la correction apparaisse dans les mouvements :
     * sinon l'historique partirait d'un stock qu'on ne peut pas expliquer, et on ne pourrait
     * plus recalculer le stock à partir des mouvements.
     */
    public void updateStock(Long id, Integer quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("product", id));

        // Une variation de 0 ne change rien : pas de mouvement ni de ligne dans le journal.
        if (quantity == 0) {
            return;
        }

        int oldStock = product.getStockQuantity();
        Long userId = getCurrentUserId();
        String reason = "Ajustement manuel du stock";

        if (quantity > 0) {
            stockService.addStock(id, quantity, null, reason, null, userId);
        } else {
            // removeStock lance InsufficientStockException (400) si le stock devenait négatif.
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
