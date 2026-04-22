package com.gescom.backend.controller;

import com.gescom.backend.entity.Product;
import com.gescom.backend.service.ProductService;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.CsvImportService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/products")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class ProductController {

    private final ProductService productService;
    private final CsvExportService csvExportService;
    private final CsvImportService csvImportService;

    public ProductController(ProductService productService, CsvExportService csvExportService,
                             CsvImportService csvImportService) {
        this.productService = productService;
        this.csvExportService = csvExportService;
        this.csvImportService = csvImportService;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/active")
    public ResponseEntity<List<Product>> getActiveProducts() {
        return ResponseEntity.ok(productService.getActiveProducts());
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
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<Product>> getLowStockProducts() {
        return ResponseEntity.ok(productService.getLowStockProducts());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> createProduct(@Valid @RequestBody Product product) {
        Product createdProduct = productService.createProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @Valid @RequestBody Product product) {
        return ResponseEntity.ok(productService.updateProduct(id, product));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> updateStock(@PathVariable Long id, @RequestBody Map<String, Integer> request) {
        Integer quantity = request.get("quantity");
        productService.updateStock(id, quantity);
        return ResponseEntity.ok(Map.of("message", "Stock mis à jour avec succès"));
    }

    @GetMapping("/{id}/check-stock/{quantity}")
    public ResponseEntity<Boolean> checkStock(@PathVariable Long id, @PathVariable Integer quantity) {
        return ResponseEntity.ok(productService.checkStock(id, quantity));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportProducts() {
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
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> importProducts(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le fichier est vide"));
        }

        int count = csvImportService.importProducts(file);

        return ResponseEntity.ok(Map.of(
            "message", count + " produit(s) importé(s) avec succès",
            "count", count,
            "filename", file.getOriginalFilename()
        ));
    }
}
