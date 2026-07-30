package com.gescom.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entité commande, pièce maîtresse du domaine. Elle agrège ses lignes (OrderItem) avec
 * cascade complète, mémorise les montants (total, remise, taxe, montant final) et porte
 * sa propre machine à états (enum OrderStatus) qui régit les transitions de statut autorisées.
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String orderNumber;

    // Optionnel : null pour une vente de passage (client non enregistré).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal finalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        generateOrderNumber();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private void generateOrderNumber() {
        if (orderNumber == null) {
            orderNumber = "CMD-" + System.currentTimeMillis();
        }
    }

    public enum OrderStatus {

        // ── Valeurs (ordre du cycle de vie) ──────────────────────────
        // Flux linéaire strict : la livraison ne peut être créée qu'après la facturation.
        PENDING,    // Créée, en attente de confirmation
        CONFIRMED,  // Confirmée — peut être facturée
        INVOICED,   // Facturée — peut être livrée
        DELIVERED,  // Livrée — état commercial final
        CANCELED;   // Annulée — terminal

        // ── Machine à états : transitions autorisées ─────────────────
        private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS;

        static {
            Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);
            map.put(PENDING,   Collections.unmodifiableSet(EnumSet.of(CONFIRMED, CANCELED)));
            map.put(CONFIRMED, Collections.unmodifiableSet(EnumSet.of(INVOICED, CANCELED)));
            map.put(INVOICED,  Collections.unmodifiableSet(EnumSet.of(DELIVERED, CANCELED)));
            // DELIVERED est terminal : on n'annule pas des marchandises déjà livrées
            // (ce serait un retour, hors périmètre de ce flux).
            map.put(DELIVERED, Collections.emptySet());
            map.put(CANCELED,  Collections.emptySet());
            ALLOWED_TRANSITIONS = Collections.unmodifiableMap(map);
        }

        // ── API publique ─────────────────────────────────────────────
        public Set<OrderStatus> allowedTransitions() {
            return ALLOWED_TRANSITIONS.getOrDefault(this, Collections.emptySet());
        }

        public boolean canTransitionTo(OrderStatus target) {
            return target != null && allowedTransitions().contains(target);
        }

        public boolean isTerminal() {
            return allowedTransitions().isEmpty();
        }
    }
}
