package com.gescom.backend.repository;

import com.gescom.backend.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Abonnements de toutes les entreprises.
 *
 * Tous les calculs de revenus sont faits en base : pas besoin de charger plusieurs
 * centaines de contrats en memoire pour calculer le MRR. Le passage d'un contrat annuel au
 * mois (division par douze) est fait ici en SQL, avec la meme regle que
 * {@code Subscription.monthlyAmount()}.
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** Contrat actif d'une entreprise ; l'index partiel en base garantit qu'il n'y en a qu'un. */
    @Query("""
           SELECT s FROM Subscription s
           WHERE s.company.id = :companyId
             AND s.status IN :liveStatuses
           """)
    Optional<Subscription> findLiveByCompany(Long companyId, Collection<Subscription.SubscriptionStatus> liveStatuses);

    List<Subscription> findByCompanyIdOrderByStartedAtDesc(Long companyId);

    long countByStatus(Subscription.SubscriptionStatus status);

    /**
     * Contrats lies a une formule, tous statuts compris.
     *
     * Les resilies comptent aussi : ils font reference a la formule, et c'est ce qui empeche
     * de supprimer une formule deja vendue (voir {@code PlanService.delete}).
     */
    long countByPlanId(Long planId);

    Page<Subscription> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Subscription> findByStatusOrderByCreatedAtDesc(Subscription.SubscriptionStatus status, Pageable pageable);

    /**
     * Revenu mensuel recurrent (MRR).
     *
     * Un contrat annuel compte pour un douzieme de son montant : c'est la definition du MRR,
     * sinon un abonne annuel multiplierait l'indicateur par douze le mois ou il s'abonne. Le
     * calcul garde toute la precision et n'est arrondi qu'une fois, a la fin, dans
     * {@code PlatformMetricsService}.
     */
    @Query("""
           SELECT COALESCE(SUM(CASE
                     WHEN s.billingPeriod = :yearly
                     THEN s.amount / 12
                     ELSE s.amount END), 0)
           FROM Subscription s
           WHERE s.status IN :liveStatuses
           """)
    java.math.BigDecimal sumMonthlyRecurringRevenue(Collection<Subscription.SubscriptionStatus> liveStatuses,
                                                    Subscription.BillingPeriod yearly);

    /** Nombre de contrats actifs par formule, pour le graphique « abonnements par formule ». */
    @Query("""
           SELECT s.plan.code, s.plan.name, COUNT(s), COALESCE(SUM(CASE
                     WHEN s.billingPeriod = :yearly
                     THEN s.amount / 12
                     ELSE s.amount END), 0)
           FROM Subscription s
           WHERE s.status IN :liveStatuses
           GROUP BY s.plan.code, s.plan.name, s.plan.sortOrder
           ORDER BY s.plan.sortOrder ASC
           """)
    List<Object[]> breakdownByPlan(Collection<Subscription.SubscriptionStatus> liveStatuses,
                                   Subscription.BillingPeriod yearly);

    @Query("SELECT s.status, COUNT(s) FROM Subscription s GROUP BY s.status")
    List<Object[]> countGroupedByStatus();

    /** Resiliations sur une periode (le haut de la fraction du churn). */
    long countByCanceledAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Contrats actifs au debut d'une periode (le bas de la fraction du churn).
     * Un contrat compte s'il a commence avant cette date et n'etait pas encore resilie, donc
     * on n'inclut pas ceux souscrits puis resilies pendant la periode.
     */
    @Query("""
           SELECT COUNT(s) FROM Subscription s
           WHERE s.startedAt < :moment
             AND (s.canceledAt IS NULL OR s.canceledAt >= :moment)
           """)
    long countLiveAt(LocalDateTime moment);

    /** Echeances a renouveler dans la periode donnee, pour le bloc « renouvellements a venir ». */
    @Query("""
           SELECT s FROM Subscription s
           WHERE s.status IN :liveStatuses
             AND s.currentPeriodEnd BETWEEN :from AND :to
           ORDER BY s.currentPeriodEnd ASC
           """)
    List<Subscription> findRenewalsBetween(Collection<Subscription.SubscriptionStatus> liveStatuses,
                                           LocalDateTime from, LocalDateTime to);

    /** Echeances passees et pas encore renouvelees, pour les alertes d'impaye. */
    @Query("""
           SELECT s FROM Subscription s
           WHERE s.status IN :liveStatuses
             AND s.currentPeriodEnd < :moment
           ORDER BY s.currentPeriodEnd ASC
           """)
    List<Subscription> findOverdue(Collection<Subscription.SubscriptionStatus> liveStatuses, LocalDateTime moment);
}
