package com.gescom.backend.controller;

import com.gescom.backend.dto.order.OrderCreateRequest;
import com.gescom.backend.dto.order.OrderItemRequest;
import com.gescom.backend.dto.order.OrderResponse;
import com.gescom.backend.dto.order.OrderUpdateRequest;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.OrderItem;
import com.gescom.backend.entity.Product;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.ClientRepository;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.OrderService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class OrderController {

    private final OrderService orderService;
    private final CsvExportService csvExportService;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;

    public OrderController(OrderService orderService,
                           CsvExportService csvExportService,
                           ClientRepository clientRepository,
                           ProductRepository productRepository) {
        this.orderService = orderService;
        this.csvExportService = csvExportService;
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
    }

    private OrderItem buildItem(OrderItemRequest req) {
        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Produit", req.productId()));
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(req.quantity());
        return item;
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders().stream()
                .map(OrderResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByOrderNumber(@PathVariable String orderNumber) {
        return orderService.getOrderByOrderNumber(orderNumber)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(orderService.getOrdersByClient(clientId).stream()
                .map(OrderResponse::from).toList());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(orderService.getOrdersByUser(userId).stream()
                .map(OrderResponse::from).toList());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<OrderResponse>> getOrdersByStatus(@PathVariable Order.OrderStatus status) {
        return ResponseEntity.ok(orderService.getOrdersByStatus(status).stream()
                .map(OrderResponse::from).toList());
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<OrderResponse>> getOrdersByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(orderService.getOrdersByDateRange(start, end).stream()
                .map(OrderResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId()));

        Order order = new Order();
        order.setClient(client);
        order.setDiscount(request.discount() != null ? request.discount() : BigDecimal.ZERO);
        order.setTax(request.tax() != null ? request.tax() : BigDecimal.ZERO);
        order.setNotes(request.notes());
        List<OrderItem> items = request.items().stream().map(this::buildItem).toList();
        order.getItems().addAll(items);

        Order created = orderService.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(@PathVariable Long id,
                                                     @Valid @RequestBody OrderUpdateRequest request) {
        Order patch = new Order();
        if (request.status() != null) {
            patch.setStatus(request.status());
        }
        if (request.discount() != null) {
            patch.setDiscount(request.discount());
        }
        if (request.tax() != null) {
            patch.setTax(request.tax());
        }
        patch.setNotes(request.notes());
        List<OrderItem> items = request.items().stream().map(this::buildItem).toList();
        patch.getItems().addAll(items);

        return ResponseEntity.ok(OrderResponse.from(orderService.updateOrder(id, patch)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable Long id,
                                                           @RequestBody Map<String, String> request) {
        Order.OrderStatus status = Order.OrderStatus.valueOf(request.get("status"));
        return ResponseEntity.ok(OrderResponse.from(orderService.updateOrderStatus(id, status)));
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
            "ID", "Order Number", "Client", "User", "Total Amount", "Discount", "Tax",
            "Final Amount", "Status", "Notes", "Created At", "Updated At"
        };

        byte[] csvData = csvExportService.exportToCsv(orders, headers, order -> new String[]{
            csvExportService.toString(order.getId()),
            csvExportService.toString(order.getOrderNumber()),
            order.getClient() != null ? order.getClient().getFirstName() + " " + order.getClient().getLastName() : "",
            order.getCreatedBy() != null ? csvExportService.toString(order.getCreatedBy().getUsername()) : "",
            csvExportService.toString(order.getTotalAmount()),
            csvExportService.toString(order.getDiscount()),
            csvExportService.toString(order.getTax()),
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
