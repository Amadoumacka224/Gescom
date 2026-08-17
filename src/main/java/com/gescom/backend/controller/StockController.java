package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.product.ProductResponse;
import com.gescom.backend.dto.stock.StockMovementRequest;
import com.gescom.backend.dto.stock.StockAdjustRequest;
import com.gescom.backend.dto.stock.StockMovementResponse;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.ReferenceMapper;
import com.gescom.backend.mapper.StockMapper;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.StockService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Stock", description = "Mouvements de stock et grand livre")
@RestController
@RequestMapping("/api/stock")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class StockController {

    private final StockService stockService;
    private final CsvExportService csvExportService;
    private final StockMapper stockMapper;
    private final ReferenceMapper referenceMapper;

    public StockController(StockService stockService, CsvExportService csvExportService,
                           StockMapper stockMapper, ReferenceMapper referenceMapper) {
        this.stockService = stockService;
        this.csvExportService = csvExportService;
        this.stockMapper = stockMapper;
        this.referenceMapper = referenceMapper;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

    // Le grand livre des mouvements est append-only : ses listes sont paginées, contrairement
    // à /export qui doit rester exhaustif.
    //
    // Sa consultation est réservée aux ADMIN, au même titre que les écritures plus bas. Un
    // mouvement porte le numéro de la commande qui l'a produit en référence : ouvert au
    // CAISSIER, le registre lui donnerait le détail des ventes de tous ses collègues, article
    // par article — exactement ce que le cloisonnement des commandes interdit par ailleurs.
    // C'est aussi ce que fait déjà le frontend, où /stock est un écran d'administrateur.
    @GetMapping("/movements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<StockMovementResponse>> getAllMovements(
            @RequestParam(required = false) StockMovement.MovementType type,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                stockService.searchMovements(type, productId, start, end, search, pageable),
                stockMapper::toResponse));
    }

    @GetMapping("/movements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockMovementResponse> getMovementById(@PathVariable Long id) {
        return stockService.getMovementById(id)
                .map(stockMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("stockMovement", id));
    }

    @GetMapping("/movements/product/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<StockMovementResponse>> getMovementsByProduct(
            @PathVariable Long productId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                stockService.getMovementsByProduct(productId, pageable), stockMapper::toResponse));
    }

    @GetMapping("/movements/type/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<StockMovementResponse>> getMovementsByType(
            @PathVariable StockMovement.MovementType type,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                stockService.getMovementsByType(type, pageable), stockMapper::toResponse));
    }

    @GetMapping("/movements/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<StockMovementResponse>> getMovementsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                stockService.getMovementsByDateRange(start, end, pageable), stockMapper::toResponse));
    }

    // Les opérations d'écriture de stock sont réservées aux ADMIN (cohérent avec
    // la sidebar qui masque /stock pour CAISSIER). Avant, ces endpoints étaient
    // ouverts à CAISSIER via la sécurité au niveau classe — défaut corrigé ici.
    @PostMapping("/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockMovementResponse> addStock(@Valid @RequestBody StockMovementRequest request) {
        StockMovement movement = stockService.addStock(
                request.productId(), request.quantity(), request.unitCost(),
                request.reason(), request.reference(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(stockMapper.toResponse(movement));
    }

    @PostMapping("/remove")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockMovementResponse> removeStock(@Valid @RequestBody StockMovementRequest request) {
        StockMovement movement = stockService.removeStock(
                request.productId(), request.quantity(),
                request.reason(), request.reference(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(stockMapper.toResponse(movement));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockMovementResponse> adjustStock(@Valid @RequestBody StockAdjustRequest request) {
        StockMovement movement = stockService.adjustStock(
                request.productId(), request.newQuantity(), request.reason(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(stockMapper.toResponse(movement));
    }

    @PostMapping("/damage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockMovementResponse> recordDamage(@Valid @RequestBody StockMovementRequest request) {
        StockMovement movement = stockService.recordDamage(
                request.productId(), request.quantity(), request.reason(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(stockMapper.toResponse(movement));
    }

    // Les retours clients ne sont plus une simple entrée de stock à saisir à la main : ils
    // partent de la vente d'origine et vivent dans StockReturnController (/api/stock/returns).

    @GetMapping("/low-stock")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts() {
        return ResponseEntity.ok(stockService.getLowStockProducts().stream()
                .map(referenceMapper::toResponse).toList());
    }

    @GetMapping("/out-of-stock")
    public ResponseEntity<List<ProductResponse>> getOutOfStockProducts() {
        return ResponseEntity.ok(stockService.getOutOfStockProducts().stream()
                .map(referenceMapper::toResponse).toList());
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
