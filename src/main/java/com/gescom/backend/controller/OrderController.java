package com.gescom.backend.controller;

import com.gescom.backend.dto.order.OrderCreateRequest;
import com.gescom.backend.dto.order.OrderResponse;
import com.gescom.backend.dto.order.OrderStatusUpdateRequest;
import com.gescom.backend.dto.order.OrderUpdateRequest;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.OrderMapper;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.InvoiceService;
import com.gescom.backend.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class OrderController {

    private final OrderService orderService;
    private final CsvExportService csvExportService;
    private final OrderMapper orderMapper;
    private final InvoiceService invoiceService;

    public OrderController(OrderService orderService,
                           CsvExportService csvExportService,
                           OrderMapper orderMapper,
                           InvoiceService invoiceService) {
        this.orderService = orderService;
        this.csvExportService = csvExportService;
        this.orderMapper = orderMapper;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        // Statut de facturation par commande (1 requête groupée) pour afficher « Payée » dans la liste.
        Map<Long, Invoice.InvoiceStatus> invoiceStatuses = invoiceService.getInvoiceStatusesByOrderIds(
                orders.stream().map(Order::getId).toList());
        return ResponseEntity.ok(orders.stream()
                .map(o -> orderMapper.toResponse(o, invoiceStatuses.get(o.getId()))).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(orderMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("order", id));
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByOrderNumber(@PathVariable String orderNumber) {
        return orderService.getOrderByOrderNumber(orderNumber)
                .map(orderMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("order", "number", orderNumber));
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByClient(@PathVariable Long clientId) {
        List<Order> orders = orderService.getOrdersByClient(clientId);
        // Même résolution que la liste générale : sans le statut de facture, une commande réglée
        // s'afficherait « Facturée » sur la fiche client et « Payée » dans la liste des commandes.
        Map<Long, Invoice.InvoiceStatus> invoiceStatuses = invoiceService.getInvoiceStatusesByOrderIds(
                orders.stream().map(Order::getId).toList());
        return ResponseEntity.ok(orders.stream()
                .map(o -> orderMapper.toResponse(o, invoiceStatuses.get(o.getId()))).toList());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(orderService.getOrdersByUser(userId).stream()
                .map(orderMapper::toResponse).toList());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<OrderResponse>> getOrdersByStatus(@PathVariable Order.OrderStatus status) {
        return ResponseEntity.ok(orderService.getOrdersByStatus(status).stream()
                .map(orderMapper::toResponse).toList());
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<OrderResponse>> getOrdersByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(orderService.getOrdersByDateRange(start, end).stream()
                .map(orderMapper::toResponse).toList());
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        Order created = orderService.createOrder(orderMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(@PathVariable Long id,
                                                     @Valid @RequestBody OrderUpdateRequest request) {
        Order patch = orderMapper.toUpdate(request);
        return ResponseEntity.ok(orderMapper.toResponse(orderService.updateOrder(id, patch)));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderMapper.toResponse(orderService.confirmOrder(id)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable Long id,
                                                           @Valid @RequestBody OrderStatusUpdateRequest request) {
        Order.OrderStatus status = parseStatus(request.status());
        return ResponseEntity.ok(orderMapper.toResponse(orderService.updateOrderStatus(id, status)));
    }

    /** Convertit la valeur reçue en statut, en renvoyant une erreur métier 400 claire si invalide. */
    private Order.OrderStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BusinessException.of("status.target.required", "Le statut cible est obligatoire");
        }
        try {
            return Order.OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.of("status.unknown", "Statut inconnu : " + raw, raw);
        }
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportOrders() {
        List<Order> orders = orderService.getAllOrders();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String[] headers = {
            "ID", "Order Number", "Client", "User", "Total Amount", "Discount",
            "Final Amount", "Status", "Notes", "Created At", "Updated At"
        };

        byte[] csvData = csvExportService.exportToCsv(orders, headers, order -> new String[]{
            csvExportService.toString(order.getId()),
            csvExportService.toString(order.getOrderNumber()),
            order.getClient() != null ? order.getClient().getFirstName() + " " + order.getClient().getLastName() : "",
            order.getCreatedBy() != null ? csvExportService.toString(order.getCreatedBy().getUsername()) : "",
            csvExportService.toString(order.getTotalAmount()),
            csvExportService.toString(order.getDiscount()),
            csvExportService.toString(order.getFinalAmount()),
            csvExportService.toString(order.getStatus()),
            csvExportService.toString(order.getNotes()),
            order.getCreatedAt() != null ? order.getCreatedAt().format(formatter) : "",
            order.getUpdatedAt() != null ? order.getUpdatedAt().format(formatter) : ""
        });

        HttpHeaders headersResponse = new HttpHeaders();
        headersResponse.setContentType(MediaType.parseMediaType("text/csv"));
        headersResponse.setContentDispositionFormData("attachment", "orders.csv");

        return new ResponseEntity<>(csvData, headersResponse, HttpStatus.OK);
    }
}
