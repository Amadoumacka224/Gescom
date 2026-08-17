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

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


/**
 * Entité livraison (bon de livraison), liée à une commande en relation 1-1.
 * Porte l'adresse et le contact de livraison, la date planifiée vs. effective, et une
 * machine à états simple (PENDING → DELIVERED) garantissant des transitions cohérentes.
 */
@Entity
@Table(name = "deliveries", uniqueConstraints = @UniqueConstraint(name = "uq_deliveries_company_number", columnNames = {"company_id", "delivery_number"}))
@Filter(name = "tenantFilter", condition = "company_id = :tenantCompanyId")
@EntityListeners(TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Delivery implements TenantOwned {

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
    private String deliveryNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 255)
    private String deliveryAddress;

    @Column(length = 100)
    private String deliveryCity;

    @Column(length = 20)
    private String deliveryPostalCode;

    @Column(length = 100)
    private String deliveryCountry;

    @Column(length = 100)
    private String contactName;

    @Column(length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime scheduledDate;

    @Column
    private LocalDateTime deliveredDate;

    @Column(length = 100)
    private String deliveredBy;

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

    /* Numéro attribué par DocumentNumberService — voir le commentaire équivalent sur Order. */

    public enum DeliveryStatus {
        PENDING,    // Planifiée
        DELIVERED;  // Livrée — terminal

        private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED_TRANSITIONS;

        static {
            Map<DeliveryStatus, Set<DeliveryStatus>> map = new EnumMap<>(DeliveryStatus.class);
            map.put(PENDING, EnumSet.of(DELIVERED));
            map.put(DELIVERED, EnumSet.noneOf(DeliveryStatus.class));
            ALLOWED_TRANSITIONS = map;
        }

        public boolean canTransitionTo(DeliveryStatus target) {
            return target != null && ALLOWED_TRANSITIONS.get(this).contains(target);
        }

        public boolean isTerminal() {
            return ALLOWED_TRANSITIONS.get(this).isEmpty();
        }
    }
}
