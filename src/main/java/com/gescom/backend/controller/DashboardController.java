package com.gescom.backend.controller;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.ClientRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class DashboardController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ActivityLogService activityLogService;

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

        // Total des ventes (somme de tous les finalAmount)
        List<Order> allOrders = orderRepository.findAll();
        BigDecimal totalSales = allOrders.stream()
                .map(Order::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Total des commandes
        long totalOrders = allOrders.size();

        // Total des clients actifs
        long totalClients = clientRepository.findByActiveTrue().size();

        // Produits en stock faible
        long lowStock = productRepository.findByStockQuantityLessThanMinStockAlert().size();

        stats.put("totalSales", totalSales);
        stats.put("totalOrders", totalOrders);
        stats.put("totalClients", totalClients);
        stats.put("lowStock", lowStock);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/recent-orders")
    public ResponseEntity<List<Map<String, Object>>> getRecentOrders() {
        List<Order> orders = orderRepository.findAll().stream()
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = orders.stream().map(order -> {
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
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<Map<String, Object>>> getTopProducts() {
        // Récupérer tous les produits et trier par stock (simulation de ventes)
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .sorted((p1, p2) -> Integer.compare(p2.getStockQuantity(), p1.getStockQuantity()))
                .limit(4)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = products.stream().map(product -> {
            Map<String, Object> productData = new HashMap<>();
            productData.put("id", product.getId());
            productData.put("name", product.getName());
            productData.put("sales", product.getStockQuantity()); // Simulation
            return productData;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getDashboardOverview() {
        logView("Consultation du tableau de bord");
        Map<String, Object> overview = new HashMap<>();

        // Stats
        List<Order> allOrders = orderRepository.findAll();
        BigDecimal totalSales = allOrders.stream()
                .map(Order::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        overview.put("totalSales", totalSales);
        overview.put("totalOrders", allOrders.size());
        overview.put("totalClients", clientRepository.findByActiveTrue().size());
        overview.put("lowStock", productRepository.findByStockQuantityLessThanMinStockAlert().size());

        // Recent Orders
        List<Order> recentOrders = allOrders.stream()
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());

        List<Map<String, Object>> ordersList = recentOrders.stream().map(order -> {
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
        }).collect(Collectors.toList());

        overview.put("recentOrders", ordersList);

        // Top Products
        List<Product> topProducts = productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .sorted((p1, p2) -> Integer.compare(p2.getStockQuantity(), p1.getStockQuantity()))
                .limit(4)
                .collect(Collectors.toList());

        List<Map<String, Object>> productsList = topProducts.stream().map(product -> {
            Map<String, Object> productData = new HashMap<>();
            productData.put("id", product.getId());
            productData.put("name", product.getName());
            productData.put("sales", product.getStockQuantity());
            return productData;
        }).collect(Collectors.toList());

        overview.put("topProducts", productsList);

        return ResponseEntity.ok(overview);
    }
}
