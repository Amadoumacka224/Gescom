package com.gescom.backend.controller;

import com.gescom.backend.entity.Product;
import com.gescom.backend.service.ProductService;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.CsvImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/products")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private CsvExportService csvExportService;

    @Autowired
    private CsvImportService csvImportService;

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/active")
    public ResponseEntity<List<Product>> getActiveProducts() {
        List<Product> products = productService.getActiveProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Product> getProductByCode(@PathVariable String code) {
        return productService.getProductByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<Product>> getProductsByCategory(@PathVariable Long categoryId) {
        List<Product> products = productService.getProductsByCategory(categoryId);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<Product>> getLowStockProducts() {
        List<Product> products = productService.getLowStockProducts();
        return ResponseEntity.ok(products);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createProduct(@RequestBody Product product) {
        try {
            Product createdProduct = productService.createProduct(product);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        try {
            Product updatedProduct = productService.updateProduct(id, product);
            return ResponseEntity.ok(updatedProduct);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStock(@PathVariable Long id, @RequestBody Map<String, Integer> request) {
        try {
            Integer quantity = request.get("quantity");
            productService.updateStock(id, quantity);
            return ResponseEntity.ok().body("Stock updated successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/check-stock/{quantity}")
    public ResponseEntity<Boolean> checkStock(@PathVariable Long id, @PathVariable Integer quantity) {
        boolean available = productService.checkStock(id, quantity);
        return ResponseEntity.ok(available);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            return ResponseEntity.ok().body("Product deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportProducts() {
        try {
            List<Product> products = productService.getAllProducts();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            String[] headers = {
                "ID", "Code", "Name", "Description", "Category", "Purchase Price", "Selling Price",
                "Unit", "Stock Quantity", "Min Stock Alert", "Barcode", "Active", "Created At"
            };

            byte[] csvData = csvExportService.exportToCsv(products, headers, product -> new String[]{
                csvExportService.toString(product.getId()),
                csvExportService.toString(product.getCode()),
                csvExportService.toString(product.getName()),
                csvExportService.toString(product.getDescription()),
                product.getCategory() != null ? csvExportService.toString(product.getCategory().getName()) : "",
                csvExportService.toString(product.getPurchasePrice()),
                csvExportService.toString(product.getSellingPrice()),
                csvExportService.toString(product.getUnit()),
                csvExportService.toString(product.getStockQuantity()),
                csvExportService.toString(product.getMinStockAlert()),
                csvExportService.toString(product.getBarcode()),
                csvExportService.toString(product.getActive()),
                product.getCreatedAt() != null ? product.getCreatedAt().format(formatter) : ""
            });

            HttpHeaders headersResponse = new HttpHeaders();
            headersResponse.setContentType(MediaType.parseMediaType("text/csv"));
            headersResponse.setContentDispositionFormData("attachment", "products.csv");

            return new ResponseEntity<>(csvData, headersResponse, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> importProducts(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Le fichier est vide"));
            }

            int count = csvImportService.importProducts(file);

            return ResponseEntity.ok(Map.of(
                "message", count + " produit(s) importé(s) avec succès",
                "count", count,
                "filename", file.getOriginalFilename()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Erreur lors de l'import: " + e.getMessage()));
        }
    }
}
