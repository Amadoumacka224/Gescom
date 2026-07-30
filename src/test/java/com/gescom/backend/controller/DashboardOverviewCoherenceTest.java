package com.gescom.backend.controller;

import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Delivery;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.service.ActivityLogService;
import com.gescom.backend.service.ClientService;
import com.gescom.backend.service.DeliveryService;
import com.gescom.backend.service.InvoiceService;
import com.gescom.backend.service.OrderService;
import com.gescom.backend.service.ProductService;
import com.gescom.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Vérifie que l'aperçu du tableau de bord décrit fidèlement l'état réel des données : ce sont
 * exactement les chiffres que l'interface affiche côte à côte (anneaux, « reste à encaisser »,
 * répartitions par statut), et une incohérence entre eux se lit immédiatement à l'écran.
 *
 * Test unitaire pur (Mockito) : aucune base ni contexte Spring. Le journal d'activité reste
 * silencieux, aucun utilisateur n'étant authentifié.
 */
@ExtendWith(MockitoExtension.class)
class DashboardOverviewCoherenceTest {

    @Mock private OrderService orderService;
    @Mock private ClientService clientService;
    @Mock private ProductService productService;
    @Mock private InvoiceService invoiceService;
    @Mock private DeliveryService deliveryService;
    @Mock private ActivityLogService activityLogService;
    @Mock private UserService userService;

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(orderService, clientService, productService,
                invoiceService, deliveryService, activityLogService, userService);
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private Order order(long id, Order.OrderStatus status, String finalAmount) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber("CMD-" + id);
        order.setStatus(status);
        order.setFinalAmount(finalAmount != null ? new BigDecimal(finalAmount) : null);
        order.setCreatedAt(LocalDateTime.now().minusHours(id));
        Client client = new Client();
        client.setFirstName("Client");
        client.setLastName(String.valueOf(id));
        order.setClient(client);
        return order;
    }

    private Invoice invoice(Invoice.InvoiceStatus status, String totalAmount, String paidAmount) {
        Invoice invoice = new Invoice();
        invoice.setStatus(status);
        invoice.setTotalAmount(new BigDecimal(totalAmount));
        invoice.setPaidAmount(new BigDecimal(paidAmount));
        return invoice;
    }

    private Delivery delivery(Order order, Delivery.DeliveryStatus status) {
        Delivery delivery = new Delivery();
        delivery.setOrder(order);
        delivery.setStatus(status);
        return delivery;
    }

    /**
     * Jeu de données de référence. Le cas notable est la commande 6 : annulée, mais conservant
     * une livraison en attente — état réellement atteignable (annulation de la facture puis de
     * la commande, `cancelInvoice` ne vérifiant pas les livraisons).
     */
    private Map<String, Object> overviewOf(List<Order> orders, List<Invoice> invoices, List<Delivery> deliveries) {
        when(orderService.getAllOrders()).thenReturn(orders);
        when(invoiceService.getAllInvoices()).thenReturn(invoices);
        when(deliveryService.getAllDeliveries()).thenReturn(deliveries);
        when(invoiceService.getInvoiceStatusesByOrderIds(anyList())).thenReturn(Collections.emptyMap());
        when(clientService.getActiveClients()).thenReturn(Collections.emptyList());
        when(productService.getLowStockProducts()).thenReturn(Collections.emptyList());
        when(productService.getAllProducts()).thenReturn(Collections.emptyList());

        return controller.getDashboardOverview().getBody();
    }

    // ── Commandes ───────────────────────────────────────────────────────────

    @Test
    void orderStatusCounts_areExhaustive_andSalesExcludeCanceledOrders() {
        List<Order> orders = List.of(
                order(1, Order.OrderStatus.PENDING, "100.00"),
                order(2, Order.OrderStatus.CONFIRMED, "200.00"),
                order(3, Order.OrderStatus.INVOICED, "300.00"),
                order(4, Order.OrderStatus.INVOICED, "400.00"),
                order(5, Order.OrderStatus.DELIVERED, "500.00"),
                order(6, Order.OrderStatus.CANCELED, "600.00"));

        Map<String, Object> overview = overviewOf(orders, List.of(), List.of());

        // Les cinq statuts couvrent la table : c'est ce qui permet à la répartition affichée de
        // totaliser 100 %, et à « commandes actives » de valoir total − annulées.
        long sumOfStatuses = (long) overview.get("pendingOrders")
                + (long) overview.get("confirmedOrders")
                + (long) overview.get("invoicedOrders")
                + (long) overview.get("deliveredOrders")
                + (long) overview.get("canceledOrders");
        assertThat(sumOfStatuses).isEqualTo((int) overview.get("totalOrders"));

        // Le CA et le sous-titre « N commandes honorées » doivent décrire le même ensemble.
        assertThat((BigDecimal) overview.get("totalSales")).isEqualByComparingTo("1500.00");
    }

    @Test
    void totalSales_toleratesOrdersWithoutAmount() {
        List<Order> orders = List.of(
                order(1, Order.OrderStatus.DELIVERED, "250.00"),
                order(2, Order.OrderStatus.PENDING, null));

        Map<String, Object> overview = overviewOf(orders, List.of(), List.of());

        assertThat((BigDecimal) overview.get("totalSales")).isEqualByComparingTo("250.00");
    }

    // ── Factures ────────────────────────────────────────────────────────────

    @Test
    void invoiceAmounts_describeTheSameSet_andExcludeCanceledInvoices() {
        List<Invoice> invoices = List.of(
                invoice(Invoice.InvoiceStatus.PAID, "300.00", "300.00"),
                invoice(Invoice.InvoiceStatus.PARTIALLY_PAID, "400.00", "150.00"),
                invoice(Invoice.InvoiceStatus.UNPAID, "200.00", "0.00"),
                // Facture annulée partiellement encaissée : ni son total ni son encaissement
                // ne doivent apparaître, sinon le taux d'encaissement porte sur un périmètre
                // qui n'existe pas.
                invoice(Invoice.InvoiceStatus.CANCELED, "1000.00", "50.00"));

        Map<String, Object> overview = overviewOf(List.of(), invoices, List.of());

        assertThat((BigDecimal) overview.get("invoicedAmount")).isEqualByComparingTo("900.00");
        assertThat((BigDecimal) overview.get("totalRevenue")).isEqualByComparingTo("450.00");
        assertThat((BigDecimal) overview.get("pendingAmount")).isEqualByComparingTo("450.00");

        // Identité affichée à l'écran : encaissé + reste à encaisser = facturé.
        BigDecimal collected = (BigDecimal) overview.get("totalRevenue");
        BigDecimal pending = (BigDecimal) overview.get("pendingAmount");
        assertThat(collected.add(pending)).isEqualByComparingTo((BigDecimal) overview.get("invoicedAmount"));
    }

    @Test
    void invoiceStatusCounts_areExhaustive() {
        List<Invoice> invoices = List.of(
                invoice(Invoice.InvoiceStatus.PAID, "100.00", "100.00"),
                invoice(Invoice.InvoiceStatus.PARTIALLY_PAID, "100.00", "40.00"),
                invoice(Invoice.InvoiceStatus.UNPAID, "100.00", "0.00"),
                invoice(Invoice.InvoiceStatus.CANCELED, "100.00", "0.00"));

        Map<String, Object> overview = overviewOf(List.of(), invoices, List.of());

        long sumOfStatuses = (long) overview.get("paidInvoices")
                + (long) overview.get("partiallyPaidInvoices")
                + (long) overview.get("unpaidInvoices")
                + (long) overview.get("canceledInvoices");
        assertThat(sumOfStatuses).isEqualTo((int) overview.get("totalInvoices"));
    }

    // ── Livraisons ──────────────────────────────────────────────────────────

    @Test
    void ordersToSchedule_countsInvoicedOrdersWithoutDelivery_notADifferenceOfCounters() {
        Order toSchedule = order(3, Order.OrderStatus.INVOICED, "300.00");
        Order scheduled = order(4, Order.OrderStatus.INVOICED, "400.00");
        Order shipped = order(5, Order.OrderStatus.DELIVERED, "500.00");
        Order canceledWithLeftoverDelivery = order(6, Order.OrderStatus.CANCELED, "600.00");

        List<Delivery> deliveries = List.of(
                delivery(scheduled, Delivery.DeliveryStatus.PENDING),
                delivery(shipped, Delivery.DeliveryStatus.DELIVERED),
                delivery(canceledWithLeftoverDelivery, Delivery.DeliveryStatus.PENDING));

        Map<String, Object> overview = overviewOf(
                List.of(toSchedule, scheduled, shipped, canceledWithLeftoverDelivery),
                List.of(), deliveries);

        // Seule la commande 3 reste à planifier. La soustraction naïve
        // « facturées (2) − livraisons en attente (2) » aurait répondu 0.
        assertThat(overview.get("ordersToSchedule")).isEqualTo(1L);
        assertThat(overview.get("pendingDeliveries")).isEqualTo(2L);
        assertThat(overview.get("invoicedOrders")).isEqualTo(2L);
    }

    @Test
    void deliveryStatusCounts_areExhaustive() {
        Order first = order(1, Order.OrderStatus.INVOICED, "100.00");
        Order second = order(2, Order.OrderStatus.DELIVERED, "200.00");

        Map<String, Object> overview = overviewOf(
                List.of(first, second),
                List.of(),
                List.of(delivery(first, Delivery.DeliveryStatus.PENDING),
                        delivery(second, Delivery.DeliveryStatus.DELIVERED)));

        long sumOfStatuses = (long) overview.get("pendingDeliveries")
                + (long) overview.get("deliveredDeliveries");
        assertThat(sumOfStatuses).isEqualTo((int) overview.get("totalDeliveries"));
    }
}
