package com.gescom.backend.service;

import com.gescom.backend.dto.dashboard.DashboardOverview;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.Product;
import com.gescom.backend.repository.ClientRepository;
import com.gescom.backend.repository.DeliveryRepository;
import com.gescom.backend.repository.InvoiceRepository;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Agrégats du tableau de bord.
 *
 * <h2>Pourquoi ce service existe</h2>
 *
 * Ces calculs vivaient dans {@code DashboardController}, qui atteignait 539 lignes. Outre
 * l'entorse à la règle du projet — les contrôleurs sont fins, ils appellent le service puis
 * mappent —, la méthode chargeait EN MÉMOIRE toutes les commandes, toutes les factures, toutes
 * les livraisons et tous les produits du locataire, à chaque affichage, pour n'en tirer qu'une
 * vingtaine de chiffres et trois listes de cinq lignes.
 *
 * Tout est désormais agrégé en base, comme le fait {@code PlatformMetricsService} pour le
 * back-office de la plateforme — les deux écrans du même genre suivent enfin la même méthode.
 *
 * <h2>Une requête par bloc, et non une par chiffre</h2>
 *
 * Les décomptes d'un même bloc viennent d'une seule requête. Ce n'est pas une optimisation :
 * l'écran les affiche côte à côte comme une répartition, et deux requêtes séparées pourraient
 * lire des états différents de la base — le total ne tomberait plus juste, sans qu'aucune ligne
 * ne soit fausse.
 */
@Service
public class DashboardService {

    /** Longueur des trois listes courtes de l'aperçu. */
    private static final int SHORT_LIST_SIZE = 5;

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final DeliveryRepository deliveryRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;

    public DashboardService(OrderRepository orderRepository, InvoiceRepository invoiceRepository,
                            DeliveryRepository deliveryRepository, ClientRepository clientRepository,
                            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.deliveryRepository = deliveryRepository;
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
    }

    /**
     * Aperçu complet.
     *
     * Réservé à l'ADMIN par le contrôleur : les agrégats portent donc sur toute l'entreprise, et
     * les synthèses sont appelées sans restriction d'opérateur. Le cloisonnement PAR ENTREPRISE,
     * lui, reste assuré — le filtre Hibernate couvre ces requêtes comme les autres.
     */
    @Transactional(readOnly = true)
    public DashboardOverview getOverview() {
        OrderRepository.OrderSummaryView orders = orderRepository.summaryFor(
                null, Order.OrderStatus.PENDING, Order.OrderStatus.CONFIRMED,
                Order.OrderStatus.INVOICED, Order.OrderStatus.DELIVERED, Order.OrderStatus.CANCELED);

        InvoiceRepository.InvoiceSummaryView invoices = invoiceRepository.summaryFor(
                null, LocalDate.now(), Invoice.InvoiceStatus.UNPAID, Invoice.InvoiceStatus.PARTIALLY_PAID,
                Invoice.InvoiceStatus.PAID, Invoice.InvoiceStatus.CANCELED);

        DeliveryRepository.DeliverySummaryView deliveries = deliveryRepository.summaryFor(
                null, LocalDate.now().atStartOfDay(),
                com.gescom.backend.entity.Delivery.DeliveryStatus.PENDING,
                com.gescom.backend.entity.Delivery.DeliveryStatus.DELIVERED);

        ProductRepository.CatalogSummaryView catalog = productRepository.catalogSummary();

        BigDecimal collected = orZero(invoices.getCollected());
        BigDecimal pending = orZero(invoices.getPending());

        return new DashboardOverview(
                orZero(orderRepository.sumHonoredSales(Order.OrderStatus.CANCELED)),
                orders.getTotal(),
                orders.getPending(),
                orders.getConfirmed(),
                orders.getInvoiced(),
                orders.getDelivered(),
                orders.getCanceled(),
                clientRepository.countByActiveTrue(),
                catalog.getLowStock(),

                invoices.getTotal(),
                collected,
                // Facturé = encaissé + reliquat, sur le même ensemble. Reconstruit ici plutôt
                // que demandé à la base : l'identité affichée par l'écran devient vraie par
                // construction, et non par la coïncidence de deux sommes.
                collected.add(pending),
                pending,
                invoices.getUnpaid(),
                invoices.getPartial(),
                invoices.getPaid(),
                invoices.getCanceled(),

                deliveries.getTotal(),
                deliveries.getPending(),
                deliveries.getDelivered(),
                orderRepository.countToSchedule(Order.OrderStatus.INVOICED),

                recentOrders(),
                stockLines(productRepository.findTopStocked(PageRequest.of(0, SHORT_LIST_SIZE))),
                stockLines(productRepository.findMostDepleted(PageRequest.of(0, SHORT_LIST_SIZE))));
    }

    private List<DashboardOverview.RecentOrder> recentOrders() {
        List<Order> recent = orderRepository.findRecent(PageRequest.of(0, SHORT_LIST_SIZE));
        Map<Long, Invoice.InvoiceStatus> statuses = invoiceRepository
                .findByOrderIdIn(recent.stream().map(Order::getId).toList()).stream()
                .filter(i -> i.getOrder() != null)
                .collect(java.util.stream.Collectors.toMap(
                        i -> i.getOrder().getId(), Invoice::getStatus, (a, b) -> a));

        return recent.stream().map(o -> new DashboardOverview.RecentOrder(
                o.getId(),
                o.getOrderNumber(),
                o.getClient() != null
                        ? (o.getClient().getFirstName() + " " + o.getClient().getLastName()).trim()
                        : "N/A",
                o.getFinalAmount(),
                o.getStatus() != null ? o.getStatus().name() : null,
                o.getCreatedAt() != null ? o.getCreatedAt().toString() : null,
                // Les lignes sont chargées par la jointure de findRecent : pas de N+1 ici.
                o.getItems() != null
                        ? o.getItems().stream().mapToInt(it -> it.getQuantity() != null ? it.getQuantity() : 0).sum()
                        : 0,
                statuses.get(o.getId()) != null ? statuses.get(o.getId()).name() : null
        )).toList();
    }

    private List<DashboardOverview.StockLine> stockLines(List<Product> products) {
        return products.stream()
                .map(p -> new DashboardOverview.StockLine(
                        p.getId(), p.getName(),
                        p.getStockQuantity() != null ? p.getStockQuantity() : 0))
                .toList();
    }

    /** SUM rend NULL sur un ensemble vide ; un montant nul hérité faisait tomber l'aperçu en 500. */
    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
