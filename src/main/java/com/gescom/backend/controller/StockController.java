package com.gescom.backend.controller;

import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.service.StockService;
import com.gescom.backend.service.CsvExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/stock")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class StockController {

    @Autowired
    private StockService stockService;

    @Autowired
    private CsvExportService csvExportService;

    @GetMapping("/movements")
    public ResponseEntity<List<StockMovement>> getAllMovements() {
        List<StockMovement> movements = stockService.getAllMovements();
        return ResponseEntity.ok(movements);
    }

    @GetMapping("/movements/{id}")
    public ResponseEntity<StockMovement> getMovementById(@PathVariable Long id) {
        return stockService.getMovementById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/movements/product/{productId}")
    public ResponseEntity<List<StockMovement>> getMovementsByProduct(@PathVariable Long productId) {
        List<StockMovement> movements = stockService.getMovementsByProduct(productId);
        return ResponseEntity.ok(movements);
    }

    @GetMapping("/movements/type/{type}")
    public ResponseEntity<List<StockMovement>> getMovementsByType(@PathVariable StockMovement.MovementType type) {
        List<StockMovement> movements = stockService.getMovementsByType(type);
        return ResponseEntity.ok(movements);
    }

    @GetMapping("/movements/date-range")
    public ResponseEntity<List<StockMovement>> getMovementsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<StockMovement> movements = stockService.getMovementsByDateRange(start, end);
        return ResponseEntity.ok(movements);
    }

    @PostMapping("/add")
    public ResponseEntity<?> addStock(@RequestBody Map<String, Object> request) {
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Integer quantity = Integer.valueOf(request.get("quantity").toString());
            BigDecimal unitCost = request.containsKey("unitCost") && request.get("unitCost") != null
                    ? new BigDecimal(request.get("unitCost").toString())
                    : null;
            String reason = request.containsKey("reason") ? request.get("reason").toString() : null;
            String reference = request.containsKey("reference") ? request.get("reference").toString() : null;
            Long userId = request.containsKey("userId") && request.get("userId") != null
                    ? Long.valueOf(request.get("userId").toString())
                    : null;

            StockMovement movement = stockService.addStock(productId, quantity, unitCost, reason, reference, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(movement);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/remove")
    public ResponseEntity<?> removeStock(@RequestBody Map<String, Object> request) {
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Integer quantity = Integer.valueOf(request.get("quantity").toString());
            String reason = request.containsKey("reason") ? request.get("reason").toString() : null;
            String reference = request.containsKey("reference") ? request.get("reference").toString() : null;
            Long userId = request.containsKey("userId") && request.get("userId") != null
                    ? Long.valueOf(request.get("userId").toString())
                    : null;

            StockMovement movement = stockService.removeStock(productId, quantity, reason, reference, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(movement);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/adjust")
    public ResponseEntity<?> adjustStock(@RequestBody Map<String, Object> request) {
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Integer newQuantity = Integer.valueOf(request.get("newQuantity").toString());
            String reason = request.containsKey("reason") ? request.get("reason").toString() : null;
            Long userId = request.containsKey("userId") && request.get("userId") != null
                    ? Long.valueOf(request.get("userId").toString())
                    : null;

            StockMovement movement = stockService.adjustStock(productId, newQuantity, reason, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(movement);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/damage")
    public ResponseEntity<?> recordDamage(@RequestBody Map<String, Object> request) {
        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Integer quantity = Integer.valueOf(request.get("quantity").toString());
            String reason = request.containsKey("reason") ? request.get("reason").toString() : null;
            Long userId = request.containsKey("userId") && request.get("userId") != null
                    ? Long.valueOf(request.get("userId").toString())
                    : null;

            StockMovement movement = stockService.recordDamage(productId, quantity, reason, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(movement);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<Product>> getLowStockProducts() {
        List<Product> products = stockService.getLowStockProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/out-of-stock")
    public ResponseEntity<List<Product>> getOutOfStockProducts() {
        List<Product> products = stockService.getOutOfStockProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStockStatistics() {
        Map<String, Object> stats = stockService.getStockStatistics();
        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/movements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteMovement(@PathVariable Long id) {
        try {
            stockService.deleteMovement(id);
            return ResponseEntity.ok().body("Stock movement deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportStockMovements() {
        try {
            List<StockMovement> movements = stockService.getAllMovements();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            String[] headers = {
                "ID", "Product", "Movement Type", "Quantity", "Previous Stock", "New Stock",
                "Unit Cost", "Reference", "Reason", "User", "Created At"
            };

            byte[] csvData = csvExportService.exportToCsv(movements, headers, movement -> new String[]{
                csvExportService.toString(movement.getId()),
                movement.getProduct() != null ? csvExportService.toString(movement.getProduct().getName()) : "",
                csvExportService.toString(movement.getType()),
                csvExportService.toString(movement.getQuantity()),
                csvExportService.toString(movement.getPreviousStock()),
                csvExportService.toString(movement.getNewStock()),
                csvExportService.toString(movement.getUnitCost()),
                csvExportService.toString(movement.getReference()),
                csvExportService.toString(movement.getReason()),
                movement.getUser() != null ? csvExportService.toString(movement.getUser().getUsername()) : "",
                movement.getCreatedAt() != null ? movement.getCreatedAt().format(formatter) : ""
            });

            HttpHeaders headersResponse = new HttpHeaders();
            headersResponse.setContentType(MediaType.parseMediaType("text/csv"));
            headersResponse.setContentDispositionFormData("attachment", "stock_movements.csv");

            return new ResponseEntity<>(csvData, headersResponse, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> importStockMovements(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Le fichier est vide"));
            }

            return ResponseEntity.ok(Map.of(
                "message", "Endpoint d'import disponible. Implémentation complète à venir.",
                "filename", file.getOriginalFilename(),
                "size", file.getSize()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Erreur lors de l'import: " + e.getMessage()));
        }
    }
}
