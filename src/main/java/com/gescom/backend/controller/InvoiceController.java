package com.gescom.backend.controller;

import com.gescom.backend.dto.invoice.InvoiceCreateRequest;
import com.gescom.backend.dto.invoice.InvoicePaymentRequest;
import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.invoice.InvoiceResponse;
import com.gescom.backend.dto.invoice.InvoiceSearchCriteria;
import com.gescom.backend.dto.invoice.InvoiceSummary;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.SalesMapper;
import com.gescom.backend.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Factures", description = "Facturation et encaissements")
@RestController
@RequestMapping("/api/invoices")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final SalesMapper salesMapper;

    public InvoiceController(InvoiceService invoiceService, SalesMapper salesMapper) {
        this.invoiceService = invoiceService;
        this.salesMapper = salesMapper;
    }

    /**
     * Toutes les factures du périmètre, sans pagination.
     *
     * Conservé pour les écrans qui s'en servent de référentiel — le tableau de bord y puise ses
     * agrégats. Le tableau de l'écran Factures, lui, passe par {@link #searchInvoices}.
     */
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices().stream()
                .map(salesMapper::toResponse).toList());
    }

    /**
     * Page de factures, filtrée et triée en base.
     *
     * {@code overdue} est un critère à part et non un statut : c'est une échéance dépassée sur
     * une facture ni soldée ni annulée. L'écran le présente pourtant dans la même liste que les
     * statuts, d'où ce paramètre distinct.
     */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<InvoiceResponse>> searchInvoices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Invoice.InvoiceStatus status,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Invoice.PaymentMethod paymentMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedTo,
            @RequestParam(required = false) BigDecimal amountMin,
            @RequestParam(required = false) BigDecimal amountMax,
            @RequestParam(defaultValue = "false") boolean onlyRemaining,
            @PageableDefault(size = 50, sort = "invoiceDate", direction = Sort.Direction.DESC) Pageable pageable) {

        InvoiceSearchCriteria criteria = new InvoiceSearchCriteria(
                search, status, overdue, clientId, paymentMethod,
                issuedFrom, issuedTo, amountMin, amountMax, onlyRemaining);

        return ResponseEntity.ok(PageResponse.of(
                invoiceService.searchInvoices(criteria, pageable), salesMapper::toResponse));
    }

    /** Compteurs d'en-tête : ils portent sur tout le périmètre, pas sur la page affichée. */
    @GetMapping("/summary")
    public ResponseEntity<InvoiceSummary> getSummary() {
        return ResponseEntity.ok(invoiceService.getSummary());
    }

    /** Clients proposés par le filtre : ceux qui ont réellement une facture. */
    @GetMapping("/filter-options")
    public ResponseEntity<InvoiceSummary.FilterOptions> getFilterOptions() {
        return ResponseEntity.ok(invoiceService.getFilterOptions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id)
                .map(salesMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", id));
    }

    @GetMapping("/number/{invoiceNumber}")
    public ResponseEntity<InvoiceResponse> getInvoiceByInvoiceNumber(@PathVariable String invoiceNumber) {
        return invoiceService.getInvoiceByInvoiceNumber(invoiceNumber)
                .map(salesMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", "number", invoiceNumber));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<InvoiceResponse> getInvoiceByOrder(@PathVariable Long orderId) {
        return invoiceService.getInvoiceByOrder(orderId)
                .map(salesMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", "order", String.valueOf(orderId)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByStatus(@PathVariable Invoice.InvoiceStatus status) {
        return ResponseEntity.ok(invoiceService.getInvoicesByStatus(status).stream()
                .map(salesMapper::toResponse).toList());
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(invoiceService.getInvoicesByDateRange(start, end).stream()
                .map(salesMapper::toResponse).toList());
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<InvoiceResponse>> getOverdueInvoices() {
        return ResponseEntity.ok(invoiceService.getOverdueInvoices().stream()
                .map(salesMapper::toResponse).toList());
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceCreateRequest request) {
        Invoice created = invoiceService.createInvoice(salesMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(salesMapper.toResponse(created));
    }

    @PatchMapping("/{id}/payment")
    public ResponseEntity<InvoiceResponse> recordPayment(@PathVariable Long id,
                                                          @Valid @RequestBody InvoicePaymentRequest request) {
        Invoice invoice = request.paymentDate() != null
                ? invoiceService.recordPayment(id, request.amount(), request.paymentMethod(), request.paymentDate())
                : invoiceService.recordPayment(id, request.amount(), request.paymentMethod());
        return ResponseEntity.ok(salesMapper.toResponse(invoice));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelInvoice(@PathVariable Long id) {
        invoiceService.cancelInvoice(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInvoice(@PathVariable Long id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }
}
