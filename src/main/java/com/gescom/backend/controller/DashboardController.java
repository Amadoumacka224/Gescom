package com.gescom.backend.controller;

import com.gescom.backend.entity.*;
import com.gescom.backend.service.ActivityLogService;
import com.gescom.backend.service.ClientService;
import com.gescom.backend.service.InvoiceService;
import com.gescom.backend.service.DeliveryService;
import com.gescom.backend.service.OrderService;
import com.gescom.backend.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class DashboardController {

    private final OrderService orderService;
    private final ClientService clientService;
    private final ProductService productService;
    private final InvoiceService invoiceService;
    private final DeliveryService deliveryService;
    private final ActivityLogService activityLogService;

    public DashboardController(OrderService orderService, ClientService clientService,
                               ProductService productService, InvoiceService invoiceService,
                               DeliveryService deliveryService, ActivityLogService activityLogService) {
        this.orderService = orderService;
        this.clientService = clientService;
        this.productService = productService;
        this.invoiceService = invoiceService;
        this.deliveryService = deliveryService;
        this.activityLogService = activityLogService;
    }

    private void logView(String description) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User) {
                Long userId = ((User) auth.getPrincipal()).getId();
                activityLogService.logActivity(userId, ActivityLog.ActionType.VIEW, "Dashboard", null, description, null, null);
            }
        } catch (Exception e) {
            // Don't fail if logging fails
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        List<Order> allOrders = orderService.getAllOrders();
        BigDecimal totalSales = allOrders.stream()
                .map(Order::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        stats.put("totalSales", totalSales);
        stats.put("totalOrders", allOrders.size());
        stats.put("totalClients", clientService.getActiveClients().size());
        stats.put("lowStock", productService.getLowStockProducts().size());

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/recent-orders")
    public ResponseEntity<List<Map<String, Object>>> getRecentOrders() {
        List<Order> orders = orderService.getAllOrders().stream()
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = orders.stream().map(this::mapOrder).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<Map<String, Object>>> getTopProducts() {
        List<Product> products = productService.getAllProducts().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .sorted((p1, p2) -> Integer.compare(p2.getStockQuantity(), p1.getStockQuantity()))
                .limit(4)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = products.stream().map(product -> {
            Map<String, Object> productData = new HashMap<>();
            productData.put("id", product.getId());
            productData.put("name", product.getName());
            productData.put("stock", product.getStockQuantity());
            return productData;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getDashboardOverview() {
        logView("Consultation du tableau de bord");
        Map<String, Object> overview = new HashMap<>();

        // --- Orders ---
        List<Order> allOrders = orderService.getAllOrders();
        BigDecimal totalSales = allOrders.stream()
                .map(Order::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.PENDING).count();
        long confirmedOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.CONFIRMED).count();
        long completedOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.COMPLETED).count();

        overview.put("totalSales", totalSales);
        overview.put("totalOrders", allOrders.size());
        overview.put("pendingOrders", pendingOrders);
        overview.put("confirmedOrders", confirmedOrders);
        overview.put("completedOrders", completedOrders);
        overview.put("totalClients", clientService.getActiveClients().size());
        overview.put("lowStock", productService.getLowStockProducts().size());

        // --- Invoices ---
        List<Invoice> allInvoices = invoiceService.getAllInvoices();
        BigDecimal totalRevenue = allInvoices.stream()
                .map(Invoice::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingAmount = allInvoices.stream()
                .map(inv -> inv.getTotalAmount().subtract(inv.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long unpaidInvoices = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.UNPAID).count();
        long paidInvoices = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.PAID).count();

        overview.put("totalInvoices", allInvoices.size());
        overview.put("totalRevenue", totalRevenue);
        overview.put("pendingAmount", pendingAmount);
        overview.put("unpaidInvoices", unpaidInvoices);
        overview.put("paidInvoices", paidInvoices);

        // --- Deliveries ---
        List<Delivery> allDeliveries = deliveryService.getAllDeliveries();
        long pendingDeliveries = allDeliveries.stream().filter(d -> d.getStatus() == Delivery.DeliveryStatus.PENDING).count();
        long inTransitDeliveries = allDeliveries.stream().filter(d -> d.getStatus() == Delivery.DeliveryStatus.IN_TRANSIT).count();

        overview.put("totalDeliveries", allDeliveries.size());
        overview.put("pendingDeliveries", pendingDeliveries);
        overview.put("inTransitDeliveries", inTransitDeliveries);

        // --- Recent orders ---
        List<Order> recentOrders = allOrders.stream()
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());

        overview.put("recentOrders", recentOrders.stream().map(this::mapOrder).collect(Collectors.toList()));

        // --- Top products (by stock) ---
        List<Product> topProducts = productService.getAllProducts().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .sorted((p1, p2) -> Integer.compare(p2.getStockQuantity(), p1.getStockQuantity()))
                .limit(5)
                .collect(Collectors.toList());

        List<Map<String, Object>> productsList = topProducts.stream().map(product -> {
            Map<String, Object> productData = new HashMap<>();
            productData.put("id", product.getId());
            productData.put("name", product.getName());
            productData.put("stock", product.getStockQuantity());
            return productData;
        }).collect(Collectors.toList());

        overview.put("topProducts", productsList);

        // --- Low stock products ---
        List<Product> lowStockProducts = productService.getLowStockProducts().stream()
                .sorted(Comparator.comparingInt(Product::getStockQuantity))
                .limit(5)
                .collect(Collectors.toList());

        List<Map<String, Object>> lowStockList = lowStockProducts.stream().map(product -> {
            Map<String, Object> productData = new HashMap<>();
            productData.put("id", product.getId());
            productData.put("name", product.getName());
            productData.put("stock", product.getStockQuantity());
            return productData;
        }).collect(Collectors.toList());

        overview.put("lowStockProducts", lowStockList);

        return ResponseEntity.ok(overview);
    }

    private Map<String, Object> mapOrder(Order order) {
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("id", order.getId());
        orderData.put("orderNumber", order.getOrderNumber());
        orderData.put("clientName", order.getClient() != null
            ? order.getClient().getFirstName() + " " + order.getClient().getLastName()
            : "N/A");
        orderData.put("finalAmount", order.getFinalAmount());
        orderData.put("status", order.getStatus());
        orderData.put("createdAt", order.getCreatedAt());
        return orderData;
    }
}
