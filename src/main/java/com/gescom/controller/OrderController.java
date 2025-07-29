package com.gescom.controller;

import com.gescom.entity.Order;
import com.gescom.entity.Product;
import com.gescom.entity.User;
import com.gescom.entity.Client;
import com.gescom.repository.OrderRepository;
import com.gescom.repository.ProductRepository;
import com.gescom.repository.UserRepository;
import com.gescom.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProductRepository productRepository;

    @GetMapping
    public String listOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Model model) {

        try {
            // Récupérer l'utilisateur connecté
            Optional<User> currentUserOpt = userRepository.findByUsername(userDetails.getUsername());
            if (currentUserOpt.isEmpty()) {
                model.addAttribute("error", "Utilisateur non trouvé");
                return "redirect:/dashboard";
            }

            User currentUser = currentUserOpt.get();

            // Récupérer les commandes selon le rôle
            List<Order> allOrders;
            if (currentUser.hasRole("ADMIN") || currentUser.hasRole("MANAGER")) {
                // Admin et Manager voient toutes les commandes
                allOrders = orderRepository.findAll();
            } else {
                // Utilisateur normal voit seulement ses commandes
                allOrders = orderRepository.findAll().stream()
                        .filter(order -> order.getUser().getId().equals(currentUser.getId()))
                        .collect(Collectors.toList());
            }

            // Filtrage par recherche
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                allOrders = allOrders.stream()
                        .filter(order ->
                                order.getOrderNumber().toLowerCase().contains(searchLower) ||
                                        order.getClient().getName().toLowerCase().contains(searchLower) ||
                                        order.getUser().getFullName().toLowerCase().contains(searchLower))
                        .collect(Collectors.toList());
            }

            // Filtrage par statut
            if (status != null && !status.trim().isEmpty()) {
                Order.OrderStatus orderStatus = Order.OrderStatus.valueOf(status);
                allOrders = allOrders.stream()
                        .filter(order -> order.getStatus() == orderStatus)
                        .collect(Collectors.toList());
            }

            // Filtrage par date
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (dateFrom != null && !dateFrom.trim().isEmpty()) {
                LocalDateTime fromDate = LocalDateTime.parse(dateFrom + "T00:00:00");
                allOrders = allOrders.stream()
                        .filter(order -> order.getOrderDate().isAfter(fromDate))
                        .collect(Collectors.toList());
            }
            if (dateTo != null && !dateTo.trim().isEmpty()) {
                LocalDateTime toDate = LocalDateTime.parse(dateTo + "T23:59:59");
                allOrders = allOrders.stream()
                        .filter(order -> order.getOrderDate().isBefore(toDate))
                        .collect(Collectors.toList());
            }

            // Tri
            switch (sortBy) {
                case "orderNumber" -> allOrders.sort((a, b) -> sortDir.equals("desc") ?
                        b.getOrderNumber().compareTo(a.getOrderNumber()) :
                        a.getOrderNumber().compareTo(b.getOrderNumber()));
                case "client" -> allOrders.sort((a, b) -> sortDir.equals("desc") ?
                        b.getClient().getName().compareTo(a.getClient().getName()) :
                        a.getClient().getName().compareTo(b.getClient().getName()));
                case "amount" -> allOrders.sort((a, b) -> {
                    BigDecimal amountA = a.getTotalAmount() != null ? a.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal amountB = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO;
                    return sortDir.equals("desc") ? amountB.compareTo(amountA) : amountA.compareTo(amountB);
                });
                default -> allOrders.sort((a, b) -> sortDir.equals("desc") ?
                        b.getOrderDate().compareTo(a.getOrderDate()) :
                        a.getOrderDate().compareTo(b.getOrderDate()));
            }

            // Pagination manuelle
            int start = Math.min(page * size, allOrders.size());
            int end = Math.min(start + size, allOrders.size());
            List<Order> ordersPage = allOrders.subList(start, end);

            // Calculs pour la pagination
            int totalPages = (int) Math.ceil((double) allOrders.size() / size);
            boolean hasNext = page < totalPages - 1;
            boolean hasPrevious = page > 0;

            // Statistiques
            long totalOrders = allOrders.size();
            long draftOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.DRAFT).count();
            long confirmedOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.CONFIRMED).count();
            long deliveredOrders = allOrders.stream().filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED).count();

            // Montant total
            BigDecimal totalAmount = allOrders.stream()
                    .map(Order::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Récupérer la liste des clients pour les filtres
            List<Client> clients;
            if (currentUser.hasRole("ADMIN") || currentUser.hasRole("MANAGER")) {
                clients = clientRepository.findAll();
            } else {
                clients = clientRepository.findAll().stream()
                        .filter(client -> client.getAssignedUser() != null &&
                                client.getAssignedUser().getId().equals(currentUser.getId()))
                        .collect(Collectors.toList());
            }

            // Ajouter les attributs au modèle
            model.addAttribute("orders", ordersPage);
            model.addAttribute("clients", clients);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("hasNext", hasNext);
            model.addAttribute("hasPrevious", hasPrevious);
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);
            model.addAttribute("search", search);
            model.addAttribute("selectedStatus", status);
            model.addAttribute("dateFrom", dateFrom);
            model.addAttribute("dateTo", dateTo);
            model.addAttribute("size", size);

            // Statistiques
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("draftOrders", draftOrders);
            model.addAttribute("confirmedOrders", confirmedOrders);
            model.addAttribute("deliveredOrders", deliveredOrders);
            model.addAttribute("totalAmount", totalAmount);

            // Vérification des permissions
            model.addAttribute("canEdit", currentUser.hasRole("ADMIN") || currentUser.hasRole("MANAGER"));
            model.addAttribute("canDelete", currentUser.hasRole("ADMIN"));
            model.addAttribute("canCreate", true); // Tous peuvent créer des commandes

        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des commandes: " + e.getMessage());
        }

        return "orders/list";
    }

    @GetMapping("/new")
    public String newOrder(Model model) {

        List<Product> products  = productRepository.findAll().stream()
                .filter(Product::getIsActive)
                .toList();
        model.addAttribute("products", products);
        model.addAttribute("order", new Order());
        model.addAttribute("isEdit", false);

        // Liste des clients pour la sélection
        List<Client> clients = clientRepository.findAll().stream()
                .filter(client -> client.getStatus() == Client.ClientStatus.ACTIVE)
                .collect(Collectors.toList());
        model.addAttribute("clients", clients);

        return "orders/form";
    }

    @GetMapping("/{id}")
    public String viewOrder(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Commande non trouvée");
            return "redirect:/orders";
        }

        model.addAttribute("order", orderOpt.get());
        return "orders/detail";
    }

    @GetMapping("/{id}/edit")
    public String editOrder(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Commande non trouvée");
            return "redirect:/orders";
        }

        Order order = orderOpt.get();

        // Vérifier si la commande peut être modifiée
        if (order.getStatus() == Order.OrderStatus.DELIVERED || order.getStatus() == Order.OrderStatus.CANCELLED) {
            redirectAttributes.addFlashAttribute("error", "Cette commande ne peut plus être modifiée");
            return "redirect:/orders/" + id;
        }

        model.addAttribute("order", order);
        model.addAttribute("isEdit", true);

        // Liste des clients pour la sélection
        List<Client> clients = clientRepository.findAll().stream()
                .filter(client -> client.getStatus() == Client.ClientStatus.ACTIVE)
                .collect(Collectors.toList());
        model.addAttribute("clients", clients);

        return "orders/form";
    }

    @PostMapping("/{id}/update-status")
    public String updateOrderStatus(
            @PathVariable Long id,
            @RequestParam String newStatus,
            RedirectAttributes redirectAttributes) {

        try {
            Optional<Order> orderOpt = orderRepository.findById(id);
            if (orderOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Commande non trouvée");
                return "redirect:/orders";
            }

            Order order = orderOpt.get();
            Order.OrderStatus status = Order.OrderStatus.valueOf(newStatus);

            // Validation des transitions de statut
            if (!isValidStatusTransition(order.getStatus(), status)) {
                redirectAttributes.addFlashAttribute("error", "Transition de statut invalide");
                return "redirect:/orders/" + id;
            }

            order.setStatus(status);
            orderRepository.save(order);

            String statusName = getStatusDisplayName(status);
            redirectAttributes.addFlashAttribute("success", "Statut mis à jour: " + statusName);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la mise à jour: " + e.getMessage());
        }

        return "redirect:/orders/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteOrder(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(id);
            if (orderOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Commande non trouvée");
                return "redirect:/orders";
            }

            Order order = orderOpt.get();

            // Vérifier si la commande peut être supprimée
            if (order.getStatus() != Order.OrderStatus.DRAFT) {
                redirectAttributes.addFlashAttribute("error", "Seules les commandes en brouillon peuvent être supprimées");
                return "redirect:/orders/" + id;
            }

            orderRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Commande supprimée avec succès");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression: " + e.getMessage());
        }

        return "redirect:/orders";
    }

    @PostMapping("/{id}/duplicate")
    public String duplicateOrder(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(id);
            if (orderOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Commande non trouvée");
                return "redirect:/orders";
            }

            Order originalOrder = orderOpt.get();

            // Créer une nouvelle commande basée sur l'originale
            Order newOrder = new Order();
            newOrder.setClient(originalOrder.getClient());
            newOrder.setUser(originalOrder.getUser());
            newOrder.setStatus(Order.OrderStatus.DRAFT);
            newOrder.setOrderDate(LocalDateTime.now());
            newOrder.setBillingAddress(originalOrder.getBillingAddress());
            newOrder.setShippingAddress(originalOrder.getShippingAddress());
            newOrder.setNotes("Copie de la commande " + originalOrder.getOrderNumber());

            // Générer un nouveau numéro de commande
            newOrder.setOrderNumber(generateOrderNumber());

            // Copier les items (vous devrez implémenter cette logique selon votre modèle)
            // newOrder.setOrderItems(copyOrderItems(originalOrder.getOrderItems(), newOrder));

            orderRepository.save(newOrder);

            redirectAttributes.addFlashAttribute("success", "Commande dupliquée avec succès");
            return "redirect:/orders/" + newOrder.getId() + "/edit";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la duplication: " + e.getMessage());
        }

        return "redirect:/orders/" + id;
    }

    private boolean isValidStatusTransition(Order.OrderStatus from, Order.OrderStatus to) {
        return switch (from) {
            case DRAFT -> to == Order.OrderStatus.CONFIRMED || to == Order.OrderStatus.CANCELLED;
            case CONFIRMED -> to == Order.OrderStatus.PROCESSING || to == Order.OrderStatus.CANCELLED;
            case PROCESSING -> to == Order.OrderStatus.SHIPPED || to == Order.OrderStatus.CANCELLED;
            case SHIPPED -> to == Order.OrderStatus.DELIVERED;
            case DELIVERED -> false; // Aucune transition possible depuis DELIVERED
            case CANCELLED -> false; // Aucune transition possible depuis CANCELLED
            default -> false;
        };
    }

    private String getStatusDisplayName(Order.OrderStatus status) {
        return switch (status) {
            case DRAFT -> "Brouillon";
            case CONFIRMED -> "Confirmée";
            case PROCESSING -> "En cours";
            case SHIPPED -> "Expédiée";
            case DELIVERED -> "Livrée";
            case CANCELLED -> "Annulée";
            default -> status.name();
        };
    }

    private String generateOrderNumber() {
        // Générer un numéro de commande unique
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = orderRepository.count() + 1;
        return String.format("CMD-%s-%04d", datePrefix, count);
    }
}