package com.gescom.backend.controller;

import com.gescom.backend.entity.*;
import com.gescom.backend.service.ActivityLogService;
import com.gescom.backend.service.ClientService;
import com.gescom.backend.service.InvoiceService;
import com.gescom.backend.service.DeliveryService;
import com.gescom.backend.service.OrderService;
import com.gescom.backend.service.ProductService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

        // Bug fix : exclure les commandes annulées du CA (elles n'ont jamais été honorées).
        BigDecimal totalSales = allOrders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.CANCELED)
                .map(Order::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.PENDING).count();
        long confirmedOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.CONFIRMED).count();
        long invoicedOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.INVOICED).count();
        long deliveredOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED).count();
        long canceledOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.CANCELED).count();

        overview.put("totalSales", totalSales);
        overview.put("totalOrders", allOrders.size());
        overview.put("pendingOrders", pendingOrders);
        overview.put("confirmedOrders", confirmedOrders);
        overview.put("invoicedOrders", invoicedOrders);
        overview.put("deliveredOrders", deliveredOrders);
        overview.put("canceledOrders", canceledOrders);
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
        // Bug fix : compter explicitement DELIVERED plutôt que (total - pending), pour ne
        // pas inclure les CANCELED ni le statut legacy INVOICED.
        long deliveredDeliveries = allDeliveries.stream().filter(d -> d.getStatus() == Delivery.DeliveryStatus.DELIVERED).count();
        long canceledDeliveries = allDeliveries.stream().filter(d -> d.getStatus() == Delivery.DeliveryStatus.CANCELED).count();
        overview.put("totalDeliveries", allDeliveries.size());
        overview.put("pendingDeliveries", pendingDeliveries);
        overview.put("deliveredDeliveries", deliveredDeliveries);
        overview.put("canceledDeliveries", canceledDeliveries);

        // --- Recent orders ---
        List<Order> recentOrders = allOrders.stream()
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());

        overview.put("recentOrders", recentOrders.stream().map(this::mapOrder).collect(Collectors.toList()));

        // --- Produits avec le plus de stock (tri par stock DESC) ---
        // NB : ce n'est PAS un classement des meilleures ventes — pour cela il faudrait
        // agréger les OrderItem. Champ renommé pour ne pas induire l'UI en erreur.
        List<Product> topStockProducts = productService.getAllProducts().stream()
                .filter(p -> p.getStockQuantity() > 0)
                .sorted((p1, p2) -> Integer.compare(p2.getStockQuantity(), p1.getStockQuantity()))
                .limit(5)
                .collect(Collectors.toList());

        List<Map<String, Object>> productsList = topStockProducts.stream().map(product -> {
            Map<String, Object> productData = new HashMap<>();
            productData.put("id", product.getId());
            productData.put("name", product.getName());
            productData.put("stock", product.getStockQuantity());
            return productData;
        }).collect(Collectors.toList());

        overview.put("topStockProducts", productsList);

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

    /**
     * Vue caisse opérationnelle pour l'utilisateur connecté.
     * Toutes les métriques sont filtrées sur Order.createdBy = utilisateur courant.
     * Le paramètre date (défaut : aujourd'hui) borne les indicateurs de la journée ;
     * les blocs « à faire » (factures impayées, livraisons en attente) ne sont pas
     * bornés par date — c'est la file d'attente du caissier.
     */
    @GetMapping("/cashier")
    public ResponseEntity<Map<String, Object>> getCashierDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long currentUserId = currentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        LocalDate target = date != null ? date : LocalDate.now();
        LocalDateTime dayStart = target.atStartOfDay();
        LocalDateTime dayEnd = target.plusDays(1).atStartOfDay();

        logView("Consultation du tableau de bord caisse (" + target + ")");

        // Commandes du caissier (toutes dates) — base pour tous les filtres ci-dessous.
        List<Order> myOrders = orderService.getAllOrders().stream()
                .filter(o -> o.getCreatedBy() != null && currentUserId.equals(o.getCreatedBy().getId()))
                .collect(Collectors.toList());

        // Commandes du jour (filtre + exclusion CANCELED pour les agrégats financiers).
        List<Order> dayOrders = myOrders.stream()
                .filter(o -> o.getCreatedAt() != null
                        && !o.getCreatedAt().isBefore(dayStart)
                        && o.getCreatedAt().isBefore(dayEnd))
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .collect(Collectors.toList());

        List<Order> dayHonoredOrders = dayOrders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.CANCELED)
                .collect(Collectors.toList());

        BigDecimal daySales = dayHonoredOrders.stream()
                .map(Order::getFinalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int dayItemsCount = dayHonoredOrders.stream()
                .flatMap(o -> o.getItems() != null ? o.getItems().stream() : java.util.stream.Stream.empty())
                .mapToInt(it -> it.getQuantity() != null ? it.getQuantity() : 0)
                .sum();

        BigDecimal averageBasket = !dayHonoredOrders.isEmpty()
                ? daySales.divide(BigDecimal.valueOf(dayHonoredOrders.size()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long dayCanceledCount = dayOrders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.CANCELED)
                .count();

        // « À faire » — factures impayées sur mes commandes (toutes dates).
        Set<Long> myOrderIds = myOrders.stream().map(Order::getId).collect(Collectors.toSet());
        List<Invoice> myPendingInvoices = invoiceService.getAllInvoices().stream()
                .filter(inv -> inv.getOrder() != null && myOrderIds.contains(inv.getOrder().getId()))
                .filter(inv -> inv.getStatus() == Invoice.InvoiceStatus.UNPAID
                        || inv.getStatus() == Invoice.InvoiceStatus.PARTIALLY_PAID)
                .sorted(Comparator.comparing(Invoice::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        BigDecimal myPendingInvoicesAmount = myPendingInvoices.stream()
                .map(inv -> inv.getRemainingAmount() != null ? inv.getRemainingAmount()
                        : inv.getTotalAmount().subtract(inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // « À faire » — livraisons en attente sur mes commandes.
        List<Delivery> myPendingDeliveries = deliveryService.getAllDeliveries().stream()
                .filter(d -> d.getOrder() != null && myOrderIds.contains(d.getOrder().getId()))
                .filter(d -> d.getStatus() == Delivery.DeliveryStatus.PENDING)
                .sorted(Comparator.comparing(Delivery::getScheduledDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectedDate", target.toString());

        // Indicateurs de la journée
        result.put("daySales", daySales);
        result.put("dayOrdersCount", dayHonoredOrders.size());
        result.put("dayCanceledCount", dayCanceledCount);
        result.put("dayItemsCount", dayItemsCount);
        result.put("averageBasket", averageBasket);

        // À faire
        result.put("pendingInvoicesCount", myPendingInvoices.size());
        result.put("pendingInvoicesAmount", myPendingInvoicesAmount);
        result.put("pendingDeliveriesCount", myPendingDeliveries.size());

        // Listes
        result.put("dayOrders", dayOrders.stream().map(this::mapOrder).collect(Collectors.toList()));
        result.put("pendingInvoices", myPendingInvoices.stream().limit(5).map(this::mapInvoice).collect(Collectors.toList()));
        result.put("pendingDeliveries", myPendingDeliveries.stream().limit(5).map(this::mapDelivery).collect(Collectors.toList()));

        return ResponseEntity.ok(result);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }

    private Map<String, Object> mapInvoice(Invoice invoice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", invoice.getId());
        data.put("invoiceNumber", invoice.getInvoiceNumber());
        data.put("orderNumber", invoice.getOrder() != null ? invoice.getOrder().getOrderNumber() : null);
        data.put("clientName", invoice.getOrder() != null && invoice.getOrder().getClient() != null
                ? invoice.getOrder().getClient().getFirstName() + " " + invoice.getOrder().getClient().getLastName()
                : "N/A");
        data.put("totalAmount", invoice.getTotalAmount());
        data.put("remainingAmount", invoice.getRemainingAmount());
        data.put("dueDate", invoice.getDueDate());
        data.put("status", invoice.getStatus());
        return data;
    }

    private Map<String, Object> mapDelivery(Delivery delivery) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", delivery.getId());
        data.put("deliveryNumber", delivery.getDeliveryNumber());
        data.put("orderNumber", delivery.getOrder() != null ? delivery.getOrder().getOrderNumber() : null);
        data.put("clientName", delivery.getOrder() != null && delivery.getOrder().getClient() != null
                ? delivery.getOrder().getClient().getFirstName() + " " + delivery.getOrder().getClient().getLastName()
                : "N/A");
        data.put("contactName", delivery.getContactName());
        data.put("scheduledDate", delivery.getScheduledDate());
        data.put("status", delivery.getStatus());
        return data;
    }
}
