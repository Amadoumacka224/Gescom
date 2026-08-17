package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.stock.ReturnLookupResponse;
import com.gescom.backend.dto.stock.StockReturnRequest;
import com.gescom.backend.dto.stock.StockReturnResponse;
import com.gescom.backend.entity.StockReturn;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.StockMapper;
import com.gescom.backend.service.StockReturnService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Retours clients. Comme les autres écritures de stock, l'enregistrement est réservé aux ADMIN
 * (la barre latérale masque déjà /stock au CAISSIER) ; la consultation du registre suit la
 * sécurité de classe.
 */
@Tag(name = "Retours clients", description = "Retours a partir d'une vente existante")
@RestController
@RequestMapping("/api/stock/returns")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class StockReturnController {

    private final StockReturnService stockReturnService;
    private final StockMapper stockMapper;

    public StockReturnController(StockReturnService stockReturnService, StockMapper stockMapper) {
        this.stockReturnService = stockReturnService;
        this.stockMapper = stockMapper;
    }

    /**
     * Retrouve la vente correspondant à un numéro de commande ou de facture, avec ses lignes et
     * la quantité encore retournable de chacune. Point d'entrée du formulaire de retour.
     */
    @GetMapping("/lookup")
    public ResponseEntity<ReturnLookupResponse> lookup(@RequestParam String reference) {
        return ResponseEntity.ok(stockReturnService.lookup(reference));
    }

    // Registre des retours : append-only, donc paginé, filtré et trié côté serveur.
    @GetMapping
    public ResponseEntity<PageResponse<StockReturnResponse>> getReturns(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                stockReturnService.searchReturns(orderId, start, end, search, pageable),
                stockMapper::toReturnSummary));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockReturnResponse> getReturnById(@PathVariable Long id) {
        return stockReturnService.getReturnById(id)
                .map(stockMapper::toReturnResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("stockReturn", id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockReturnResponse> createReturn(@Valid @RequestBody StockReturnRequest request) {
        StockReturn stockReturn = stockReturnService.createReturn(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockMapper.toReturnResponse(stockReturn));
    }
}
