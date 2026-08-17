package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.product.ProductCatalogSummary;
import com.gescom.backend.dto.product.ProductRequest;
import com.gescom.backend.dto.product.ProductResponse;
import com.gescom.backend.dto.product.StockUpdateRequest;
import com.gescom.backend.entity.Product;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.ReferenceMapper;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.CsvImportService;
import com.gescom.backend.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Produits", description = "Catalogue et niveaux de stock")
@RestController
@RequestMapping("/api/products")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class ProductController {

    private final ProductService productService;
    private final CsvExportService csvExportService;
    private final CsvImportService csvImportService;
    private final ReferenceMapper referenceMapper;

    public ProductController(ProductService productService,
                             CsvExportService csvExportService,
                             CsvImportService csvImportService,
                             ReferenceMapper referenceMapper) {
        this.productService = productService;
        this.csvExportService = csvExportService;
        this.csvImportService = csvImportService;
        this.referenceMapper = referenceMapper;
    }

    /**
     * Catalogue intégral, sans pagination.
     *
     * Conservé parce que plusieurs écrans s'en servent comme d'un référentiel à charger une
     * fois — la caisse, l'ajustement de stock, le décompte par catégorie — et non comme d'un
     * tableau à parcourir. Le tableau de l'écran Produits, lui, passe par {@link #searchProducts}.
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts().stream()
                .map(referenceMapper::toResponse).toList());
    }

    /**
     * Page du catalogue, filtrée et triée en base.
     *
     * Le tri vient du {@code Pageable} (?sort=name,asc) : les colonnes triables de l'écran sont
     * des champs de l'entité, il n'y a rien à traduire. Défaut sur le nom, ordre alphabétique,
     * qui est l'attendu d'un catalogue.
     */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<ProductResponse>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                productService.searchProducts(search, categoryId, active, pageable),
                referenceMapper::toResponse));
    }

    /** Compteurs d'en-tête : ils portent sur le catalogue entier, pas sur la page affichée. */
    @GetMapping("/summary")
    public ResponseEntity<ProductCatalogSummary> getCatalogSummary() {
        return ResponseEntity.ok(productService.getCatalogSummary());
    }

    @GetMapping("/active")
    public ResponseEntity<List<ProductResponse>> getActiveProducts() {
        return ResponseEntity.ok(productService.getActiveProducts().stream()
                .map(referenceMapper::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(referenceMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("product", id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ProductResponse> getProductByCode(@PathVariable String code) {
        return productService.getProductByCode(code)
                .map(referenceMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("product", "code", code));
    }

    /**
     * Recherche d'un produit par code-barres (scan lors de la saisie d'une commande).
     * Renvoie le produit même s'il est désactivé (le client distingue alors « indisponible »
     * de « inconnu ») ; 404 si aucun produit ne porte ce code-barres.
     */
    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductResponse> getProductByBarcode(@PathVariable String barcode) {
        return productService.getProductByBarcode(barcode)
                .map(referenceMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("product", "barcode", barcode));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId).stream()
                .map(referenceMapper::toResponse).toList());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts() {
        return ResponseEntity.ok(productService.getLowStockProducts().stream()
                .map(referenceMapper::toResponse).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        Product created = productService.createProduct(referenceMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceMapper.toResponse(created));
    }

    /**
     * Modification de la fiche produit. {@code stockQuantity} n'est lu qu'à la création : sur une
     * mise à jour il est ignoré (cf. {@link com.gescom.backend.service.ProductService#updateProduct}),
     * le stock ne se corrigeant que par {@code PATCH /{id}/stock} ou les opérations de {@code /stock},
     * qui laissent une trace dans le grand livre des mouvements.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id,
                                                          @Valid @RequestBody ProductRequest request) {
        Product details = referenceMapper.toEntity(request);
        return ResponseEntity.ok(referenceMapper.toResponse(productService.updateProduct(id, details)));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> updateStock(@PathVariable Long id,
                                                            @Valid @RequestBody StockUpdateRequest request) {
        productService.updateStock(id, request.quantity());
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
