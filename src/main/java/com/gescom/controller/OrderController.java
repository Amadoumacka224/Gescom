package com.gescom.controller;






import com.gescom.entity.Order;
import com.gescom.entity.OrderItem;
import com.gescom.entity.Product;
import com.gescom.entity.User;
import com.gescom.entity.Client;
import com.gescom.repository.OrderRepository;
import com.gescom.repository.OrderItemsRepository;
import com.gescom.repository.ProductRepository;
import com.gescom.repository.UserRepository;
import com.gescom.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

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

        // Liste des catégories pour le filtre
        List<String> categories = productRepository.findAll().stream()
                .map(Product::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        model.addAttribute("categories", categories);

        return "orders/form";
    }


    @PostMapping
    @Transactional
    public String saveOrder(
            @ModelAttribute Order order,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String action,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {


        try {
            // Récupérer l'utilisateur connecté
            Optional<User> currentUserOpt = userRepository.findByUsername(userDetails.getUsername());
            // Vérifier si l'utilisateur existe
            if (currentUserOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Utilisateur non trouvé");
                return "redirect:/orders";
            }

            User currentUser = currentUserOpt.get();

            // Si c'est une nouvelle commande (ID null ou vide)
            if (order.getId() == null) {
                order.setUser(currentUser);
                order.setOrderDate(LocalDateTime.now());
                order.setOrderNumber(generateOrderNumber());

                // Définir le statut selon l'action
                if ("confirm".equals(action)) {
                    order.setStatus(Order.OrderStatus.CONFIRMED);
                } else {
                    order.setStatus(Order.OrderStatus.DRAFT);
                }
            } else {
                // Pour une mise à jour, rediriger vers la méthode spécifique
                return updateOrder(order.getId(), order, action, allParams, redirectAttributes);
            }

            // Traiter les OrderItems depuis les paramètres
            processOrderItems(order, allParams);

            System.out.println("Nombre d'OrderItems après traitement: " + order.getOrderItems().size());

            // Valider et calculer les totaux
            validateAndCalculateOrderItems(order);
            order.calculateTotals();

            // Sauvegarder la commande - le cascade ALL va automatiquement sauvegarder les OrderItems
            Order savedOrder = orderRepository.save(order);

            System.out.println("Commande sauvegardée avec ID: " + savedOrder.getId());
            System.out.println("OrderItems sauvegardés: " + savedOrder.getOrderItems().size());

            String message = "Commande créée avec succès (" + savedOrder.getOrderItems().size() + " articles)";
            redirectAttributes.addFlashAttribute("success", message);

            return "redirect:/orders/" + savedOrder.getId();

        } catch (Exception e) {
            System.err.println("Erreur lors de la sauvegarde: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la sauvegarde: " + e.getMessage());
            return "redirect:/orders/new";
        }
    }

    @PostMapping("/{id}")
    @Transactional
    public String updateOrder(
            @PathVariable Long id,
            @ModelAttribute Order order,
            @RequestParam(required = false) String action,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        try {
            // Récupérer la commande existante
            Optional<Order> existingOrderOpt = orderRepository.findById(id);
            if (existingOrderOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Commande non trouvée");
                return "redirect:/orders";
            }

            Order existingOrder = existingOrderOpt.get();

            // Vérifier si la commande peut être modifiée
            if (existingOrder.getStatus() == Order.OrderStatus.DELIVERED ||
                    existingOrder.getStatus() == Order.OrderStatus.CANCELLED) {
                redirectAttributes.addFlashAttribute("error", "Cette commande ne peut plus être modifiée");
                return "redirect:/orders/" + id;
            }

            // Mettre à jour les champs modifiables
            existingOrder.setClient(order.getClient());
            existingOrder.setBillingAddress(order.getBillingAddress());
            existingOrder.setShippingAddress(order.getShippingAddress());
            existingOrder.setNotes(order.getNotes());
            existingOrder.setInternalNotes(order.getInternalNotes());
            existingOrder.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
            existingOrder.setDiscountRate(order.getDiscountRate());
            existingOrder.setShippingCost(order.getShippingCost());

            // Traiter les OrderItems mis à jour
            processOrderItems(existingOrder, allParams);

            // Définir le statut selon l'action
            if ("confirm".equals(action) && existingOrder.getStatus() == Order.OrderStatus.DRAFT) {
                existingOrder.setStatus(Order.OrderStatus.CONFIRMED);
            }

            // Calculer les totaux des items individuels puis de la commande
            validateAndCalculateOrderItems(existingOrder);
            existingOrder.calculateTotals();

            Order savedOrder = orderRepository.save(existingOrder);

            System.out.println("Mise à jour - OrderItems sauvegardés: " + savedOrder.getOrderItems().size());

            String message = "Commande mise à jour avec succès";
            if ("confirm".equals(action)) {
                message = "Commande confirmée avec succès";
            }
            redirectAttributes.addFlashAttribute("success", message);

            return "redirect:/orders/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la mise à jour: " + e.getMessage());
            return "redirect:/orders/" + id + "/edit";
        }
    }

    @GetMapping("/{id}")
    public String viewOrder(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Optional<Order> orderOpt = orderRepository.findByIdWithOrderItems(id);
            if (orderOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Commande non trouvée");
                return "redirect:/orders";
            }

            Order order = orderOpt.get();

            // Forcer le chargement des relations nécessaires
            order.getOrderItems().size();
            order.getOrderItems().forEach(item -> item.getProduct().getName());
            order.getClient().getName();
            order.getUser().getUsername();
            if (order.getInvoice() != null) {
                order.getInvoice().getId();
            }


            // Récupérer explicitement la liste des articles commandés
            List<OrderItem> orderItems = order.getOrderItems();


            // Afficher les détails de chaque article pour debug
            orderItems.forEach(item -> {
                System.out.println("Article: " + item.getProduct().getName() +
                        ", Qté: " + item.getQuantity() +
                        ", Prix unitaire: " + item.getUnitPrice() +
                        ", Total HT: " + item.getTotalPriceHT());
            });

            // Recalculer les totaux si nécessaire
            order.getOrderItems().forEach(OrderItem::calculateTotals);
            order.calculateTotals();

            // Sauvegarder pour persister les totaux mis à jour
            orderRepository.save(order);

            // Ajouter les données au modèle
            model.addAttribute("order", order);
            model.addAttribute("orderItems", orderItems);
            return "orders/detail";

        } catch (Exception e) {
            System.err.println("Erreur lors du chargement de la commande: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Erreur lors du chargement de la commande: " + e.getMessage());
            return "redirect:/orders";
        }
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

    // API endpoint pour récupérer les produits (appelé par JavaScript)
    @GetMapping("/api/products")
    @ResponseBody
    public List<Map<String, Object>> getProductsApi() {
        List<Product> products = productRepository.findAll().stream()
                .filter(Product::getIsActive)
                .toList();

        return products.stream().map(product -> {
            Map<String, Object> productMap = new HashMap<>();
            productMap.put("id", product.getId());
            productMap.put("name", product.getName());
            productMap.put("reference", product.getReference());
            productMap.put("unitPrice", product.getUnitPrice());
            productMap.put("stock", product.getStock());
            productMap.put("category", product.getCategory());
            productMap.put("unit", product.getUnit());
            productMap.put("vatRate", product.getVatRate());
            productMap.put("isActive", product.getIsActive());
            return productMap;
        }).collect(Collectors.toList());
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

    private void processOrderItems(Order order, Map<String, String> allParams) {
        System.out.println("=== TRAITEMENT DES ORDERITEMS ===");

        // S'assurer que la liste existe
        if (order.getOrderItems() == null) {
            order.setOrderItems(new ArrayList<>());
        }

        // Nettoyer les anciens items si c'est une modification
        if (order.getId() != null) {
            // Supprimer explicitement les anciens items de la base de données
            if (!order.getOrderItems().isEmpty()) {
                orderItemsRepository.deleteAll(order.getOrderItems());
            }
            order.getOrderItems().clear();
        }

        // Grouper les paramètres par index d'item
        Map<Integer, Map<String, String>> itemsData = groupOrderItemsParams(allParams);
        System.out.println("Nombre d'items à traiter: " + itemsData.size());

        // Créer les OrderItems
        for (Map.Entry<Integer, Map<String, String>> entry : itemsData.entrySet()) {
            System.out.println("Traitement item " + entry.getKey() + ": " + entry.getValue());
            createOrderItemFromData(order, entry.getValue());
        }

        System.out.println("OrderItems créés: " + order.getOrderItems().size());
    }

    private String generateOrderNumber() {
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

        try {
            String monthPattern = "CMD-" + datePrefix + "%";
            int nextNumber = orderRepository.findNextOrderNumberForMonth(monthPattern);
            String orderNumber = String.format("CMD-%s-%04d", datePrefix, nextNumber);

            // Vérification finale de sécurité
            if (orderRepository.existsByOrderNumber(orderNumber)) {
                orderNumber = String.format("CMD-%s-%d", datePrefix, System.currentTimeMillis() % 10000);
            }

            return orderNumber;

        } catch (Exception e) {
            // Fallback simple avec timestamp
            return String.format("CMD-%s-%d", datePrefix, System.currentTimeMillis() % 10000);
        }
    }

    // Méthodes utilitaires pour optimiser le code

    private Map<Integer, Map<String, String>> groupOrderItemsParams(Map<String, String> allParams) {
        Map<Integer, Map<String, String>> itemsData = new HashMap<>();

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.startsWith("orderItems[") && key.contains("]")) {
                try {
                    int startIndex = key.indexOf('[') + 1;
                    int endIndex = key.indexOf(']');
                    int itemIndex = Integer.parseInt(key.substring(startIndex, endIndex));

                    String property = key.substring(endIndex + 2);

                    if (value != null && !value.trim().isEmpty()) {
                        itemsData.computeIfAbsent(itemIndex, k -> new HashMap<>()).put(property, value);
                    }
                } catch (Exception ignored) {
                    // Ignorer les erreurs de parsing
                }
            }
        }

        return itemsData;
    }


    private void createOrderItemFromData(Order order, Map<String, String> itemData) {
        try {
            String productIdStr = itemData.get("productId");
            System.out.println("Création OrderItem pour produit ID: " + productIdStr);

            if (productIdStr != null && !productIdStr.trim().isEmpty()) {
                Long productId = Long.parseLong(productIdStr);
                Optional<Product> productOpt = productRepository.findById(productId);

                if (productOpt.isPresent()) {
                    Product product = productOpt.get();
                    OrderItem orderItem = new OrderItem();

                    orderItem.setOrder(order);
                    orderItem.setProduct(product);

                    // Définir les valeurs avec des défauts sécurisés
                    String quantityStr = itemData.getOrDefault("quantity", "1");
                    String unitPriceStr = itemData.getOrDefault("unitPrice",
                            product.getUnitPrice().toString());
                    String discountRateStr = itemData.getOrDefault("discountRate", "0");
                    String vatRateStr = "20";

                    orderItem.setQuantity(Integer.parseInt(quantityStr));
                    orderItem.setUnitPrice(new BigDecimal(unitPriceStr));
                    orderItem.setDiscountRate(new BigDecimal(discountRateStr));
                    orderItem.setVatRate(new BigDecimal(vatRateStr));

                    // Calculer les totaux avant d'ajouter
                    orderItem.calculateTotals();
                    order.addOrderItem(orderItem);

                    System.out.println("OrderItem créé: " + product.getName() +
                            ", Qté: " + orderItem.getQuantity() +
                            ", Prix: " + orderItem.getUnitPrice() +
                            ", Total HT: " + orderItem.getTotalPriceHT());
                } else {
                    System.err.println("Produit non trouvé avec ID: " + productId);
                }
            } else {
                System.err.println("ProductId manquant dans les données: " + itemData);
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la création d'un OrderItem: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void validateAndCalculateOrderItems(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            if (item.getUnitPrice() == null) {
                item.setUnitPrice(BigDecimal.ZERO);
            }
            if (item.getQuantity() == null) {
                item.setQuantity(1);
            }
            if (item.getVatRate() == null) {
                item.setVatRate(BigDecimal.valueOf(20));
            }

            item.setOrder(order);
            item.calculateTotals();
        }
    }

    private Order loadOrderRelations(Order order) {
        // Forcer le chargement des relations nécessaires
        order.getOrderItems().size();
        order.getOrderItems().forEach(item -> item.getProduct().getName());
        order.getClient().getName();
        order.getUser().getUsername();
        if (order.getInvoice() != null) {
            order.getInvoice().getId();
        }
        return order;
    }

    private void recalculateOrderTotals(Order order) {
        order.getOrderItems().forEach(OrderItem::calculateTotals);
        order.calculateTotals();
    }
}