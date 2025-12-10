package com.gescom.backend.service;

import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.Product;
import com.gescom.backend.repository.CategoryRepository;
import com.gescom.backend.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvImportService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductService productService;

    public int importProducts(MultipartFile file) throws Exception {
        List<Product> products = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            // Read header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new RuntimeException("Le fichier est vide");
            }

            System.out.println("CSV Header: " + headerLine);

            // Detect delimiter (semicolon, tab, or comma)
            char delimiter = ';';
            if (headerLine.contains(";")) {
                delimiter = ';';
            } else if (headerLine.contains("\t")) {
                delimiter = '\t';
            } else {
                delimiter = ',';
            }
            String delimiterName = delimiter == ';' ? "SEMICOLON" : (delimiter == '\t' ? "TAB" : "COMMA");
            System.out.println("Detected delimiter: " + delimiterName);

            // Parse headers to get column indices
            String[] headers = parseCsvLine(headerLine, delimiter);
            java.util.Map<String, Integer> columnMap = new java.util.HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String header = headers[i].trim().toLowerCase();
                columnMap.put(header, i);
                System.out.println("Column " + i + ": " + header);
            }

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                try {
                    System.out.println("Processing line " + lineNumber + ": " + line);
                    Product product = parseProductLine(line, columnMap, delimiter);
                    if (product != null) {
                        System.out.println("Product parsed: " + product.getCode() + " - " + product.getName());
                        products.add(product);
                    }
                } catch (Exception e) {
                    System.err.println("Error at line " + lineNumber + ": " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Erreur à la ligne " + lineNumber + ": " + e.getMessage(), e);
                }
            }
        }

        System.out.println("Saving " + products.size() + " products...");
        // Save products one by one to trigger code generation
        int savedCount = 0;
        for (Product product : products) {
            try {
                productService.createProduct(product);
                savedCount++;
            } catch (Exception e) {
                System.err.println("Error saving product " + product.getName() + ": " + e.getMessage());
                // Continue with next product
            }
        }
        System.out.println(savedCount + " products saved successfully!");
        return savedCount;
    }

    private Product parseProductLine(String line, java.util.Map<String, Integer> columnMap, char delimiter) {
        // Split by delimiter, but handle quoted strings
        String[] values = parseCsvLine(line, delimiter);

        System.out.println("Values count: " + values.length);
        System.out.println("Available columns: " + columnMap.keySet());

        Product product = new Product();

        // Map columns by header name (case insensitive)
        // Code (optional - will be generated if not provided)
        String code = getColumnValue(values, columnMap, "code", "product code", "code produit");
        product.setCode(code);

        // Required fields
        String productName = getColumnValue(values, columnMap, "name", "nom", "product name", "nom produit", "designation");
        product.setName(productName);

        // Validate required fields
        if (product.getName() == null || product.getName().isEmpty()) {
            System.err.println("Available columns: " + columnMap.keySet());
            System.err.println("Parsed values: " + java.util.Arrays.toString(values));
            throw new RuntimeException("Le nom du produit est obligatoire. Colonnes disponibles: " + columnMap.keySet());
        }

        // Optional fields
        product.setDescription(getColumnValue(values, columnMap, "description", "desc"));

        // Category
        String categoryName = getColumnValue(values, columnMap, "category", "categorie", "catégorie");
        if (categoryName != null && !categoryName.isEmpty()) {
            Category category = categoryRepository.findByName(categoryName).orElse(null);
            product.setCategory(category);
        }

        // Prices
        String purchasePrice = getColumnValue(values, columnMap, "purchase price", "prix achat", "prix d'achat", "purchaseprice");
        product.setPurchasePrice(parseBigDecimal(purchasePrice));

        String sellingPrice = getColumnValue(values, columnMap, "selling price", "prix vente", "prix de vente", "sellingprice");
        product.setSellingPrice(parseBigDecimal(sellingPrice));

        // Unit
        product.setUnit(getColumnValue(values, columnMap, "unit", "unité", "unite"));
        if (product.getUnit() == null || product.getUnit().isEmpty()) {
            product.setUnit("pièce"); // Default unit
        }

        // Stock
        String stockQty = getColumnValue(values, columnMap, "stock quantity", "stock", "quantité", "quantite", "qty", "stockquantity");
        product.setStockQuantity(parseInteger(stockQty));

        String minStock = getColumnValue(values, columnMap, "min stock alert", "min stock", "seuil alerte", "minstockalert");
        product.setMinStockAlert(parseInteger(minStock));

        // Barcode
        product.setBarcode(getColumnValue(values, columnMap, "barcode", "code barre", "code-barre"));

        // Active
        String active = getColumnValue(values, columnMap, "active", "actif");
        product.setActive(parseBoolean(active));

        return product;
    }

    private String getColumnValue(String[] values, java.util.Map<String, Integer> columnMap, String... possibleNames) {
        for (String name : possibleNames) {
            Integer index = columnMap.get(name.toLowerCase());
            if (index != null && index < values.length) {
                return parseString(values[index]);
            }
        }
        return null;
    }

    private String[] parseCsvLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

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
