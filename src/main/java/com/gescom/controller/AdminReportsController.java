package com.gescom.controller;

import com.gescom.entity.*;
import com.gescom.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportsController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProductRepository productRepository;

    @GetMapping
    public String reportsIndex(
            @RequestParam(defaultValue = "30") int period,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Model model) {

        try {
            // Déterminer la période d'analyse
            LocalDateTime startDate;
            LocalDateTime endDate = LocalDateTime.now();

            if (dateFrom != null && dateTo != null && !dateFrom.isEmpty() && !dateTo.isEmpty()) {
                startDate = LocalDate.parse(dateFrom).atStartOfDay();
                endDate = LocalDate.parse(dateTo).atTime(23, 59, 59);
            } else {
                startDate = LocalDateTime.now().minusDays(period);
            }

            // === RAPPORT DE VENTES ===
            Map<String, Object> salesReport = generateSalesReport(startDate, endDate);

            // === RAPPORT COMMERCIAL ===
            Map<String, Object> commercialReport = generateCommercialReport(startDate, endDate);

            // === RAPPORT FINANCIER ===
            Map<String, Object> financialReport = generateFinancialReport(startDate, endDate);

            // === RAPPORT CLIENTS ===
            Map<String, Object> clientReport = generateClientReport(startDate, endDate);

            // === RAPPORT PRODUITS ===
            Map<String, Object> productReport = generateProductReport(startDate, endDate);

            // Ajouter au modèle
            model.addAttribute("salesReport", salesReport);
            model.addAttribute("commercialReport", commercialReport);
            model.addAttribute("financialReport", financialReport);
            model.addAttribute("clientReport", clientReport);
            model.addAttribute("productReport", productReport);
            model.addAttribute("period", period);
            model.addAttribute("dateFrom", dateFrom);
            model.addAttribute("dateTo", dateTo);
            model.addAttribute("startDate", startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            model.addAttribute("endDate", endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors de la génération des rapports: " + e.getMessage());
        }

        return "admin/reports/index";
    }

    @GetMapping("/sales")
    public String salesDetailReport(
            @RequestParam(defaultValue = "30") int period,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Model model) {

        try {
            LocalDateTime startDate;
            LocalDateTime endDate = LocalDateTime.now();

            if (dateFrom != null && dateTo != null && !dateFrom.isEmpty() && !dateTo.isEmpty()) {
                startDate = LocalDate.parse(dateFrom).atStartOfDay();
                endDate = LocalDate.parse(dateTo).atTime(23, 59, 59);
            } else {
                startDate = LocalDateTime.now().minusDays(period);
            }

            // Rapport détaillé des ventes
            Map<String, Object> detailedSalesReport = generateDetailedSalesReport(startDate, endDate);

            model.addAttribute("salesReport", detailedSalesReport);
            model.addAttribute("period", period);
            model.addAttribute("dateFrom", dateFrom);
            model.addAttribute("dateTo", dateTo);

        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors de la génération du rapport: " + e.getMessage());
        }

        return "admin/reports/sales";
    }

    @GetMapping("/commercial")
    public String commercialDetailReport(
            @RequestParam(defaultValue = "30") int period,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Model model) {

        try {
            LocalDateTime startDate;
            LocalDateTime endDate = LocalDateTime.now();

            if (dateFrom != null && dateTo != null && !dateFrom.isEmpty() && !dateTo.isEmpty()) {
                startDate = LocalDate.parse(dateFrom).atStartOfDay();
                endDate = LocalDate.parse(dateTo).atTime(23, 59, 59);
            } else {
                startDate = LocalDateTime.now().minusDays(period);
            }

            // Rapport détaillé commercial
            Map<String, Object> detailedCommercialReport = generateDetailedCommercialReport(startDate, endDate);

            model.addAttribute("commercialReport", detailedCommercialReport);
            model.addAttribute("period", period);
            model.addAttribute("dateFrom", dateFrom);
            model.addAttribute("dateTo", dateTo);

        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors de la génération du rapport: " + e.getMessage());
        }

        return "admin/reports/commercial";
    }

    @GetMapping("/export/{type}")
    public String exportReport(
            @PathVariable String type,
            @RequestParam(defaultValue = "30") int period,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Model model) {

        // TODO: Implémenter l'export en PDF/Excel
        model.addAttribute("info", "Fonctionnalité d'export en cours de développement");
        return "redirect:/admin/reports";
    }

    private Map<String, Object> generateSalesReport(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> report = new HashMap<>();

        List<Order> orders = orderRepository.findAll().stream()
                .filter(order -> order.getOrderDate().isAfter(startDate) && order.getOrderDate().isBefore(endDate))
                .filter(order -> order.getStatus() != Order.OrderStatus.DRAFT && order.getStatus() != Order.OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        // Chiffre d'affaires total
        BigDecimal totalRevenue = orders.stream()
                .map(order -> order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Nombre de commandes
        long totalOrders = orders.size();

        // Panier moyen
        BigDecimal averageBasket = totalOrders > 0 ?
                totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;

        // Évolution par jour
        Map<String, BigDecimal> dailyRevenue = orders.stream()
                .collect(Collectors.groupingBy(
                        order -> order.getOrderDate().format(DateTimeFormatter.ofPattern("dd/MM")),
                        Collectors.mapping(
                                order -> order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                        )
                ));

        // Top 5 des meilleurs jours
        List<Map.Entry<String, BigDecimal>> topDays = dailyRevenue.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        report.put("totalRevenue", totalRevenue);
        report.put("totalOrders", totalOrders);
        report.put("averageBasket", averageBasket);
        report.put("dailyRevenue", dailyRevenue);
        report.put("topDays", topDays);

        return report;
    }

    private Map<String, Object> generateCommercialReport(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> report = new HashMap<>();

        List<User> commercials = userRepository.findAll().stream()
                .filter(user -> user.hasRole("USER"))
                .collect(Collectors.toList());

        List<Order> orders = orderRepository.findAll().stream()
                .filter(order -> order.getOrderDate().isAfter(startDate) && order.getOrderDate().isBefore(endDate))
                .filter(order -> order.getStatus() != Order.OrderStatus.DRAFT && order.getStatus() != Order.OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        // Performance par commercial
        List<Map<String, Object>> commercialPerformance = commercials.stream()
                .map(commercial -> {
                    List<Order> commercialOrders = orders.stream()
                            .filter(order -> order.getUser().getId().equals(commercial.getId()))
                            .collect(Collectors.toList());

                    BigDecimal revenue = commercialOrders.stream()
                            .map(order -> order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    Map<String, Object> performance = new HashMap<>();
                    performance.put("commercial", commercial);
                    performance.put("orders", commercialOrders.size());
                    performance.put("revenue", revenue);
                    performance.put("averageBasket", commercialOrders.size() > 0 ?
                            revenue.divide(BigDecimal.valueOf(commercialOrders.size()), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO);

                    return performance;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("revenue")).compareTo((BigDecimal) a.get("revenue")))
                .collect(Collectors.toList());

        // Top 3 commerciaux
        List<Map<String, Object>> topCommercials = commercialPerformance.stream()
                .limit(3)
                .collect(Collectors.toList());

        report.put("commercialPerformance", commercialPerformance);
        report.put("topCommercials", topCommercials);
        report.put("totalCommercials", commercials.size());

        return report;
    }

    private Map<String, Object> generateFinancialReport(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> report = new HashMap<>();

        List<Invoice> invoices = invoiceRepository.findAll().stream()
                .filter(invoice -> invoice.getInvoiceDate().isAfter(startDate.toLocalDate()) &&
                        invoice.getInvoiceDate().isBefore(endDate.toLocalDate()))
                .collect(Collectors.toList());

        // Montant total facturé
        BigDecimal totalInvoiced = invoices.stream()
                .map(invoice -> invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Montant total encaissé
        BigDecimal totalPaid = invoices.stream()
                .filter(invoice -> invoice.getStatus() == Invoice.InvoiceStatus.PAID)
                .map(invoice -> invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Montant en attente
        BigDecimal totalOutstanding = totalInvoiced.subtract(totalPaid);

        // Factures en retard
        LocalDate today = LocalDate.now();
        long overdueInvoices = invoices.stream()
                .filter(invoice -> invoice.getDueDate() != null &&
                        invoice.getDueDate().isBefore(today) &&
                        invoice.getStatus() != Invoice.InvoiceStatus.PAID)
                .count();

        // Taux d'encaissement
        double collectionRate = totalInvoiced.compareTo(BigDecimal.ZERO) > 0 ?
                totalPaid.divide(totalInvoiced, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue() : 0;

        report.put("totalInvoiced", totalInvoiced);
        report.put("totalPaid", totalPaid);
        report.put("totalOutstanding", totalOutstanding);
        report.put("overdueInvoices", overdueInvoices);
        report.put("collectionRate", collectionRate);
        report.put("totalInvoices", invoices.size());

        return report;
    }

    private Map<String, Object> generateClientReport(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> report = new HashMap<>();

        List<Client> allClients = clientRepository.findAll();

        // Nouveaux clients sur la période
        List<Client> newClients = allClients.stream()
                .filter(client -> client.getCreatedAt() != null &&
                        client.getCreatedAt().isAfter(startDate) &&
                        client.getCreatedAt().isBefore(endDate))
                .collect(Collectors.toList());

        // Répartition par type
        long companies = allClients.stream()
                .filter(client -> client.getClientType() == Client.ClientType.COMPANY)
                .count();

        long individuals = allClients.stream()
                .filter(client -> client.getClientType() == Client.ClientType.INDIVIDUAL)
                .count();

        // Répartition par statut
        long activeClients = allClients.stream()
                .filter(client -> client.getStatus() == Client.ClientStatus.ACTIVE)
                .count();

        long prospects = allClients.stream()
                .filter(client -> client.getStatus() == Client.ClientStatus.PROSPECT)
                .count();

        report.put("totalClients", allClients.size());
        report.put("newClients", newClients.size());
        report.put("companies", companies);
        report.put("individuals", individuals);
        report.put("activeClients", activeClients);
        report.put("prospects", prospects);

        return report;
    }

    private Map<String, Object> generateProductReport(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> report = new HashMap<>();

        List<Product> allProducts = productRepository.findAll();

        // Produits en stock bas
        List<Product> lowStockProducts = allProducts.stream()
                .filter(product -> product.getMinStock() != null && product.getStock() <= product.getMinStock())
                .collect(Collectors.toList());

        // Valeur totale du stock
        BigDecimal totalStockValue = allProducts.stream()
                .filter(product -> true)
                .map(product -> product.getUnitPrice().multiply(BigDecimal.valueOf(product.getStock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Répartition par catégorie
        Map<String, Long> productsByCategory = allProducts.stream()
                .filter(product -> product.getCategory() != null)
                .collect(Collectors.groupingBy(
                        Product::getCategory,
                        Collectors.counting()
                ));

        // Produits actifs vs inactifs
        long activeProducts = allProducts.stream()
                .filter(product -> product.getIsActive() != null && product.getIsActive())
                .count();

        report.put("totalProducts", allProducts.size());
        report.put("lowStockProducts", lowStockProducts);
        report.put("lowStockCount", lowStockProducts.size());
        report.put("totalStockValue", totalStockValue);
        report.put("productsByCategory", productsByCategory);
        report.put("activeProducts", activeProducts);

        return report;
    }

    private Map<String, Object> generateDetailedSalesReport(LocalDateTime startDate, LocalDateTime endDate) {
        // Version détaillée du rapport de ventes avec plus de métriques
        Map<String, Object> report = generateSalesReport(startDate, endDate);

        List<Order> orders = orderRepository.findAll().stream()
                .filter(order -> order.getOrderDate().isAfter(startDate) && order.getOrderDate().isBefore(endDate))
                .filter(order -> order.getStatus() != Order.OrderStatus.DRAFT && order.getStatus() != Order.OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        // Analyse par statut de commande
        Map<Order.OrderStatus, Long> ordersByStatus = orders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

        // Analyse par mois (si période > 60 jours)
        if (startDate.isBefore(LocalDateTime.now().minusDays(60))) {
            Map<String, BigDecimal> monthlyRevenue = orders.stream()
                    .collect(Collectors.groupingBy(
                            order -> order.getOrderDate().format(DateTimeFormatter.ofPattern("MM/yyyy")),
                            Collectors.mapping(
                                    order -> order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO,
                                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                            )
                    ));
            report.put("monthlyRevenue", monthlyRevenue);
        }

        report.put("ordersByStatus", ordersByStatus);

        return report;
    }

    private Map<String, Object> generateDetailedCommercialReport(LocalDateTime startDate, LocalDateTime endDate) {
        // Version détaillée du rapport commercial
        Map<String, Object> report = generateCommercialReport(startDate, endDate);

        // Ajouter des métriques de conversion, taux de closing, etc.
        List<User> commercials = userRepository.findAll().stream()
                .filter(user -> user.hasRole("USER"))
                .collect(Collectors.toList());

        // Analyse des objectifs vs réalisations
        List<Map<String, Object>> objectiveAnalysis = commercials.stream()
                .map(commercial -> {
                    Map<String, Object> analysis = new HashMap<>();
                    analysis.put("commercial", commercial);
                    analysis.put("personalTarget", commercial.getPersonalTarget());

                    // Calculer le CA réalisé
                    BigDecimal achievedRevenue = orderRepository.findAll().stream()
                            .filter(order -> order.getUser().getId().equals(commercial.getId()))
                            .filter(order -> order.getOrderDate().isAfter(startDate) && order.getOrderDate().isBefore(endDate))
                            .filter(order -> order.getStatus() != Order.OrderStatus.DRAFT && order.getStatus() != Order.OrderStatus.CANCELLED)
                            .map(order -> order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    analysis.put("achievedRevenue", achievedRevenue);

                    // Calculer le pourcentage d'atteinte
                    if (commercial.getPersonalTarget() != null && commercial.getPersonalTarget().compareTo(BigDecimal.ZERO) > 0) {
                        double achievementRate = achievedRevenue
                                .divide(commercial.getPersonalTarget(), 4, BigDecimal.ROUND_HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .doubleValue();
                        analysis.put("achievementRate", achievementRate);
                    } else {
                        analysis.put("achievementRate", 0.0);
                    }

                    return analysis;
                })
                .collect(Collectors.toList());

        report.put("objectiveAnalysis", objectiveAnalysis);

        return report;
    }
}