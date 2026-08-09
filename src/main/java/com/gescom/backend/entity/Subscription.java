package com.gescom.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Abonnement d'une entreprise a une formule.
 *
 * Le montant est fige a la souscription plutot que relu depuis {@link Plan} : une revision
 * du catalogue ne doit pas reecrire retroactivement le MRR des contrats en cours ni les
 * encaissements deja journalises. C'est {@code amount} qui fait foi, pas le tarif courant.
 *
 * Resilier ne supprime pas la ligne : le statut bascule et {@code canceledAt} est
 * renseigne. Sans cette trace, le churn ne serait pas calculable — c'est la raison d'etre
 * de l'index partiel {@code uq_subscriptions_active_per_company}, qui n'impose l'unicite
 * qu'aux contrats vivants et laisse l'historique s'accumuler.
 */
@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    /** Statuts qui comptent comme un abonnement vivant — miroir de l'index partiel en base. */
    public static final Set<SubscriptionStatus> LIVE_STATUSES =
            Set.of(SubscriptionStatus.TRIALING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.TRIALING;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BillingPeriod billingPeriod = BillingPeriod.MONTHLY;

    @NotNull(message = "Le montant de l'abonnement est obligatoire")
    @DecimalMin(value = "0.0", message = "Le montant ne peut pas etre negatif")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime currentPeriodStart;

    /** Date de renouvellement ; sert aussi a reperer les echeances depassees. */
    @NotNull
    @Column(nullable = false)
    private LocalDateTime currentPeriodEnd;

    @Column
    private LocalDateTime canceledAt;

    @Size(max = 255)
    @Column(length = 255)
    private String cancelReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (startedAt == null) startedAt = now;
        if (currentPeriodStart == null) currentPeriodStart = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Contribution de cet abonnement au revenu mensuel recurrent.
     *
     * Un contrat annuel est ramene au douzieme : c'est la definition meme du MRR, et sans
     * cette mensualisation un client annuel ferait bondir l'indicateur d'un facteur douze
     * le mois de sa souscription. Le resultat garde six decimales et n'est arrondi qu'une
     * fois, apres sommation, par {@code PlatformMetricsService} — arrondir chaque ligne au
     * centime derive de plusieurs euros sur un parc de quelques centaines d'abonnes.
     */
    public BigDecimal monthlyAmount() {
        if (amount == null) return BigDecimal.ZERO;
        return billingPeriod == BillingPeriod.YEARLY
                ? amount.divide(MONTHS_PER_YEAR, 6, RoundingMode.HALF_UP)
                : amount.setScale(6, RoundingMode.HALF_UP);
    }

    /** Vrai tant que le contrat n'est ni resilie ni expire. */
    public boolean isLive() {
        return LIVE_STATUSES.contains(status);
    }

    /**
     * Cycle de vie du contrat.
     *
     * PAST_DUE est distinct de CANCELED a dessein : l'echeance est impayee mais le contrat
     * court toujours, il pese donc encore dans le MRR et n'est pas comptabilise en churn.
     */
    public enum SubscriptionStatus {
        TRIALING,  // Periode d'essai, pas encore facturee
        ACTIVE,    // En cours et a jour
        PAST_DUE,  // Echeance impayee, contrat maintenu
        CANCELED,  // Resilie a la demande du client ou par l'operateur
        EXPIRED    // Arrive a terme sans renouvellement
    }

    public enum BillingPeriod {
        MONTHLY, YEARLY
    }
}
