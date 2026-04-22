package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.OrderItem;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.InsufficientStockException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository, ActivityLogService activityLogService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }

    private void logActivity(ActivityLog.ActionType actionType, String entity, Long entityId, String description) {
        try {
            Long userId = getCurrentUserId();
            if (userId != null) {
                activityLogService.logActivity(userId, actionType, entity, entityId, description, null, null);
            }
        } catch (Exception e) {
            log.warn("Échec du log d'activité: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByClient(Long clientId) {
        return orderRepository.findByClientId(clientId);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUser(Long userId) {
        return orderRepository.findByCreatedById(userId);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByStatus(Order.OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByDateRange(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findByCreatedAtBetween(start, end);
    }

    public Order createOrder(Order order) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "username", username));

        order.setCreatedBy(user);

        // Lecture des produits avec verrou pessimiste : toute commande concurrente
        // sur les mêmes produits attend la fin de cette transaction.
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produit", item.getProduct().getId()));

            if (product.getStockQuantity() < item.getQuantity()) {
                throw new InsufficientStockException(product.getName(), product.getStockQuantity(), item.getQuantity());
            }

            item.setUnitPrice(product.getSellingPrice());
            item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            totalAmount = totalAmount.add(item.getTotalPrice());
            item.setOrder(order);

            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            productRepository.save(product);
        }

        order.setTotalAmount(totalAmount);
        order.setFinalAmount(totalAmount.subtract(order.getDiscount()).add(order.getTax()));

        Order savedOrder = orderRepository.save(order);

        logActivity(ActivityLog.ActionType.SALE, "Order", savedOrder.getId(),
            "Création de la commande " + savedOrder.getOrderNumber() + " - Montant: " + savedOrder.getFinalAmount());

        return savedOrder;
    }

    public Order updateOrder(Long id, Order updatedOrder) {
        Order existingOrder = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", id));

        // 1) Valider d'abord la disponibilité nette (ancien stock + items retirés - items ajoutés)
        //    Cela évite de restaurer puis échouer : on détecte l'incohérence avant toute mutation.
        java.util.Map<Long, Integer> netDelta = new java.util.HashMap<>();
        for (OrderItem item : existingOrder.getItems()) {
            netDelta.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }
        for (OrderItem item : updatedOrder.getItems()) {
            netDelta.merge(item.getProduct().getId(), -item.getQuantity(), Integer::sum);
        }

        // 2) Verrouiller tous les produits concernés et vérifier que le stock résultant reste positif.
        for (java.util.Map.Entry<Long, Integer> entry : netDelta.entrySet()) {
            Product product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Produit", entry.getKey()));
            int resultingStock = product.getStockQuantity() + entry.getValue();
            if (resultingStock < 0) {
                throw new InsufficientStockException(product.getName(), product.getStockQuantity(), -entry.getValue());
            }
            product.setStockQuantity(resultingStock);
            productRepository.save(product);
        }

        // 3) Appliquer les changements sur la commande (plus de risque d'échec métier sur le stock).
        existingOrder.setStatus(updatedOrder.getStatus());
        existingOrder.getItems().clear();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem item : updatedOrder.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produit", item.getProduct().getId()));

            item.setUnitPrice(product.getSellingPrice());
            item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            totalAmount = totalAmount.add(item.getTotalPrice());
            item.setOrder(existingOrder);
            existingOrder.getItems().add(item);
        }

        existingOrder.setTotalAmount(totalAmount);
        existingOrder.setFinalAmount(totalAmount.subtract(existingOrder.getDiscount()).add(existingOrder.getTax()));

        Order savedOrder = orderRepository.save(existingOrder);

        logActivity(ActivityLog.ActionType.UPDATE, "Order", savedOrder.getId(),
            "Modification de la commande " + savedOrder.getOrderNumber());

        return savedOrder;
    }

    public Order updateOrderStatus(Long id, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", id));
        return transitionTo(order, newStatus);
    }

    /**
     * Applique une transition de statut à une commande après validation de la machine à états.
     * Point d'entrée unique pour toute mutation de Order.status — à utiliser depuis les
     * services externes (InvoiceService, DeliveryService) plutôt que setStatus direct.
     */
    public Order transitionTo(Order order, Order.OrderStatus newStatus) {
        validateStatusTransition(order.getStatus(), newStatus);
        Order.OrderStatus previous = order.getStatus();
        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);

        logActivity(ActivityLog.ActionType.UPDATE, "Order", savedOrder.getId(),
            "Changement de statut de la commande " + savedOrder.getOrderNumber()
                + " : " + previous + " → " + newStatus);

        return savedOrder;
    }

    private void validateStatusTransition(Order.OrderStatus current, Order.OrderStatus target) {
        if (current == target) {
            throw new BusinessException("La commande est déjà au statut " + current);
        }
        if (current == Order.OrderStatus.CANCELED) {
            throw new BusinessException("Impossible de modifier une commande annulée");
        }
        if (current == Order.OrderStatus.COMPLETED) {
            throw new BusinessException("Impossible de modifier une commande terminée");
        }

        boolean valid = switch (target) {
            case CONFIRMED -> current == Order.OrderStatus.PENDING;
            case INVOICED -> current == Order.OrderStatus.CONFIRMED;
            case DELIVERED -> current == Order.OrderStatus.CONFIRMED;
            case COMPLETED -> current == Order.OrderStatus.INVOICED || current == Order.OrderStatus.DELIVERED;
            case CANCELED -> true;
            case PENDING -> false;
        };

        if (!valid) {
            throw new BusinessException("Transition de statut invalide : " + current + " → " + target);
        }
    }

    public void cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", id));

        if (order.getStatus() == Order.OrderStatus.CANCELED) {
            throw new BusinessException("La commande est déjà annulée");
        }
        if (order.getStatus() == Order.OrderStatus.COMPLETED) {
            throw new BusinessException("Impossible d'annuler une commande terminée");
        }

        // Restaurer le stock (verrou pessimiste pour éviter les lectures/écritures concurrentes)
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produit", item.getProduct().getId()));
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        order.setStatus(Order.OrderStatus.CANCELED);
        orderRepository.save(order);

        logActivity(ActivityLog.ActionType.UPDATE, "Order", order.getId(),
            "Annulation de la commande " + order.getOrderNumber());
    }

    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", id));

        // Restaurer le stock si la commande n'est pas déjà annulée (verrou pessimiste)
        if (order.getStatus() != Order.OrderStatus.CANCELED) {
            for (OrderItem item : order.getItems()) {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produit", item.getProduct().getId()));
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                productRepository.save(product);
            }
        }

        String orderNumber = order.getOrderNumber();
        orderRepository.delete(order);

        logActivity(ActivityLog.ActionType.DELETE, "Order", id,
            "Suppression de la commande " + orderNumber);
    }
}
