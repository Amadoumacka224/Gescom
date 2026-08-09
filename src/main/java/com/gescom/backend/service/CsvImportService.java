package com.gescom.backend.service;

import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.Product;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.repository.CategoryRepository;
import com.gescom.backend.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service d'import de produits depuis un fichier CSV.
 * Conçu pour être tolérant aux fichiers réels : détection automatique du séparateur
 * (; , ou tabulation), correspondance des colonnes par alias multilingues (ex : « name »,
 * « nom », « designation »), et valeurs par défaut quand une cellule est absente ou invalide.
 * L'import est ligne par ligne : une ligne en erreur stoppe l'analyse, mais un produit
 * impossible à sauvegarder (ex : code en doublon) est seulement ignoré et tracé.
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductService productService;

    public CsvImportService(ProductRepository productRepository, CategoryRepository categoryRepository,
                            ProductService productService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productService = productService;
    }

    public int importProducts(MultipartFile file) throws Exception {
        List<Product> products = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw BusinessException.of("import.file.empty", "Le fichier est vide");
            }

            log.debug("CSV Header: {}", headerLine);

            // Détection du séparateur d'après la ligne d'en-tête : point-virgule (Excel FR),
            // tabulation, ou virgule par défaut.
            char delimiter = ';';
            if (headerLine.contains(";")) {
                delimiter = ';';
            } else if (headerLine.contains("\t")) {
                delimiter = '\t';
            } else {
                delimiter = ',';
            }
            log.debug("Detected delimiter: {}", delimiter);

            // Indexe chaque nom de colonne (en minuscules) vers sa position, pour ensuite
            // retrouver une valeur par son nom plutôt que par un numéro de colonne figé.
            String[] headers = parseCsvLine(headerLine, delimiter);
            Map<String, Integer> columnMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String header = headers[i].trim().toLowerCase();
                columnMap.put(header, i);
                log.debug("Column {}: {}", i, header);
            }

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    log.debug("Processing line {}: {}", lineNumber, line);
                    Product product = parseProductLine(line, columnMap, delimiter);
                    if (product != null) {
                        log.debug("Product parsed: {} - {}", product.getCode(), product.getName());
                        products.add(product);
                    }
                } catch (Exception e) {
                    log.error("Error at line {}: {}", lineNumber, e.getMessage());
                    throw BusinessException.of("import.line.error",
                            "Erreur à la ligne " + lineNumber + " : " + e.getMessage(),
                            lineNumber, e.getMessage());
                }
            }
        }

        log.info("Saving {} products...", products.size());
        int savedCount = 0;
        for (Product product : products) {
            try {
                productService.createProduct(product);
                savedCount++;
            } catch (Exception e) {
                log.warn("Error saving product {}: {}", product.getName(), e.getMessage());
            }
        }
        log.info("{} products saved successfully!", savedCount);
        return savedCount;
    }

    private Product parseProductLine(String line, Map<String, Integer> columnMap, char delimiter) {
        String[] values = parseCsvLine(line, delimiter);

        log.debug("Values count: {}", values.length);
        log.debug("Available columns: {}", columnMap.keySet());

        Product product = new Product();

        String code = getColumnValue(values, columnMap, "code", "product code", "code produit");
        product.setCode(code);

        String productName = getColumnValue(values, columnMap, "name", "nom", "product name", "nom produit", "designation");
        product.setName(productName);

        if (product.getName() == null || product.getName().isEmpty()) {
            log.error("Available columns: {}", columnMap.keySet());
            log.error("Parsed values: {}", Arrays.toString(values));
            throw new BusinessException("Le nom du produit est obligatoire. Colonnes disponibles: " + columnMap.keySet());
        }

        product.setDescription(getColumnValue(values, columnMap, "description", "desc"));

        String categoryName = getColumnValue(values, columnMap, "category", "categorie", "catégorie");
        if (categoryName != null && !categoryName.isEmpty()) {
            Category category = categoryRepository.findByName(categoryName).orElse(null);
            product.setCategory(category);
        }

        String purchasePrice = getColumnValue(values, columnMap, "purchase price", "prix achat", "prix d'achat", "purchaseprice");
        product.setPurchasePrice(parseBigDecimal(purchasePrice));

        String sellingPrice = getColumnValue(values, columnMap, "selling price", "prix vente", "prix de vente", "sellingprice");
        product.setSellingPrice(parseBigDecimal(sellingPrice));

        product.setUnit(getColumnValue(values, columnMap, "unit", "unité", "unite"));
        if (product.getUnit() == null || product.getUnit().isEmpty()) {
            product.setUnit("pièce");
        }

        String stockQty = getColumnValue(values, columnMap, "stock quantity", "stock", "quantité", "quantite", "qty", "stockquantity");
        product.setStockQuantity(parseInteger(stockQty));

        String minStock = getColumnValue(values, columnMap, "min stock alert", "min stock", "seuil alerte", "minstockalert");
        product.setMinStockAlert(parseInteger(minStock));

        product.setBarcode(getColumnValue(values, columnMap, "barcode", "code barre", "code-barre"));

        String active = getColumnValue(values, columnMap, "active", "actif");
        product.setActive(parseBoolean(active));

        return product;
    }

    /**
     * Récupère la valeur d'une colonne en testant plusieurs alias possibles
     * (ex : "name", "nom", "designation") — rend l'import indépendant de la langue de l'en-tête.
     */
    private String getColumnValue(String[] values, Map<String, Integer> columnMap, String... possibleNames) {
        for (String name : possibleNames) {
            Integer index = columnMap.get(name.toLowerCase());
            if (index != null && index < values.length) {
                return parseString(values[index]);
            }
        }
        return null;
    }

    /**
     * Découpe une ligne CSV en respectant les guillemets : un séparateur situé à l'intérieur
     * d'une paire de guillemets fait partie de la valeur et ne coupe pas le champ.
     */
    private String[] parseCsvLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false; // bascule à chaque guillemet rencontré

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());

        return values.toArray(new String[0]);
    }

    private String parseString(String value) {
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("null")) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("null")) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("null")) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Boolean parseBoolean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        String v = value.trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("oui");
    }
}
