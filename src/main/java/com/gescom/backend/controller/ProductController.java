package com.gescom.backend.controller;

import com.gescom.backend.dto.product.ProductRequest;
import com.gescom.backend.dto.product.ProductResponse;
import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.Product;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.CategoryRepository;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.CsvImportService;
import com.gescom.backend.service.ProductService;
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
    private final CategoryRepository categoryRepository;
    private final CsvExportService csvExportService;
    private final CsvImportService csvImportService;

    public ProductController(ProductService productService,
                             CategoryRepository categoryRepository,
                             CsvExportService csvExportService,
                             CsvImportService csvImportService) {
        this.productService = productService;
        this.categoryRepository = categoryRepository;
        this.csvExportService = csvExportService;
        this.csvImportService = csvImportService;
    }

    private Product applyRequest(Product target, ProductRequest request) {
        if (request.code() != null && !request.code().isBlank()) {
            target.setCode(request.code());
        }
        target.setName(request.name());
        target.setDescription(request.description());
        target.setPurchasePrice(request.purchasePrice());
        target.setSellingPrice(request.sellingPrice());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie", request.categoryId()));
            target.setCategory(category);
        } else {
            target.setCategory(null);
        }

        if (request.unit() != null) target.setUnit(request.unit());
        if (request.stockQuantity() != null) target.setStockQuantity(request.stockQuantity());
        if (request.minStockAlert() != null) target.setMinStockAlert(request.minStockAlert());
        target.setBarcode(request.barcode());
        target.setImageUrl(request.imageUrl());
        if (request.active() != null) target.setActive(request.active());
        return target;
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts().stream()
                .map(ProductResponse::from).toList());
    }

    @GetMapping("/active")
    public ResponseEntity<List<ProductResponse>> getActiveProducts() {
        return ResponseEntity.ok(productService.getActiveProducts().stream()
                .map(ProductResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(ProductResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ProductResponse> getProductByCode(@PathVariable String code) {
        return productService.getProductByCode(code)
                .map(ProductResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId).stream()
                .map(ProductResponse::from).toList());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts() {
        return ResponseEntity.ok(productService.getLowStockProducts().stream()
                .map(ProductResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        Product product = applyRequest(new Product(), request);
        Product created = productService.createProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id,
                                                          @Valid @RequestBody ProductRequest request) {
        Product details = applyRequest(new Product(), request);
        return ResponseEntity.ok(ProductResponse.from(productService.updateProduct(id, details)));
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
