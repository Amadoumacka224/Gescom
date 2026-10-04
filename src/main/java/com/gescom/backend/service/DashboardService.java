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
 * Calculs du tableau de bord.
 *
 * <h2>Pourquoi ce service</h2>
 *
 * Avant, ces calculs étaient dans {@code DashboardController}, qui faisait 539 lignes. En
 * plus de ne pas respecter la règle du projet (les contrôleurs appellent le service puis
 * convertissent, c'est tout), la méthode chargeait EN MÉMOIRE toutes les commandes, factures,
 * livraisons et tous les produits de l'entreprise à chaque affichage, juste pour sortir une
 * vingtaine de chiffres et trois listes de cinq lignes.
 *
 * Maintenant tout est calculé en base, comme {@code PlatformMetricsService} le fait pour le
 * back-office de la plateforme. Les deux écrans du même type fonctionnent enfin pareil.
 *
 * <h2>Une requête par bloc, pas une par chiffre</h2>
 *
 * Les compteurs d'un même bloc viennent d'une seule requête. Ce n'est pas pour les
 * performances : l'écran les affiche côte à côte comme une répartition, et avec deux
 * requêtes séparées on pourrait lire la base à deux moments différents. Le total ne
 * tomberait plus juste, sans qu'aucune ligne soit fausse.
 */
@Service
public class DashboardService {

    /** Nombre de lignes des trois petites listes du tableau de bord. */
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
     * Toutes les données du tableau de bord.
     *
     * Le contrôleur le réserve à l'ADMIN : les calculs portent donc sur toute l'entreprise,
     * sans limiter à un caissier. Le filtrage PAR ENTREPRISE, lui, marche toujours : le
     * filtre Hibernate s'applique à ces requêtes comme aux autres.
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
                // Facturé = encaissé + reste, sur les mêmes factures. On le calcule ici au lieu
                // de le demander à la base : comme ça l'égalité affichée à l'écran est toujours
                // vraie, et pas seulement parce que deux sommes tombent juste.
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

    /** SUM renvoie NULL quand il n'y a rien ; ce null faisait planter le tableau de bord en 500. */
    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
