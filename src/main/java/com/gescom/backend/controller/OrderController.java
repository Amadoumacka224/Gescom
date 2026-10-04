package com.gescom.backend.controller;

import com.gescom.backend.dto.order.OrderCreateRequest;
import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.order.OrderFilterOptions;
import com.gescom.backend.dto.order.OrderSearchCriteria;
import com.gescom.backend.dto.order.OrderResponse;
import com.gescom.backend.dto.order.OrderSummary;
import com.gescom.backend.dto.order.OrderStatusUpdateRequest;
import com.gescom.backend.dto.order.OrderUpdateRequest;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.SalesMapper;
import com.gescom.backend.service.CsvExportService;
import com.gescom.backend.service.InvoiceService;
import com.gescom.backend.service.OrderService;
import com.gescom.backend.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Commandes", description = "Cycle de vie des ventes, de la saisie a la livraison")
@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class OrderController {

    /**
     * Clé de tri pour le montant TTC affiché. Ce n'est pas un champ de l'entité : le montant
     * est calculé en SQL (facture active, sinon net HT + TVA), et c'est le seul moyen d'avoir
     * un tri qui correspond à ce qu'affiche la colonne.
     */
    private static final String PAYABLE_SORT = "payableAmount";

    private final OrderService orderService;
    private final CsvExportService csvExportService;
    private final SalesMapper salesMapper;
    private final InvoiceService invoiceService;
    // Seulement pour le taux de TVA du filtre par montant : le filtre porte sur un TTC qui
    // n'est pas stocké en base, donc le serveur doit le recalculer comme l'écran.
    private final SettingsService settingsService;

    public OrderController(OrderService orderService,
                           CsvExportService csvExportService,
                           SalesMapper salesMapper,
                           InvoiceService invoiceService,
                           SettingsService settingsService) {
        this.orderService = orderService;
        this.csvExportService = csvExportService;
        this.salesMapper = salesMapper;
        this.invoiceService = invoiceService;
        this.settingsService = settingsService;
    }

    /**
     * Toutes les commandes, sans pagination.
     *
     * Gardé pour les écrans qui ont besoin de toute la liste : la préparation d'une livraison
     * a besoin des commandes facturées, et les retours partent d'une vente existante. Le
     * tableau de l'écran Commandes utilise {@link #searchOrders}.
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        // Facture de chaque commande (une seule requête pour toutes) : son statut pour afficher
        // « Payée » dans la liste, et son total pour afficher le vrai montant TTC.
        Map<Long, Invoice> invoices = invoiceService.getInvoicesByOrderIds(
                orders.stream().map(Order::getId).toList());
        return ResponseEntity.ok(orders.stream()
                .map(o -> salesMapper.toResponse(o, invoices.get(o.getId()))).toList());
    }

    /**
     * Une page de commandes, filtrée et triée en base.
     *
     * Le taux de TVA vient des paramètres et pas de l'appelant : il sert à recalculer le TTC
     * sur lequel porte le filtre par montant (voir {@code OrderService.searchOrders}). Si le
     * client pouvait le choisir, le filtre ne correspondrait plus à ce qu'affiche l'écran.
     */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<OrderResponse>> searchOrders(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(required = false) Invoice.InvoiceStatus payment,
            @RequestParam(defaultValue = "false") boolean notInvoiced,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Client.ClientType clientType,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long createdById,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BigDecimal amountMin,
            @RequestParam(required = false) BigDecimal amountMax,
            @RequestParam(required = false) String notes,
            @RequestParam(defaultValue = "false") boolean onlyDiscounted,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        OrderSearchCriteria criteria = new OrderSearchCriteria(
                q, status, payment, notInvoiced, clientId, clientType, city, productId, categoryId,
                createdById, dateFrom, dateTo, amountMin, amountMax, notes, onlyDiscounted);

        BigDecimal taxRate = BigDecimal.valueOf(
                settingsService.getSettings().getTaxRate() == null ? 0d
                        : settingsService.getSettings().getTaxRate());

        // `payableAmount` n'est pas une colonne : si on le laisse dans le Pageable, Spring Data
        // essaie de le trouver dans l'entité et renvoie une 500. On l'enlève donc et on passe
        // juste le sens du tri, que la spécification applique sur le calcul. Il faut un Pageable
        // NON trié ici, sinon Spring Data remplacerait l'ORDER BY de la spécification par le sien.
        Sort.Order payableOrder = pageable.getSort().getOrderFor(PAYABLE_SORT);
        Pageable effectivePageable = payableOrder == null
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<Order> page = orderService.searchOrders(criteria, taxRate, effectivePageable,
                payableOrder == null ? null : payableOrder.getDirection());
        // Même chose que pour la liste complète : la facture donne le « Payée » et le montant
        // TTC. Une seule requête, seulement pour les commandes de la page.
        Map<Long, Invoice> invoices = invoiceService.getInvoicesByOrderIds(
                page.getContent().stream().map(Order::getId).toList());

        return ResponseEntity.ok(PageResponse.of(page,
                o -> salesMapper.toResponse(o, invoices.get(o.getId()))));
    }

    /**
     * Commandes prêtes à livrer : facturées et sans livraison.
     *
     * Utilisé par la liste déroulante du formulaire de livraison. Pas de pagination : c'est
     * une liste de travail qui reste petite, une commande en sort dès qu'elle est livrée.
     */
    @GetMapping("/deliverable")
    public ResponseEntity<List<OrderResponse>> getDeliverableOrders() {
        List<Order> orders = orderService.getDeliverableOrders();
        Map<Long, Invoice> invoices = invoiceService.getInvoicesByOrderIds(
                orders.stream().map(Order::getId).toList());
        return ResponseEntity.ok(orders.stream()
                .map(o -> salesMapper.toResponse(o, invoices.get(o.getId()))).toList());
    }

    /** Nombre de commandes par statut pour les cartes : sur toutes les commandes, pas sur la page. */
    @GetMapping("/summary")
    public ResponseEntity<OrderSummary> getSummary() {
        return ResponseEntity.ok(orderService.getSummary());
    }

    /** Utilisateurs et villes proposés dans les filtres (voir {@link OrderFilterOptions}). */
    @GetMapping("/filter-options")
    public ResponseEntity<OrderFilterOptions> getFilterOptions() {
        return ResponseEntity.ok(orderService.getFilterOptions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(salesMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("order", id));
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByOrderNumber(@PathVariable String orderNumber) {
        return orderService.getOrderByOrderNumber(orderNumber)
                .map(salesMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("order", "number", orderNumber));
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByClient(@PathVariable Long clientId) {
        List<Order> orders = orderService.getOrdersByClient(clientId);
        // Même calcul que pour la liste : sans la facture, une commande payée afficherait
        // « Facturée » sur la fiche client et « Payée » dans la liste des commandes.
        Map<Long, Invoice> invoices = invoiceService.getInvoicesByOrderIds(
                orders.stream().map(Order::getId).toList());
        return ResponseEntity.ok(orders.stream()
                .map(o -> salesMapper.toResponse(o, invoices.get(o.getId()))).toList());
    }

    /**
     * Ventes d'un utilisateur. Un caissier ne peut demander que les siennes : c'est la seule
     * route où on passe un id d'utilisateur, donc le moyen le plus simple d'aller voir les
     * ventes d'un collègue via l'API.
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#userId)")
    public ResponseEntity<List<OrderResponse>> getOrdersByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(orderService.getOrdersByUser(userId).stream()
                .map(salesMapper::toResponse).toList());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<OrderResponse>> getOrdersByStatus(@PathVariable Order.OrderStatus status) {
        return ResponseEntity.ok(orderService.getOrdersByStatus(status).stream()
                .map(salesMapper::toResponse).toList());
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<OrderResponse>> getOrdersByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(orderService.getOrdersByDateRange(start, end).stream()
                .map(salesMapper::toResponse).toList());
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        Order created = orderService.createOrder(salesMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(salesMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(@PathVariable Long id,
                                                     @Valid @RequestBody OrderUpdateRequest request) {
        Order patch = salesMapper.toUpdate(request);
        return ResponseEntity.ok(salesMapper.toResponse(orderService.updateOrder(id, patch)));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(@PathVariable Long id) {
        return ResponseEntity.ok(salesMapper.toResponse(orderService.confirmOrder(id)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable Long id,
                                                           @Valid @RequestBody OrderStatusUpdateRequest request) {
        Order.OrderStatus status = parseStatus(request.status());
        return ResponseEntity.ok(salesMapper.toResponse(orderService.updateOrderStatus(id, status)));
    }

    /** Convertit la valeur reçue en statut, avec une erreur 400 claire si elle n'est pas valide. */
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
