package com.gescom.backend.controller;

import com.gescom.backend.dto.user.UserResponse;
import com.gescom.backend.entity.*;
import com.gescom.backend.service.ActivityLogService;
import com.gescom.backend.service.ClientService;
import com.gescom.backend.service.InvoiceService;
import com.gescom.backend.service.DeliveryService;
import com.gescom.backend.service.OrderService;
import com.gescom.backend.service.ProductService;
import com.gescom.backend.service.UserService;
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
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Tableau de bord", description = "Indicateurs de l'entreprise et vues caisse")
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
    private final UserService userService;

    public DashboardController(OrderService orderService, ClientService clientService,
                               ProductService productService, InvoiceService invoiceService,
                               DeliveryService deliveryService, ActivityLogService activityLogService,
                               UserService userService) {
        this.orderService = orderService;
        this.clientService = clientService;
        this.productService = productService;
        this.invoiceService = invoiceService;
        this.deliveryService = deliveryService;
        this.activityLogService = activityLogService;
        this.userService = userService;
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
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getRecentOrders() {
        List<Order> orders = orderService.getAllOrders().stream()
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = orders.stream().map(this::mapOrder).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboardOverview() {
        logView("Consultation du tableau de bord");
        Map<String, Object> overview = new HashMap<>();

        // --- Orders ---
        List<Order> allOrders = orderService.getAllOrders();

        // Bug fix : exclure les commandes annulées du CA (elles n'ont jamais été honorées).
        // Même périmètre que `honoredOrders` (vues caisse) et que le sous-titre « N commandes
        // honorées » du tableau de bord : les trois doivent parler du même ensemble.
        // Montants nuls filtrés comme dans buildDayMetrics : un `null` hérité faisait tomber
        // tout l'aperçu en 500, donc affichait un tableau de bord vide plutôt qu'un total.
        BigDecimal totalSales = allOrders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.CANCELED)
                .map(Order::getFinalAmount)
                .filter(Objects::nonNull)
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
        //
        // Les trois montants exposés décrivent UN SEUL ensemble : les factures non annulées.
        // Une facture annulée sort des livres — compter ses encaissements sans compter son
        // reliquat (ou l'inverse) donnerait un « encaissé sur facturé » qui n'est le total
        // d'aucun périmètre, et un taux d'encaissement faux.
        //
        // L'identité pendingAmount = invoicedAmount − totalRevenue est garantie ici, par
        // construction : le tableau de bord affiche les trois chiffres côte à côte (anneau,
        // légende, « reste à encaisser ») et ils doivent tomber juste à l'euro près.
        List<Invoice> allInvoices = invoiceService.getAllInvoices();
        List<Invoice> liveInvoices = allInvoices.stream()
                .filter(inv -> inv.getStatus() != Invoice.InvoiceStatus.CANCELED)
                .collect(Collectors.toList());

        BigDecimal invoicedAmount = liveInvoices.stream()
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRevenue = liveInvoices.stream()
                .map(Invoice::getPaidAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingAmount = invoicedAmount.subtract(totalRevenue);

        long unpaidInvoices = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.UNPAID).count();
        long partiallyPaidInvoices = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.PARTIALLY_PAID).count();
        long paidInvoices = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.PAID).count();
        long canceledInvoices = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.CANCELED).count();

        overview.put("totalInvoices", allInvoices.size());
        overview.put("totalRevenue", totalRevenue);
        overview.put("invoicedAmount", invoicedAmount);
        overview.put("pendingAmount", pendingAmount);
        // Les quatre statuts sont exposés au complet : le tableau de bord en fait une
        // répartition dont les parts doivent totaliser 100 % (payées + partielles + non
        // payées + annulées = totalInvoices). Il manquait PARTIALLY_PAID et CANCELED.
        overview.put("unpaidInvoices", unpaidInvoices);
        overview.put("partiallyPaidInvoices", partiallyPaidInvoices);
        overview.put("paidInvoices", paidInvoices);
        overview.put("canceledInvoices", canceledInvoices);

        // --- Deliveries ---
        List<Delivery> allDeliveries = deliveryService.getAllDeliveries();
        long pendingDeliveries = allDeliveries.stream().filter(d -> d.getStatus() == Delivery.DeliveryStatus.PENDING).count();
        long deliveredDeliveries = allDeliveries.stream().filter(d -> d.getStatus() == Delivery.DeliveryStatus.DELIVERED).count();

        // Commandes facturées pour lesquelles aucune livraison n'existe encore : le reste à
        // planifier. Compté sur la jointure réelle, et non déduit de
        // « invoicedOrders − pendingDeliveries » : une livraison en attente peut survivre à
        // l'annulation de sa facture puis de sa commande (cancelInvoice ne vérifie pas les
        // livraisons), auquel cas la soustraction sous-compte — voire passe sous zéro.
        Set<Long> orderIdsWithDelivery = allDeliveries.stream()
                .map(Delivery::getOrder)
                .filter(Objects::nonNull)
                .map(Order::getId)
                .collect(Collectors.toSet());
        long ordersToSchedule = allOrders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.INVOICED)
                .filter(o -> !orderIdsWithDelivery.contains(o.getId()))
                .count();

        overview.put("totalDeliveries", allDeliveries.size());
        overview.put("pendingDeliveries", pendingDeliveries);
        overview.put("deliveredDeliveries", deliveredDeliveries);
        overview.put("ordersToSchedule", ordersToSchedule);

        // --- Recent orders (avec statut de facturation pour l'affichage « Payée ») ---
        List<Order> recentOrders = allOrders.stream()
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());

        Map<Long, Invoice.InvoiceStatus> recentInvoiceStatuses = invoiceService.getInvoiceStatusesByOrderIds(
                recentOrders.stream().map(Order::getId).collect(Collectors.toList()));

        overview.put("recentOrders", recentOrders.stream()
                .map(o -> {
                    Map<String, Object> data = mapOrder(o);
                    data.put("invoiceStatus", recentInvoiceStatuses.get(o.getId()));
                    return data;
                })
                .collect(Collectors.toList()));

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
        // Nombre d'articles de la commande : la supervision en fait une colonne, et le total
        // d'une ligne doit se retrouver dans `dayItemsCount`. Les lignes sont chargées par
        // jointure dans les trois requêtes qui alimentent ce mapping (findAllWithDetails,
        // findDayOrders, findDayOrdersForCashier) — pas de N+1 ni d'accès hors transaction.
        orderData.put("itemsCount", order.getItems() != null
            ? order.getItems().stream()
                .mapToInt(it -> it.getQuantity() != null ? it.getQuantity() : 0)
                .sum()
            : 0);
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

        // Commandes du jour du caissier — une seule requête, lignes et client déjà chargés
        // (remplace l'ancien chargement intégral filtré en mémoire).
        List<Order> dayOrders = orderService.getDayOrdersForCashier(currentUserId, dayStart, dayEnd);

        // Encaissé du jour : montant réellement perçu (factures soldées à cette date).
        // À distinguer de daySales (CA commandé, qui inclut des commandes non encore payées).
        BigDecimal dayCollected = invoiceService.getCollectedByCashierOnDate(currentUserId, target);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectedDate", target.toString());
        result.putAll(buildDayMetrics(dayOrders, dayCollected));
        result.put("hourlySales", buildHourlySales(honoredOrders(dayOrders)));
        result.put("dayOrders", mapDayOrders(dayOrders));

        return ResponseEntity.ok(result);
    }

    /**
     * Supervision des caisses : les mêmes indicateurs que {@link #getCashierDashboard}, mais
     * pour tous les caissiers sur la date demandée. Les deux vues partagent
     * {@link #buildDayMetrics} — c'est ce qui garantit qu'un caissier et son responsable
     * lisent les mêmes chiffres pour une même journée (notamment l'exclusion des annulées).
     */
    @GetMapping("/cashiers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCashiersDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate target = date != null ? date : LocalDate.now();
        LocalDateTime dayStart = target.atStartOfDay();
        LocalDateTime dayEnd = target.plusDays(1).atStartOfDay();

        logView("Consultation de la supervision des caisses (" + target + ")");

        List<Order> dayOrders = orderService.getDayOrders(dayStart, dayEnd);
        Map<Long, BigDecimal> collectedPerCashier = invoiceService.getCollectedPerCashierOnDate(target);

        // Statuts de facture résolus une seule fois pour toute la journée (cf. mapDayOrders).
        Map<Long, Invoice.InvoiceStatus> invoiceStatuses = invoiceService.getInvoiceStatusesByOrderIds(
                dayOrders.stream().map(Order::getId).collect(Collectors.toList()));

        Map<Long, List<Order>> ordersByCashier = dayOrders.stream()
                .filter(o -> o.getCreatedBy() != null)
                .collect(Collectors.groupingBy(o -> o.getCreatedBy().getId()));

        // Le détail part de la liste des caissiers — un caissier sans vente doit apparaître à
        // zéro plutôt que disparaître du classement — puis on y ajoute tout autre utilisateur
        // ayant passé des commandes ce jour-là (typiquement un admin en renfort). Sans cela,
        // son chiffre resterait dans les totaux sans aucune ligne pour l'expliquer.
        Map<Long, Map<String, Object>> entriesById = new LinkedHashMap<>();

        for (UserResponse caissier : userService.getCaissiers()) {
            entriesById.put(caissier.id(), buildCashierEntry(
                    caissier.id(), caissier.firstName(), caissier.lastName(), caissier.email(),
                    caissier.role(), ordersByCashier, collectedPerCashier, invoiceStatuses));
        }

        for (Order order : dayOrders) {
            User creator = order.getCreatedBy();
            if (creator == null || entriesById.containsKey(creator.getId())) {
                continue;
            }
            entriesById.put(creator.getId(), buildCashierEntry(
                    creator.getId(), creator.getFirstName(), creator.getLastName(), creator.getEmail(),
                    creator.getRole() != null ? creator.getRole().name() : null,
                    ordersByCashier, collectedPerCashier, invoiceStatuses));
        }

        List<Map<String, Object>> cashiers = entriesById.values().stream()
                .sorted(Comparator.comparing(
                        (Map<String, Object> e) -> (BigDecimal) e.get("daySales")).reversed())
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectedDate", target.toString());
        // Totaux calculés sur l'ensemble des commandes du jour, pas en resommant les lignes
        // par caissier : les commandes sans créateur (imports, reprises) restent comptées.
        result.putAll(buildDayMetrics(dayOrders, totalOf(collectedPerCashier)));
        result.put("hourlySales", buildHourlySales(honoredOrders(dayOrders)));
        result.put("activeCashiers", ordersByCashier.values().stream()
                .filter(orders -> !honoredOrders(orders).isEmpty())
                .count());
        result.put("cashiers", cashiers);

        return ResponseEntity.ok(result);
    }

    /**
     * Ligne de détail d'un opérateur de caisse. Le rôle est exposé pour que l'UI puisse
     * signaler une ligne non-caissier plutôt que de la présenter comme un caissier ordinaire.
     */
    private Map<String, Object> buildCashierEntry(Long userId, String firstName, String lastName,
                                                  String email, String role,
                                                  Map<Long, List<Order>> ordersByCashier,
                                                  Map<Long, BigDecimal> collectedPerCashier,
                                                  Map<Long, Invoice.InvoiceStatus> invoiceStatuses) {
        List<Order> orders = ordersByCashier.getOrDefault(userId, Collections.emptyList());
        BigDecimal collected = collectedPerCashier.getOrDefault(userId, BigDecimal.ZERO);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("cashierId", userId);
        entry.put("firstName", firstName);
        entry.put("lastName", lastName);
        entry.put("email", email);
        entry.put("role", role);
        entry.putAll(buildDayMetrics(orders, collected));
        entry.put("dayOrders", mapDayOrders(orders, invoiceStatuses));
        return entry;
    }

    /** Commandes honorées : les annulées n'ont jamais été encaissées, elles sortent du financier. */
    private List<Order> honoredOrders(List<Order> orders) {
        return orders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.CANCELED)
                .collect(Collectors.toList());
    }

    /**
     * Indicateurs d'une journée pour un périmètre de commandes donné (un caissier ou tous).
     * Source unique des chiffres de la caisse : toute évolution ici se propage aux deux vues.
     */
    private Map<String, Object> buildDayMetrics(List<Order> orders, BigDecimal collected) {
        List<Order> honored = honoredOrders(orders);

        BigDecimal daySales = honored.stream()
                .map(Order::getFinalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int dayItemsCount = honored.stream()
                .flatMap(o -> o.getItems() != null ? o.getItems().stream() : java.util.stream.Stream.empty())
                .mapToInt(it -> it.getQuantity() != null ? it.getQuantity() : 0)
                .sum();

        BigDecimal averageBasket = !honored.isEmpty()
                ? daySales.divide(BigDecimal.valueOf(honored.size()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("daySales", daySales);
        metrics.put("dayCollected", collected);
        metrics.put("dayOrdersCount", honored.size());
        metrics.put("dayCanceledCount", (long) (orders.size() - honored.size()));
        metrics.put("dayItemsCount", dayItemsCount);
        metrics.put("averageBasket", averageBasket);
        return metrics;
    }

    /**
     * Commandes du jour enrichies du statut de facturation, pour distinguer une commande
     * facturée mais réglée (affichée « Payée ») d'une commande seulement facturée.
     */
    private List<Map<String, Object>> mapDayOrders(List<Order> orders) {
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        return mapDayOrders(orders, invoiceService.getInvoiceStatusesByOrderIds(orderIds));
    }

    /**
     * Variante à statuts pré-chargés : la supervision résout les statuts une fois pour toute
     * la journée, au lieu d'une requête par caissier.
     */
    private List<Map<String, Object>> mapDayOrders(List<Order> orders,
                                                   Map<Long, Invoice.InvoiceStatus> invoiceStatuses) {
        return orders.stream()
                .map(o -> {
                    Map<String, Object> data = mapOrder(o);
                    data.put("invoiceStatus", invoiceStatuses.get(o.getId()));
                    return data;
                })
                .collect(Collectors.toList());
    }

    private BigDecimal totalOf(Map<Long, BigDecimal> amountsByCashier) {
        return amountsByCashier.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Ventes agrégées par heure (0–23) à partir des commandes honorées de la journée.
     * Renvoie une plage contiguë [première heure active, dernière heure active], les heures
     * creuses intermédiaires étant remplies à zéro pour un histogramme lisible. Liste vide si
     * aucune commande.
     */
    private List<Map<String, Object>> buildHourlySales(List<Order> honoredOrders) {
        if (honoredOrders.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, BigDecimal> salesByHour = new TreeMap<>();
        Map<Integer, Integer> ordersByHour = new TreeMap<>();
        for (Order o : honoredOrders) {
            if (o.getCreatedAt() == null) continue;
            int hour = o.getCreatedAt().getHour();
            BigDecimal amount = o.getFinalAmount() != null ? o.getFinalAmount() : BigDecimal.ZERO;
            salesByHour.merge(hour, amount, BigDecimal::add);
            ordersByHour.merge(hour, 1, Integer::sum);
        }

        if (salesByHour.isEmpty()) {
            return Collections.emptyList();
        }

        int minHour = Collections.min(salesByHour.keySet());
        int maxHour = Collections.max(salesByHour.keySet());

        List<Map<String, Object>> buckets = new ArrayList<>();
        for (int h = minHour; h <= maxHour; h++) {
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("hour", h);
            bucket.put("label", String.format("%02dh", h));
            bucket.put("sales", salesByHour.getOrDefault(h, BigDecimal.ZERO));
            bucket.put("orders", ordersByHour.getOrDefault(h, 0));
            buckets.add(bucket);
        }
        return buckets;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }
}
