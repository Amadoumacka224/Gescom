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
 * Commande, l'entité centrale du projet. Elle contient ses lignes (OrderItem) en cascade,
 * garde les montants (total, remise, net à facturer) et a sa propre machine à états
 * (enum OrderStatus) qui définit les changements de statut autorisés.
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
     * Entreprise a qui appartient la ligne, c'est ce qui separe les donnees des entreprises.
     *
     * Remplie automatiquement a la creation par TenantEntityListener : ni les services ni
     * les mappers n'ont a s'en occuper, donc pas de risque d'oubli. Pas envoyee en JSON
     * (les controleurs ne renvoient que des DTO).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company ownerCompany;

    @Column(nullable = false, length = 50)
    private String orderNumber;

    // Optionnel : null pour une vente sans client enregistré.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // Total HT des lignes, remises des lignes déjà enlevées.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // Remise globale en euros (les remises des lignes, elles, sont en %).
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    // Net HT à facturer : totalAmount − discount. C'est sur ce montant qu'InvoiceService
    // applique ensuite la TVA, les deux doivent toujours correspondre.
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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /*
     * Le numéro n'est plus créé ici. C'est DocumentNumberService qui le donne avant
     * l'enregistrement, au format CMD-2026-000042 : il se suit, il est propre à chaque
     * entreprise et repart à zéro chaque année. Un @PrePersist ne peut pas le faire car il n'a
     * pas accès aux repositories, il ne pouvait que fabriquer un numéro avec l'heure.
     *
     * Du coup, une commande ajoutée sans passer par le service n'a pas de numéro et ne respecte
     * pas le NOT NULL. C'est voulu : mieux vaut une erreur claire qu'un numéro basé sur l'heure
     * qui se glisse au milieu de la suite.
     */

    public enum OrderStatus {

        // ── Valeurs (dans l'ordre du cycle de vie) ───────────────────
        // Ordre strict : on ne peut créer la livraison qu'après la facturation.
        PENDING,    // Créée, en attente de confirmation
        CONFIRMED,  // Confirmée - peut être facturée
        INVOICED,   // Facturée - peut être livrée
        DELIVERED,  // Livrée - état commercial final
        CANCELED;   // Annulée - terminal

        // ── Machine à états : changements autorisés ──────────────────
        private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS;

        static {
            Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);
            map.put(PENDING,   Collections.unmodifiableSet(EnumSet.of(CONFIRMED, CANCELED)));
            map.put(CONFIRMED, Collections.unmodifiableSet(EnumSet.of(INVOICED, CANCELED)));
            map.put(INVOICED,  Collections.unmodifiableSet(EnumSet.of(DELIVERED, CANCELED)));
            // DELIVERED est final : on n'annule pas une marchandise déjà livrée
            // (ce serait un retour, ça ne passe pas par ici).
            map.put(DELIVERED, Collections.emptySet());
            map.put(CANCELED,  Collections.emptySet());
            ALLOWED_TRANSITIONS = Collections.unmodifiableMap(map);
        }

        // ── Méthodes publiques ───────────────────────────────────────
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
