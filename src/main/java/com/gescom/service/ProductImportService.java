package com.gescom.service;

import com.gescom.dto.ImportResultDto;
import com.gescom.dto.ProductImportDto;
import com.gescom.entity.Product;
import com.gescom.repository.ProductRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductImportService {

    private static final Logger logger = LoggerFactory.getLogger(ProductImportService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private Validator validator;

    /**
     * Importe une liste de produits avec les options spécifiées
     */
    public ImportResultDto importProducts(List<ProductImportDto> productsToImport, 
                                        boolean updateExisting, 
                                        boolean skipInvalid, 
                                        boolean dryRun) {
        
        ImportResultDto result = new ImportResultDto(dryRun);
        result.setTotalCount(productsToImport.size());
        
        logger.info("Début de l'import de {} produits (updateExisting: {}, skipInvalid: {}, dryRun: {})",
                productsToImport.size(), updateExisting, skipInvalid, dryRun);

        try {
            // Validation des données
            List<ProductImportDto> validProducts = validateProducts(productsToImport, result, skipInvalid);
            
            if (!dryRun) {
                // Import réel des produits
                processProductImport(validProducts, result, updateExisting);
            } else {
                // Mode test - simulation
                simulateProductImport(validProducts, result, updateExisting);
            }

        } catch (Exception e) {
            logger.error("Erreur lors de l'import des produits", e);
            result.addError(0, "SYSTEM", "Erreur système lors de l'import: " + e.getMessage());
        }

        result.finish();
        return result;
    }

    /**
     * Valide la liste des produits à importer
     */
    private List<ProductImportDto> validateProducts(List<ProductImportDto> products, 
                                                  ImportResultDto result, 
                                                  boolean skipInvalid) {
        
        List<ProductImportDto> validProducts = new ArrayList<>();
        Set<String> seenReferences = new HashSet<>();
        Set<String> seenNames = new HashSet<>();

        for (ProductImportDto productDto : products) {
            List<String> errors = validateProduct(productDto);
            
            // Vérification des doublons dans le fichier
            if (productDto.getReference() != null && !productDto.getReference().isEmpty()) {
                if (seenReferences.contains(productDto.getReference())) {
                    errors.add("Référence en doublon dans le fichier");
                } else {
                    seenReferences.add(productDto.getReference());
                }
            }
            
            if (seenNames.contains(productDto.getName())) {
                errors.add("Nom en doublon dans le fichier");
            } else {
                seenNames.add(productDto.getName());
            }

            if (!errors.isEmpty()) {
                for (String error : errors) {
                    result.addError(productDto.getLineNumber(), "VALIDATION", error);
                }
                
                if (skipInvalid) {
                    result.incrementSkipped();
                    continue;
                }
            }

            validProducts.add(productDto);
        }

        logger.info("Validation terminée: {} produits valides sur {}", validProducts.size(), products.size());
        return validProducts;
    }

    /**
     * Valide un produit individuel
     */
    private List<String> validateProduct(ProductImportDto productDto) {
        List<String> errors = new ArrayList<>();

        // Validation Jakarta Bean Validation
        Set<ConstraintViolation<ProductImportDto>> violations = validator.validate(productDto);
        for (ConstraintViolation<ProductImportDto> violation : violations) {
            errors.add(violation.getMessage());
        }

        // Validations métier supplémentaires
        if (productDto.getName() != null && productDto.getName().length() < 2) {
            errors.add("Le nom doit contenir au moins 2 caractères");
        }

        if (productDto.getPrice() != null && productDto.getPrice().compareTo(new BigDecimal("999999.99")) > 0) {
            errors.add("Le prix ne peut pas dépasser 999,999.99");
        }

        if (productDto.getQuantity() != null && productDto.getQuantity() > 999999) {
            errors.add("La quantité ne peut pas dépasser 999,999");
        }

        if (productDto.getReference() != null && !productDto.getReference().isEmpty()) {
            if (!productDto.getReference().matches("^[A-Za-z0-9\\-_]+$")) {
                errors.add("La référence ne peut contenir que des lettres, chiffres, tirets et underscores");
            }
        }

        return errors;
    }

    /**
     * Traite l'import réel des produits
     */
    private void processProductImport(List<ProductImportDto> validProducts, 
                                    ImportResultDto result, 
                                    boolean updateExisting) {
        
        for (ProductImportDto productDto : validProducts) {
            try {
                Product existingProduct = findExistingProduct(productDto);
                
                if (existingProduct != null) {
                    if (updateExisting) {
                        updateProduct(existingProduct, productDto);
                        productRepository.save(existingProduct);
                        result.incrementUpdated();
                        logger.debug("Produit mis à jour: {}", productDto.getName());
                    } else {
                        result.addWarning("Produit ignoré (existe déjà): " + productDto.getName());
                        result.incrementSkipped();
                    }
                } else {
                    Product newProduct = createProduct(productDto);
                    productRepository.save(newProduct);
                    result.incrementImported();
                    logger.debug("Nouveau produit créé: {}", productDto.getName());
                }

            } catch (Exception e) {
                logger.error("Erreur lors de l'import du produit ligne {}: {}", 
                           productDto.getLineNumber(), e.getMessage(), e);
                result.addError(productDto.getLineNumber(), "IMPORT", 
                               "Erreur lors de l'import: " + e.getMessage());
            }
        }
    }

    /**
     * Simule l'import des produits (mode test)
     */
    private void simulateProductImport(List<ProductImportDto> validProducts, 
                                     ImportResultDto result, 
                                     boolean updateExisting) {
        
        for (ProductImportDto productDto : validProducts) {
            Product existingProduct = findExistingProduct(productDto);
            
            if (existingProduct != null) {
                if (updateExisting) {
                    result.incrementUpdated();
                } else {
                    result.addWarning("Produit serait ignoré (existe déjà): " + productDto.getName());
                    result.incrementSkipped();
                }
            } else {
                result.incrementImported();
            }
        }
    }

    /**
     * Trouve un produit existant par référence ou nom
     */
    private Product findExistingProduct(ProductImportDto productDto) {
        // Recherche par référence si disponible
        if (productDto.getReference() != null && !productDto.getReference().isEmpty()) {
            Optional<Product> byReference = productRepository.findByReference(productDto.getReference());
            if (byReference.isPresent()) {
                return byReference.get();
            }
        }

        // Recherche par nom exact
        List<Product> byName = productRepository.searchByNameOrReference(productDto.getName());
        return byName.stream()
                .filter(p -> p.getName().equals(productDto.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Crée un nouveau produit à partir du DTO
     */
    private Product createProduct(ProductImportDto productDto) {
        Product product = new Product();
        product.setName(productDto.getName());
        product.setDescription(productDto.getDescription());
        product.setUnitPrice(productDto.getPrice());
        product.setStock(productDto.getQuantity());
        product.setReference(productDto.getReference());
        product.setCategory(productDto.getCategory() != null ? productDto.getCategory() : "Général");
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        
        // TODO: Gérer les catégories et fournisseurs si nécessaire
        // product.setCategory(findOrCreateCategory(productDto.getCategory()));
        // product.setSupplier(findOrCreateSupplier(productDto.getSupplier()));
        
        return product;
    }

    /**
     * Met à jour un produit existant avec les données du DTO
     */
    private void updateProduct(Product existing, ProductImportDto productDto) {
        existing.setName(productDto.getName());
        
        if (productDto.getDescription() != null && !productDto.getDescription().isEmpty()) {
            existing.setDescription(productDto.getDescription());
        }
        
        existing.setUnitPrice(productDto.getPrice());
        existing.setStock(productDto.getQuantity());
        
        if (productDto.getReference() != null && !productDto.getReference().isEmpty()) {
            existing.setReference(productDto.getReference());
        }
        
        if (productDto.getCategory() != null && !productDto.getCategory().isEmpty()) {
            existing.setCategory(productDto.getCategory());
        }
        
        existing.setUpdatedAt(LocalDateTime.now());
        
        // TODO: Gérer les catégories et fournisseurs si nécessaire
    }

    /**
     * Analyse et retourne des statistiques sur les données à importer
     */
    public Map<String, Object> analyzeImportData(List<ProductImportDto> products) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalCount", products.size());
        stats.put("validCount", products.stream().mapToInt(p -> p.isValid() ? 1 : 0).sum());
        stats.put("invalidCount", products.stream().mapToInt(p -> p.isValid() ? 0 : 1).sum());
        stats.put("emptyCount", products.stream().mapToInt(p -> p.isEmpty() ? 1 : 0).sum());
        
        // Statistiques sur les prix
        List<BigDecimal> prices = products.stream()
                .map(ProductImportDto::getPrice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
        if (!prices.isEmpty()) {
            stats.put("minPrice", prices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            stats.put("maxPrice", prices.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            stats.put("avgPrice", prices.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(prices.size()), BigDecimal.ROUND_HALF_UP));
        }
        
        // Catégories uniques
        Set<String> categories = products.stream()
                .map(ProductImportDto::getCategory)
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        stats.put("uniqueCategories", categories);
        stats.put("categoryCount", categories.size());
        
        // Fournisseurs uniques
        Set<String> suppliers = products.stream()
                .map(ProductImportDto::getSupplier)
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        stats.put("uniqueSuppliers", suppliers);
        stats.put("supplierCount", suppliers.size());
        
        return stats;
    }
}