package com.gescom.backend.controller;

import com.gescom.backend.dto.dashboard.DashboardOverview;
import com.gescom.backend.dto.user.UserResponse;
import com.gescom.backend.entity.*;
import com.gescom.backend.service.ActivityLogService;
import com.gescom.backend.service.ClientService;
import com.gescom.backend.service.DashboardService;
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

    private final DashboardService dashboardService;
    private final OrderService orderService;
    private final InvoiceService invoiceService;
    private final ActivityLogService activityLogService;
    private final UserService userService;

    public DashboardController(DashboardService dashboardService, OrderService orderService,
                               InvoiceService invoiceService, ActivityLogService activityLogService,
                               UserService userService) {
        this.dashboardService = dashboardService;
        this.orderService = orderService;
        this.invoiceService = invoiceService;
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
            // On ne fait pas échouer la requête si le log échoue
        }
    }

    /**
     * Données du tableau de bord.
     *
     * Le contrôleur ne fait presque plus rien : les calculs sont dans
     * {@link com.gescom.backend.service.DashboardService}, faits en base. Avant, il y avait
     * plus de cent cinquante lignes ici, et on chargeait en mémoire toutes les commandes,
     * factures, livraisons et produits de l'entreprise à chaque affichage.
     *
     * Trois endpoints ont été supprimés au passage (/stats, /recent-orders et /top-products) :
     * aucun écran ne les appelait et l'aperçu renvoyait déjà les mêmes infos.
     */
    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DashboardOverview> getDashboardOverview() {
        logView("Consultation du tableau de bord");
        return ResponseEntity.ok(dashboardService.getOverview());
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
        // Nombre d'articles de la commande : c'est une colonne du suivi des caisses, et le
        // total doit correspondre à `dayItemsCount`. Les lignes sont chargées avec un join
        // dans les trois requêtes utilisées ici (findAllWithDetails, findDayOrders,
        // findDayOrdersForCashier), donc pas de N+1 ni d'accès hors transaction.
        orderData.put("itemsCount", order.getItems() != null
            ? order.getItems().stream()
                .mapToInt(it -> it.getQuantity() != null ? it.getQuantity() : 0)
                .sum()
            : 0);
        return orderData;
    }

    /**
     * Vue caisse pour l'utilisateur connecté.
     * Toutes les stats sont filtrées sur Order.createdBy = utilisateur connecté.
     * Le paramètre date (par défaut aujourd'hui) limite les stats à la journée ; les listes
     * « à faire » (factures impayées, livraisons en attente) ne sont pas limitées par date,
     * c'est ce qui reste à traiter pour le caissier.
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

        // Commandes du jour du caissier, en une seule requête avec les lignes et le client
        // (avant on chargeait tout et on filtrait en mémoire).
        List<Order> dayOrders = orderService.getDayOrdersForCashier(currentUserId, dayStart, dayEnd);

        // Encaissé du jour : l'argent vraiment reçu (factures payées ce jour-là).
        // Différent de daySales (montant des commandes, y compris celles pas encore payées).
        BigDecimal dayCollected = invoiceService.getCollectedByCashierOnDate(currentUserId, target);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectedDate", target.toString());
        result.putAll(buildDayMetrics(dayOrders, dayCollected));
        result.put("hourlySales", buildHourlySales(honoredOrders(dayOrders)));
        result.put("dayOrders", mapDayOrders(dayOrders));

        return ResponseEntity.ok(result);
    }

    /**
     * Suivi des caisses : les mêmes stats que {@link #getCashierDashboard}, mais pour tous
     * les caissiers à la date demandée. Les deux utilisent {@link #buildDayMetrics}, comme ça
     * un caissier et son responsable voient les mêmes chiffres pour la même journée (en
     * particulier, les commandes annulées sont exclues des deux côtés).
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

        // Statuts de facture récupérés une seule fois pour toute la journée (voir mapDayOrders).
        Map<Long, Invoice.InvoiceStatus> invoiceStatuses = invoiceService.getInvoiceStatusesByOrderIds(
                dayOrders.stream().map(Order::getId).collect(Collectors.toList()));

        Map<Long, List<Order>> ordersByCashier = dayOrders.stream()
                .filter(o -> o.getCreatedBy() != null)
                .collect(Collectors.groupingBy(o -> o.getCreatedBy().getId()));

        // On part de la liste des caissiers (un caissier sans vente doit apparaître à zéro, pas
        // disparaître du classement), puis on ajoute les autres utilisateurs qui ont passé des
        // commandes ce jour-là (par exemple un admin qui a aidé en caisse). Sinon, ses ventes
        // seraient dans les totaux sans ligne pour les expliquer.
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
        // Totaux calculés sur toutes les commandes du jour, pas en additionnant les lignes par
        // caissier : comme ça, les commandes sans créateur (imports, reprises) sont comptées.
        result.putAll(buildDayMetrics(dayOrders, totalOf(collectedPerCashier)));
        result.put("hourlySales", buildHourlySales(honoredOrders(dayOrders)));
        result.put("activeCashiers", ordersByCashier.values().stream()
                .filter(orders -> !honoredOrders(orders).isEmpty())
                .count());
        result.put("cashiers", cashiers);

        return ResponseEntity.ok(result);
    }

    /**
     * Ligne de détail d'un caissier. On renvoie le rôle pour que le front puisse signaler une
     * ligne qui n'est pas un caissier.
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

    /** Commandes valides : les annulées n'ont jamais été payées, on ne les compte pas dans les montants. */
    private List<Order> honoredOrders(List<Order> orders) {
        return orders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.CANCELED)
                .collect(Collectors.toList());
    }

    /**
     * Stats d'une journée pour un ensemble de commandes (un caissier ou tous).
     * C'est la seule source des chiffres de caisse : une modif ici change les deux vues.
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
     * Commandes du jour avec leur statut de facture, pour distinguer une commande facturée et
     * payée (affichée « Payée ») d'une commande seulement facturée.
     */
    private List<Map<String, Object>> mapDayOrders(List<Order> orders) {
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        return mapDayOrders(orders, invoiceService.getInvoiceStatusesByOrderIds(orderIds));
    }

    /**
     * Version avec les statuts déjà chargés : le suivi des caisses les charge une fois pour
     * toute la journée, au lieu d'une requête par caissier.
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
     * Ventes par heure (0 à 23) à partir des commandes valides de la journée.
     * On renvoie les heures de la première à la dernière heure avec des ventes, et les heures
     * sans vente au milieu sont mises à zéro pour que l'histogramme soit lisible. Liste vide
     * s'il n'y a aucune commande.
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
