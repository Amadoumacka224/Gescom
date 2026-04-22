package com.gescom.backend.controller;

import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.service.StockService;
import com.gescom.backend.service.CsvExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class StockController {

    private final StockService stockService;
    private final CsvExportService csvExportService;

    public StockController(StockService stockService, CsvExportService csvExportService) {
        this.stockService = stockService;
        this.csvExportService = csvExportService;
    }

    @GetMapping("/movements")
    public ResponseEntity<List<StockMovement>> getAllMovements() {
        return ResponseEntity.ok(stockService.getAllMovements());
    }

    @GetMapping("/movements/{id}")
    public ResponseEntity<StockMovement> getMovementById(@PathVariable Long id) {
        return stockService.getMovementById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/movements/product/{productId}")
    public ResponseEntity<List<StockMovement>> getMovementsByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(stockService.getMovementsByProduct(productId));
    }

    @GetMapping("/movements/type/{type}")
    public ResponseEntity<List<StockMovement>> getMovementsByType(@PathVariable StockMovement.MovementType type) {
        return ResponseEntity.ok(stockService.getMovementsByType(type));
    }

    @GetMapping("/movements/date-range")
    public ResponseEntity<List<StockMovement>> getMovementsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(stockService.getMovementsByDateRange(start, end));
    }

    @PostMapping("/add")
    public ResponseEntity<StockMovement> addStock(@RequestBody Map<String, Object> request) {
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
    }

    @PostMapping("/remove")
    public ResponseEntity<StockMovement> removeStock(@RequestBody Map<String, Object> request) {
        Long productId = Long.valueOf(request.get("productId").toString());
        Integer quantity = Integer.valueOf(request.get("quantity").toString());
        String reason = request.containsKey("reason") ? request.get("reason").toString() : null;
        String reference = request.containsKey("reference") ? request.get("reference").toString() : null;
        Long userId = request.containsKey("userId") && request.get("userId") != null
                ? Long.valueOf(request.get("userId").toString())
                : null;

        StockMovement movement = stockService.removeStock(productId, quantity, reason, reference, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(movement);
    }

    @PostMapping("/adjust")
    public ResponseEntity<StockMovement> adjustStock(@RequestBody Map<String, Object> request) {
        Long productId = Long.valueOf(request.get("productId").toString());
        Integer newQuantity = Integer.valueOf(request.get("newQuantity").toString());
        String reason = request.containsKey("reason") ? request.get("reason").toString() : null;
        Long userId = request.containsKey("userId") && request.get("userId") != null
                ? Long.valueOf(request.get("userId").toString())
                : null;

        StockMovement movement = stockService.adjustStock(productId, newQuantity, reason, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(movement);
    }

    @PostMapping("/damage")
    public ResponseEntity<StockMovement> recordDamage(@RequestBody Map<String, Object> request) {
        Long productId = Long.valueOf(request.get("productId").toString());
        Integer quantity = Integer.valueOf(request.get("quantity").toString());
        String reason = request.containsKey("reason") ? request.get("reason").toString() : null;
        Long userId = request.containsKey("userId") && request.get("userId") != null
                ? Long.valueOf(request.get("userId").toString())
                : null;

        StockMovement movement = stockService.recordDamage(productId, quantity, reason, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(movement);
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<Product>> getLowStockProducts() {
        return ResponseEntity.ok(stockService.getLowStockProducts());
    }

    @GetMapping("/out-of-stock")
    public ResponseEntity<List<Product>> getOutOfStockProducts() {
        return ResponseEntity.ok(stockService.getOutOfStockProducts());
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStockStatistics() {
        return ResponseEntity.ok(stockService.getStockStatistics());
    }

    @DeleteMapping("/movements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteMovement(@PathVariable Long id) {
        stockService.deleteMovement(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportStockMovements() {
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
    }
}
