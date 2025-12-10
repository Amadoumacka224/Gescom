package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Order;
import com.gescom.backend.entity.OrderItem;
import com.gescom.backend.entity.Product;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.ProductRepository;
import com.gescom.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogService activityLogService;

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
            // Don't fail business operation if logging fails
        }
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public Optional<Order> getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    public List<Order> getOrdersByClient(Long clientId) {
        return orderRepository.findByClientId(clientId);
    }

    public List<Order> getOrdersByUser(Long userId) {
        return orderRepository.findByCreatedById(userId);
    }

    public List<Order> getOrdersByStatus(Order.OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> getOrdersByDateRange(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findByCreatedAtBetween(start, end);
    }

    public Order createOrder(Order order) {
        // Get authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Set user for the order
        order.setCreatedBy(user);

        // Calculate totals
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            // Check stock
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("Insufficient stock for product: " + product.getName());
            }

            item.setUnitPrice(product.getSellingPrice());
            item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            totalAmount = totalAmount.add(item.getTotalPrice());
            item.setOrder(order);
        }

        order.setTotalAmount(totalAmount);
        order.setFinalAmount(totalAmount.subtract(order.getDiscount()).add(order.getTax()));

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Update stock
        for (OrderItem item : savedOrder.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId()).get();
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            productRepository.save(product);
        }

        // Log activity
        logActivity(ActivityLog.ActionType.SALE, "Order", savedOrder.getId(),
            "Création de la commande " + savedOrder.getOrderNumber() + " - Montant: " + savedOrder.getFinalAmount());

        return savedOrder;
    }

    public Order updateOrder(Long id, Order updatedOrder) {
        Order existingOrder = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Restaurer le stock des anciens articles
        for (OrderItem item : existingOrder.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId()).get();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        // Mettre à jour les champs modifiables
        existingOrder.setStatus(updatedOrder.getStatus());

        // Effacer les anciens items et ajouter les nouveaux
        existingOrder.getItems().clear();

        // Calculer le nouveau total
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem item : updatedOrder.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            // Vérifier le stock
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("Insufficient stock for product: " + product.getName());
            }

            item.setUnitPrice(product.getSellingPrice());
            item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            totalAmount = totalAmount.add(item.getTotalPrice());
            item.setOrder(existingOrder);
            existingOrder.getItems().add(item);

            // Déduire le nouveau stock
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            productRepository.save(product);
        }

        existingOrder.setTotalAmount(totalAmount);
        existingOrder.setFinalAmount(totalAmount.subtract(existingOrder.getDiscount()).add(existingOrder.getTax()));

        Order savedOrder = orderRepository.save(existingOrder);

        // Log activity
        logActivity(ActivityLog.ActionType.UPDATE, "Order", savedOrder.getId(),
            "Modification de la commande " + savedOrder.getOrderNumber());

        return savedOrder;
    }

    public Order updateOrderStatus(Long id, Order.OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(status);
        Order savedOrder = orderRepository.save(order);

        // Log activity
        logActivity(ActivityLog.ActionType.UPDATE, "Order", savedOrder.getId(),
            "Changement de statut de la commande " + savedOrder.getOrderNumber() + " vers " + status);

        return savedOrder;
    }

    public void cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() == Order.OrderStatus.CANCELED) {
            throw new RuntimeException("Order is already canceled");
        }

        // Restore stock
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId()).get();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        order.setStatus(Order.OrderStatus.CANCELED);
        orderRepository.save(order);

        // Log activity
        logActivity(ActivityLog.ActionType.UPDATE, "Order", order.getId(),
            "Annulation de la commande " + order.getOrderNumber());
    }

    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        String orderNumber = order.getOrderNumber();
        orderRepository.delete(order);

        // Log activity
        logActivity(ActivityLog.ActionType.DELETE, "Order", id,
            "Suppression de la commande " + orderNumber);
    }
}
