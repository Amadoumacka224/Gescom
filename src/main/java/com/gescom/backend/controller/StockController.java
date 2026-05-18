package com.gescom.backend.controller;

import com.gescom.backend.dto.product.ProductResponse;
import com.gescom.backend.dto.stock.StockAddRequest;
import com.gescom.backend.dto.stock.StockAdjustRequest;
import com.gescom.backend.dto.stock.StockDamageRequest;
import com.gescom.backend.dto.stock.StockMovementResponse;
import com.gescom.backend.dto.stock.StockRemoveRequest;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.entity.User;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.StockService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

    @GetMapping("/movements")
    public ResponseEntity<List<StockMovementResponse>> getAllMovements() {
        return ResponseEntity.ok(stockService.getAllMovements().stream()
                .map(StockMovementResponse::from).toList());
    }

    @GetMapping("/movements/{id}")
    public ResponseEntity<StockMovementResponse> getMovementById(@PathVariable Long id) {
        return stockService.getMovementById(id)
                .map(StockMovementResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/movements/product/{productId}")
    public ResponseEntity<List<StockMovementResponse>> getMovementsByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(stockService.getMovementsByProduct(productId).stream()
                .map(StockMovementResponse::from).toList());
    }

    @GetMapping("/movements/type/{type}")
    public ResponseEntity<List<StockMovementResponse>> getMovementsByType(@PathVariable StockMovement.MovementType type) {
        return ResponseEntity.ok(stockService.getMovementsByType(type).stream()
                .map(StockMovementResponse::from).toList());
    }

    @GetMapping("/movements/date-range")
    public ResponseEntity<List<StockMovementResponse>> getMovementsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(stockService.getMovementsByDateRange(start, end).stream()
                .map(StockMovementResponse::from).toList());
    }

    // Les opérations d'écriture de stock sont réservées aux ADMIN (cohérent avec
    // la sidebar qui masque /stock pour CAISSIER). Avant, ces endpoints étaient
    // ouverts à CAISSIER via la sécurité au niveau classe — défaut corrigé ici.
    @PostMapping("/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockMovementResponse> addStock(@Valid @RequestBody StockAddRequest request) {
        StockMovement movement = stockService.addStock(
                request.productId(), request.quantity(), request.unitCost(),
                request.reason(), request.reference(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(StockMovementResponse.from(movement));
    }

    @PostMapping("/remove")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockMovementResponse> removeStock(@Valid @RequestBody StockRemoveRequest request) {
        StockMovement movement = stockService.removeStock(
                request.productId(), request.quantity(),
                request.reason(), request.reference(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(StockMovementResponse.from(movement));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockMovementResponse> adjustStock(@Valid @RequestBody StockAdjustRequest request) {
        StockMovement movement = stockService.adjustStock(
                request.productId(), request.newQuantity(), request.reason(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(StockMovementResponse.from(movement));
    }

    @PostMapping("/damage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockMovementResponse> recordDamage(@Valid @RequestBody StockDamageRequest request) {
        StockMovement movement = stockService.recordDamage(
                request.productId(), request.quantity(), request.reason(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(StockMovementResponse.from(movement));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts() {
        return ResponseEntity.ok(stockService.getLowStockProducts().stream()
                .map(ProductResponse::from).toList());
    }

    @GetMapping("/out-of-stock")
    public ResponseEntity<List<ProductResponse>> getOutOfStockProducts() {
        return ResponseEntity.ok(stockService.getOutOfStockProducts().stream()
                .map(ProductResponse::from).toList());
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
