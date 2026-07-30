package com.gescom.backend.controller;

import com.gescom.backend.dto.invoice.InvoiceCreateRequest;
import com.gescom.backend.dto.invoice.InvoicePaymentRequest;
import com.gescom.backend.dto.invoice.InvoiceResponse;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.InvoiceMapper;
import com.gescom.backend.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceMapper invoiceMapper;

    public InvoiceController(InvoiceService invoiceService, InvoiceMapper invoiceMapper) {
        this.invoiceService = invoiceService;
        this.invoiceMapper = invoiceMapper;
    }

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices().stream()
                .map(invoiceMapper::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id)
                .map(invoiceMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", id));
    }

    @GetMapping("/number/{invoiceNumber}")
    public ResponseEntity<InvoiceResponse> getInvoiceByInvoiceNumber(@PathVariable String invoiceNumber) {
        return invoiceService.getInvoiceByInvoiceNumber(invoiceNumber)
                .map(invoiceMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", "number", invoiceNumber));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<InvoiceResponse> getInvoiceByOrder(@PathVariable Long orderId) {
        return invoiceService.getInvoiceByOrder(orderId)
                .map(invoiceMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("invoice", "order", String.valueOf(orderId)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByStatus(@PathVariable Invoice.InvoiceStatus status) {
        return ResponseEntity.ok(invoiceService.getInvoicesByStatus(status).stream()
                .map(invoiceMapper::toResponse).toList());
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(invoiceService.getInvoicesByDateRange(start, end).stream()
                .map(invoiceMapper::toResponse).toList());
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<InvoiceResponse>> getOverdueInvoices() {
        return ResponseEntity.ok(invoiceService.getOverdueInvoices().stream()
                .map(invoiceMapper::toResponse).toList());
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceCreateRequest request) {
        Invoice created = invoiceService.createInvoice(invoiceMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceMapper.toResponse(created));
    }

    @PatchMapping("/{id}/payment")
    public ResponseEntity<InvoiceResponse> recordPayment(@PathVariable Long id,
                                                          @Valid @RequestBody InvoicePaymentRequest request) {
        Invoice invoice = request.paymentDate() != null
                ? invoiceService.recordPayment(id, request.amount(), request.paymentMethod(), request.paymentDate())
                : invoiceService.recordPayment(id, request.amount(), request.paymentMethod());
        return ResponseEntity.ok(invoiceMapper.toResponse(invoice));
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
