package com.gescom.backend.controller;

import com.gescom.backend.dto.invoice.InvoiceCreateRequest;
import com.gescom.backend.dto.invoice.InvoicePaymentRequest;
import com.gescom.backend.dto.invoice.InvoiceResponse;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.SalesMapper;
import com.gescom.backend.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices().stream()
                .map(salesMapper::toResponse).toList());
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
