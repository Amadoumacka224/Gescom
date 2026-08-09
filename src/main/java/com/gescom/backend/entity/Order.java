package com.gescom.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gescom.backend.tenancy.TenantEntityListener;
import com.gescom.backend.tenancy.TenantOwned;
import org.hibernate.annotations.Filter;
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
 * cascade complète, mémorise les montants (total, remise, net à facturer) et porte
 * sa propre machine à états (enum OrderStatus) qui régit les transitions de statut autorisées.
 */
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(name = "uq_orders_company_number", columnNames = {"company_id", "order_number"}))
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Order implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Entreprise proprietaire de la ligne - cle du cloisonnement multi-entreprises.
     *
     * Renseignee automatiquement a la creation par TenantEntityListener : aucun service ni
     * mapper n'a a s'en occuper, ce qui evite qu'un oubli produise une ligne orpheline.
     * Ecartee de la serialisation JSON, les controleurs ne renvoyant que des DTO.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company ownerCompany;

    @Column(nullable = false, length = 50)
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

    // Total HT des lignes, remises de ligne déjà déduites.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // Remise commerciale globale, en euros (les remises de ligne, elles, sont en %).
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    // Net HT à facturer : totalAmount − discount. C'est exactement la base sur laquelle
    // InvoiceService applique ensuite la TVA — les deux montants ne doivent jamais diverger.
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
