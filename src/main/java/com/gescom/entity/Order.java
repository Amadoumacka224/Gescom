package com.gescom.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@EqualsAndHashCode(exclude = {"orderItems", "invoice", "client", "user"})
@ToString(exclude = {"orderItems", "invoice", "client", "user"})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 50, message = "Le numéro de commande ne peut pas dépasser 50 caractères")
    @Column(name = "order_number", unique = true, nullable = false)
    private String orderNumber;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @Column(name = "expected_delivery_date")
    private LocalDateTime expectedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "discount_rate", precision = 5, scale = 2)
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_cost", precision = 10, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "total_amount_ht", precision = 10, scale = 2)
    private BigDecimal totalAmountHT = BigDecimal.ZERO;

    @Column(name = "total_vat_amount", precision = 10, scale = 2)
    private BigDecimal totalVatAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();
    
    // Méthode utilitaire pour ajouter un OrderItem avec relation bidirectionnelle
    public void addOrderItem(OrderItem orderItem) {
        if (orderItems == null) {
            orderItems = new ArrayList<>();
        }
        orderItems.add(orderItem);
        orderItem.setOrder(this);
    }
    
    // Méthode utilitaire pour supprimer un OrderItem
    public void removeOrderItem(OrderItem orderItem) {
        if (orderItems != null) {
            orderItems.remove(orderItem);
            orderItem.setOrder(null);
        }
    }

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Invoice invoice;

    // Audit
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Enums
    public enum OrderStatus {
        DRAFT("Brouillon"),
        CONFIRMED("Confirmée"),
        PROCESSING("En traitement"),
        SHIPPED("Expédiée"),
        DELIVERED("Livrée"),
        CANCELLED("Annulée"),
        RETURNED("Retournée"),
        PENDING("En attente"); // Added constant

        private final String displayName;

        OrderStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // src/main/java/com/gescom/entity/Order.java




    public enum PaymentStatus {
        PENDING("En attente"),
        PAID("Payée"),
        PARTIAL("Partiel"),
        OVERDUE("En retard"),
        CANCELLED("Annulée");

        private final String displayName;

        PaymentStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Méthodes utilitaires
    public void calculateTotals() {
        if (orderItems == null || orderItems.isEmpty()) {
            this.totalAmountHT = BigDecimal.ZERO;
            this.totalVatAmount = BigDecimal.ZERO;
            this.totalAmount = BigDecimal.ZERO;
            this.discountAmount = BigDecimal.ZERO;
            System.out.println("Order calculateTotals - Aucun OrderItem, totaux à zéro");
            return;
        }

        BigDecimal subtotalHT = orderItems.stream()
                .map(item -> item.getTotalPriceHT() != null ? item.getTotalPriceHT() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal subtotalVAT = orderItems.stream()
                .map(item -> item.getTotalVatAmount() != null ? item.getTotalVatAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Application de la remise
        if (discountRate != null && discountRate.compareTo(BigDecimal.ZERO) > 0) {
            discountAmount = subtotalHT.multiply(discountRate).divide(BigDecimal.valueOf(100));
        } else {
            discountAmount = BigDecimal.ZERO;
        }

        this.totalAmountHT = subtotalHT.subtract(discountAmount);
        this.totalVatAmount = subtotalVAT;
        this.totalAmount = totalAmountHT.add(totalVatAmount).add(shippingCost != null ? shippingCost : BigDecimal.ZERO);
        
        System.out.println("Order calculateTotals - Items: " + orderItems.size() + 
                         ", Subtotal HT: " + subtotalHT + 
                         ", Total HT: " + totalAmountHT + 
                         ", Total TTC: " + totalAmount);
    }

    public int getTotalQuantity() {
        return orderItems.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    public boolean canBeModified() {
        return status == OrderStatus.DRAFT || status == OrderStatus.CONFIRMED;
    }

    public boolean canBeCancelled() {
        return status != OrderStatus.CANCELLED &&
                status != OrderStatus.DELIVERED &&
                status != OrderStatus.RETURNED;
    }


}