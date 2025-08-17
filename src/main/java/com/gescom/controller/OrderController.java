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
            
            System.out.println("📊 Nombre d'OrderItems après traitement: " + order.getOrderItems().size());
            
            // VÉRIFICATION CRITIQUE : Si aucun OrderItem, on arrête
            if (order.getOrderItems().isEmpty()) {
                System.err.println("❌ ERREUR CRITIQUE: Aucun OrderItem trouvé après traitement!");
                System.err.println("Paramètres allParams qui contiennent 'orderItems':");
                allParams.entrySet().stream()
                    .filter(entry -> entry.getKey().contains("orderItems"))
                    .forEach(entry -> System.err.println("  " + entry.getKey() + " = " + entry.getValue()));
                throw new RuntimeException("Aucun article trouvé dans la commande");
            }
            
            // Valider et calculer les totaux
            validateAndCalculateOrderItems(order);
            order.calculateTotals();

            System.out.println("💾 Sauvegarde de la commande...");
            System.out.println("Items à sauvegarder: " + order.getOrderItems().size());
            order.getOrderItems().forEach(item -> 
                System.out.println("  - " + item.getProduct().getName() + " x" + item.getQuantity())
            );

            // Sauvegarder la commande - le cascade ALL va automatiquement sauvegarder les OrderItems
            Order savedOrder = orderRepository.save(order);
            
            // Force le flush pour s'assurer que tout est persisté immédiatement
            orderRepository.flush();
            
            System.out.println("✅ Commande sauvegardée avec ID: " + savedOrder.getId());
            System.out.println("✅ OrderItems sauvegardés: " + savedOrder.getOrderItems().size());
            
            // Double vérification en base de données
            List<OrderItem> itemsInDB = orderItemsRepository.findByOrderId(savedOrder.getId());
            System.out.println("🔍 Vérification en BD - OrderItems trouvés: " + itemsInDB.size());

            String message = "Commande créée avec succès (" + savedOrder.getOrderItems().size() + " articles)";
            redirectAttributes.addFlashAttribute("success", message);

            return "redirect:/orders/" + savedOrder.getId();

        } catch (Exception e) {
            System.err.println("Erreur lors de la sauvegarde: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la sauvegarde: " + e.getMessage());
            return "redirect:/orders/" + order.getId();
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
    @Transactional(readOnly = true)
    public String viewOrder(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Optional<Order> orderOpt = orderRepository.findByIdWithOrderItems(id);
            if (orderOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Commande non trouvée");
                return "redirect:/orders";
            }

            Order order = orderOpt.get();
            
            System.out.println("🔍 Debug ViewOrder - Commande ID: " + order.getId());
            System.out.println("🔍 Nombre d'OrderItems trouvés: " + order.getOrderItems().size());
            
            // Si aucun OrderItem n'est trouvé, vérifier en base
            if (order.getOrderItems().isEmpty()) {
                System.out.println("⚠️ Aucun OrderItem trouvé dans la relation, vérification directe en base...");
                List<OrderItem> itemsFromRepo = orderItemsRepository.findByOrderId(id);
                System.out.println("🔍 OrderItems trouvés directement en base: " + itemsFromRepo.size());
                
                if (!itemsFromRepo.isEmpty()) {
                    // Réassigner les items trouvés à la commande
                    order.getOrderItems().clear();
                    order.getOrderItems().addAll(itemsFromRepo);
                    itemsFromRepo.forEach(item -> item.setOrder(order));
                    System.out.println("✅ OrderItems réassignés à la commande");
                }
            }
            
            // Afficher les détails de chaque article pour debug
            order.getOrderItems().forEach(item -> {
                System.out.println("📦 Article: " + item.getProduct().getName() + 
                                   ", Qté: " + item.getQuantity() +
                                    ", Prix unitaire: " + item.getUnitPrice() +
                                 ", Total HT: " + item.getTotalPriceHT());
            });
            
            // Recalculer les totaux si nécessaire
            order.getOrderItems().forEach(OrderItem::calculateTotals);
            order.calculateTotals();

            // Ajouter les données au modèle
            model.addAttribute("order", order);
            model.addAttribute("orderItems", order.getOrderItems());
            return "orders/detail";
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du chargement de la commande: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Erreur lors du chargement de la commande: " + e.getMessage());
            return "redirect:/orders";
        }
    }

    @GetMapping("/{id}/edit")
    public String editOrder(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(id);
            if (orderOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Commande non trouvée");
                return "redirect:/orders";
            }

            Order order = orderOpt.get();

            // Charger les articles de la commande
            if (order.getOrderItems().isEmpty()) {
                List<OrderItem> itemsFromRepo = orderItemsRepository.findByOrderId(id);
                if (!itemsFromRepo.isEmpty()) {
                    order.getOrderItems().clear();
                    order.getOrderItems().addAll(itemsFromRepo);
                    itemsFromRepo.forEach(item -> item.setOrder(order));
                }
            }

            // Recalculer les totaux
            order.getOrderItems().forEach(OrderItem::calculateTotals);
            order.calculateTotals();

            model.addAttribute("order", order);
            model.addAttribute("isEdit", true);

            // Données nécessaires pour l'édition complète
            List<Client> clients = clientRepository.findAll().stream()
                    .filter(client -> client.getStatus() == Client.ClientStatus.ACTIVE)
                    .collect(Collectors.toList());
            model.addAttribute("clients", clients);

            List<Product> products = productRepository.findAll().stream()
                    .filter(Product::getIsActive)
                    .collect(Collectors.toList());
            model.addAttribute("products", products);

            // Statuts possibles selon le statut actuel
            List<Order.OrderStatus> possibleStatuses = getPossibleStatusTransitions(order.getStatus());
            model.addAttribute("possibleStatuses", possibleStatuses);

            // Vérifier si on peut facturer - possible pour CONFIRMED, PROCESSING, SHIPPED, DELIVERED
            boolean canInvoice = (order.getStatus() == Order.OrderStatus.CONFIRMED || 
                                order.getStatus() == Order.OrderStatus.PROCESSING ||
                                order.getStatus() == Order.OrderStatus.SHIPPED ||
                                order.getStatus() == Order.OrderStatus.DELIVERED) && 
                               order.getInvoice() == null;
            model.addAttribute("canInvoice", canInvoice);

            return "orders/edit";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors du chargement: " + e.getMessage());
            return "redirect:/orders";
        }
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

    // API endpoint pour débugger une commande
    @GetMapping("/{id}/debug")
    @ResponseBody
    public Map<String, Object> debugOrder(@PathVariable Long id) {
        Map<String, Object> debug = new HashMap<>();
        
        try {
            // 1. Vérifier la commande
            Optional<Order> orderOpt = orderRepository.findById(id);
            debug.put("orderExists", orderOpt.isPresent());
            
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                debug.put("orderId", order.getId());
                debug.put("orderNumber", order.getOrderNumber());
                debug.put("orderItemsFromRelation", order.getOrderItems().size());
                
                // 2. Vérifier les OrderItems directement en base
                List<OrderItem> itemsFromRepo = orderItemsRepository.findByOrderId(id);
                debug.put("orderItemsFromRepository", itemsFromRepo.size());
                
                // 3. Vérifier avec requête SQL native
                List<OrderItem> itemsFromNativeQuery = orderItemsRepository.findByOrderIdNative(id);
                debug.put("orderItemsFromNativeSQL", itemsFromNativeQuery.size());
                
                // 4. Compter avec COUNT SQL
                Long countFromDB = orderItemsRepository.countByOrderId(id);
                debug.put("countFromDatabase", countFromDB);
                
                // 5. Détails des items
                List<Map<String, Object>> itemDetails = new ArrayList<>();
                for (OrderItem item : itemsFromRepo) {
                    Map<String, Object> itemDebug = new HashMap<>();
                    itemDebug.put("id", item.getId());
                    itemDebug.put("orderId", item.getOrder() != null ? item.getOrder().getId() : "NULL");
                    itemDebug.put("productId", item.getProduct() != null ? item.getProduct().getId() : "NULL");
                    itemDebug.put("productName", item.getProduct() != null ? item.getProduct().getName() : "NULL");
                    itemDebug.put("quantity", item.getQuantity());
                    itemDebug.put("unitPrice", item.getUnitPrice());
                    itemDebug.put("totalPriceHT", item.getTotalPriceHT());
                    itemDetails.add(itemDebug);
                }
                debug.put("itemDetails", itemDetails);
            }
        } catch (Exception e) {
            debug.put("error", e.getMessage());
        }
        
        return debug;
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
            case DRAFT -> to == Order.OrderStatus.CONFIRMED || to == Order.OrderStatus.CANCELLED || to == Order.OrderStatus.PENDING;
            case PENDING -> to == Order.OrderStatus.CONFIRMED || to == Order.OrderStatus.CANCELLED;
            case CONFIRMED -> to == Order.OrderStatus.PROCESSING || to == Order.OrderStatus.CANCELLED || to == Order.OrderStatus.PENDING;
            case PROCESSING -> to == Order.OrderStatus.SHIPPED || to == Order.OrderStatus.CANCELLED || to == Order.OrderStatus.PENDING;
            case SHIPPED -> to == Order.OrderStatus.DELIVERED || to == Order.OrderStatus.RETURNED;
            case DELIVERED -> to == Order.OrderStatus.RETURNED; // Possibilité de retour après livraison
            case RETURNED -> false; // Aucune transition possible depuis RETURNED
            case CANCELLED -> false; // Aucune transition possible depuis CANCELLED
            default -> false;
        };
    }

    private String getStatusDisplayName(Order.OrderStatus status) {
        return switch (status) {
            case DRAFT -> "Brouillon";
            case CONFIRMED -> "Confirmée";
            case PROCESSING -> "En traitement";
            case SHIPPED -> "Expédiée";
            case DELIVERED -> "Livrée";
            case CANCELLED -> "Annulée";
            case RETURNED -> "Retournée";
            case PENDING -> "En attente";
            default -> status.name();
        };
    }

    private void processOrderItems(Order order, Map<String, String> allParams) {
        System.out.println("=== TRAITEMENT DES ORDERITEMS ===");
        System.out.println("Tous les paramètres reçus:");
        allParams.entrySet().stream()
            .filter(entry -> entry.getKey().contains("orderItems"))
            .forEach(entry -> System.out.println("  " + entry.getKey() + " = " + entry.getValue()));
        
        // S'assurer que la liste existe
        if (order.getOrderItems() == null) {
            order.setOrderItems(new ArrayList<>());
        }
        
        // Nettoyer les anciens items si c'est une modification
        if (order.getId() != null) {
            System.out.println("Modification d'une commande existante - nettoyage des anciens items");
            // Supprimer explicitement les anciens items de la base de données
            if (!order.getOrderItems().isEmpty()) {
                orderItemsRepository.deleteAll(order.getOrderItems());
            }
            order.getOrderItems().clear();
        }

        // Grouper les paramètres par index d'item
        Map<Integer, Map<String, String>> itemsData = groupOrderItemsParams(allParams);
        System.out.println("Nombre d'items groupés: " + itemsData.size());
        
        if (itemsData.isEmpty()) {
            System.out.println("⚠️ AUCUN ORDERITEM TROUVÉ DANS LES PARAMÈTRES!");
            return;
        }
        
        // Créer les OrderItems
        for (Map.Entry<Integer, Map<String, String>> entry : itemsData.entrySet()) {
            System.out.println("🔄 Traitement item " + entry.getKey() + ": " + entry.getValue());
            createOrderItemFromData(order, entry.getValue());
        }
        
        System.out.println("✅ OrderItems créés au final: " + order.getOrderItems().size());
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
        
        System.out.println("🔍 Analyse des paramètres orderItems:");
        
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            if (key.startsWith("orderItems[") && key.contains("]")) {
                System.out.println("  Paramètre trouvé: " + key + " = " + value);
                try {
                    int startIndex = key.indexOf('[') + 1;
                    int endIndex = key.indexOf(']');
                    int itemIndex = Integer.parseInt(key.substring(startIndex, endIndex));
                    
                    String property = key.substring(endIndex + 2); // Enlever "].
                    System.out.println("    -> Index: " + itemIndex + ", Propriété: " + property);
                    
                    if (value != null && !value.trim().isEmpty()) {
                        itemsData.computeIfAbsent(itemIndex, k -> new HashMap<>()).put(property, value);
                        System.out.println("    -> ✅ Ajouté à l'index " + itemIndex);
                    } else {
                        System.out.println("    -> ❌ Valeur vide ou null");
                    }
                } catch (Exception e) {
                    System.err.println("    -> ❌ Erreur de parsing: " + e.getMessage());
                }
            }
        }
        
        System.out.println("📊 Résultat du groupement:");
        itemsData.forEach((index, data) -> {
            System.out.println("  Item " + index + ": " + data);
        });
        
        return itemsData;
    }


    private void createOrderItemFromData(Order order, Map<String, String> itemData) {
        try {
            System.out.println("=== Création OrderItem ===");
            System.out.println("Données reçues: " + itemData);
            
            // Essayer d'abord product.id, puis productId
            String productIdStr = itemData.get("product.id");
            if (productIdStr == null || productIdStr.trim().isEmpty()) {
                productIdStr = itemData.get("productId");
            }
            
            System.out.println("Product ID extrait: " + productIdStr);
            
            if (productIdStr != null && !productIdStr.trim().isEmpty()) {
                Long productId = Long.parseLong(productIdStr);
                Optional<Product> productOpt = productRepository.findById(productId);
                
                if (productOpt.isPresent()) {
                    Product product = productOpt.get();
                    
                    // Vérifier si ce produit n'est pas déjà dans la commande
                    boolean alreadyExists = order.getOrderItems().stream()
                        .anyMatch(item -> item.getProduct().getId().equals(productId));
                    
                    if (alreadyExists) {
                        System.out.println("⚠️ Produit ID " + productId + " déjà présent dans la commande - ignoré");
                        return;
                    }
                    
                    OrderItem orderItem = new OrderItem();
                    
                    orderItem.setOrder(order);
                    orderItem.setProduct(product);
                    
                    // Définir les valeurs avec des défauts sécurisés
                    String quantityStr = itemData.getOrDefault("quantity", "1");
                    String unitPriceStr = itemData.getOrDefault("unitPrice",
                            product.getUnitPrice().toString());
                    String discountRateStr = itemData.getOrDefault("discountRate", "0");
                    String vatRateStr = itemData.getOrDefault("vatRate", "20");
                    
                    try {
                        orderItem.setQuantity(Integer.parseInt(quantityStr));
                        orderItem.setUnitPrice(new BigDecimal(unitPriceStr));
                        orderItem.setDiscountRate(new BigDecimal(discountRateStr));
                        orderItem.setVatRate(new BigDecimal(vatRateStr));
                        
                        // Calculer les totaux avant d'ajouter
                        orderItem.calculateTotals();
                        order.addOrderItem(orderItem);
                        
                        System.out.println("✅ OrderItem créé avec succès: " + product.getName() + 
                                         ", Qté: " + orderItem.getQuantity() + 
                                         ", Prix: " + orderItem.getUnitPrice() + 
                                         ", Total HT: " + orderItem.getTotalPriceHT());
                    } catch (NumberFormatException e) {
                        System.err.println("Erreur de conversion numérique: " + e.getMessage());
                        System.err.println("Quantity: " + quantityStr + ", UnitPrice: " + unitPriceStr);
                    }
                } else {
                    System.err.println("❌ Produit non trouvé avec ID: " + productId);
                }
            } else {
                System.err.println("❌ ProductId manquant dans les données: " + itemData);
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la création d'un OrderItem: " + e.getMessage());
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

    private List<Order.OrderStatus> getPossibleStatusTransitions(Order.OrderStatus currentStatus) {
        List<Order.OrderStatus> possibleStatuses = new ArrayList<>();
        
        switch (currentStatus) {
            case DRAFT:
                possibleStatuses.add(Order.OrderStatus.CONFIRMED);
                possibleStatuses.add(Order.OrderStatus.PENDING);
                possibleStatuses.add(Order.OrderStatus.CANCELLED);
                break;
            case PENDING:
                possibleStatuses.add(Order.OrderStatus.CONFIRMED);
                possibleStatuses.add(Order.OrderStatus.CANCELLED);
                break;
            case CONFIRMED:
                possibleStatuses.add(Order.OrderStatus.PROCESSING);
                possibleStatuses.add(Order.OrderStatus.PENDING);
                possibleStatuses.add(Order.OrderStatus.CANCELLED);
                break;
            case PROCESSING:
                possibleStatuses.add(Order.OrderStatus.SHIPPED);
                possibleStatuses.add(Order.OrderStatus.PENDING);
                possibleStatuses.add(Order.OrderStatus.CANCELLED);
                break;
            case SHIPPED:
                possibleStatuses.add(Order.OrderStatus.DELIVERED);
                possibleStatuses.add(Order.OrderStatus.RETURNED);
                break;
            case DELIVERED:
                possibleStatuses.add(Order.OrderStatus.RETURNED);
                break;
            case RETURNED:
            case CANCELLED:
                // Aucune transition possible
                break;
        }
        
        return possibleStatuses;
    }

    @PostMapping("/{id}/update")
    @Transactional
    public String updateOrder(
            @PathVariable Long id,
            @RequestParam Map<String, String> allParams,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
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

            // Mettre à jour le client si changé
            String clientIdStr = allParams.get("client.id");
            if (clientIdStr != null && !clientIdStr.trim().isEmpty()) {
                Long clientId = Long.parseLong(clientIdStr);
                Optional<Client> clientOpt = clientRepository.findById(clientId);
                if (clientOpt.isPresent()) {
                    order.setClient(clientOpt.get());
                }
            }

            // Mettre à jour le statut si changé
            String newStatus = allParams.get("status");
            if (newStatus != null && !newStatus.trim().isEmpty()) {
                Order.OrderStatus status = Order.OrderStatus.valueOf(newStatus);
                if (isValidStatusTransition(order.getStatus(), status)) {
                    order.setStatus(status);
                }
            }

            // Traiter les articles de la commande
            processOrderItems(order, allParams);

            // Recalculer les totaux
            validateAndCalculateOrderItems(order);
            recalculateOrderTotals(order);

            // Sauvegarder
            orderRepository.save(order);

            redirectAttributes.addFlashAttribute("success", "Commande modifiée avec succès");
            return "redirect:/orders/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la modification: " + e.getMessage());
            return "redirect:/orders/" + id + "/edit";
        }
    }
}