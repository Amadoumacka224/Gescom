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
 * Abonnements du parc.
 *
 * Toutes les mesures de revenu sont agregees en base : le MRR d'un parc de plusieurs
 * centaines de contrats n'a aucune raison de transiter par la memoire de l'application.
 * La mensualisation d'un contrat annuel (division par douze) est faite ici, en SQL, avec
 * la meme regle que {@code Subscription.monthlyAmount()}.
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** Contrat vivant d'une entreprise ; l'index partiel en base en garantit l'unicite. */
    @Query("""
           SELECT s FROM Subscription s
           WHERE s.company.id = :companyId
             AND s.status IN :liveStatuses
           """)
    Optional<Subscription> findLiveByCompany(Long companyId, Collection<Subscription.SubscriptionStatus> liveStatuses);

    List<Subscription> findByCompanyIdOrderByStartedAtDesc(Long companyId);

    long countByStatus(Subscription.SubscriptionStatus status);

    /**
     * Contrats rattaches a une formule, tous statuts confondus.
     *
     * Les resilies comptent : l'historique reference la formule, et c'est ce qui interdit
     * de supprimer une formule deja vendue (voir {@code PlanService.delete}).
     */
    long countByPlanId(Long planId);

    Page<Subscription> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Subscription> findByStatusOrderByCreatedAtDesc(Subscription.SubscriptionStatus status, Pageable pageable);

    /**
     * Revenu mensuel recurrent.
     *
     * Un contrat annuel compte pour un douzieme de son montant : c'est la definition du MRR,
     * et sans cette mensualisation un abonne annuel gonflerait l'indicateur d'un facteur
     * douze le mois de sa souscription. Le calcul reste en precision native et n'est arrondi
     * qu'une fois, au retour, par {@code PlatformMetricsService}.
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

    /** Repartition des contrats vivants par formule — camembert « abonnements par formule ». */
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

    /** Resiliations sur une periode — numerateur du churn. */
    long countByCanceledAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Contrats vivants au debut d'une periode — denominateur du churn.
     * Un contrat compte s'il avait demarre avant la borne et n'etait pas encore resilie a
     * cette date, ce qui exclut ceux souscrits puis resilies a l'interieur de la periode.
     */
    @Query("""
           SELECT COUNT(s) FROM Subscription s
           WHERE s.startedAt < :moment
             AND (s.canceledAt IS NULL OR s.canceledAt >= :moment)
           """)
    long countLiveAt(LocalDateTime moment);

    /** Echeances a renouveler dans la fenetre donnee — bloc « renouvellements a venir ». */
    @Query("""
           SELECT s FROM Subscription s
           WHERE s.status IN :liveStatuses
             AND s.currentPeriodEnd BETWEEN :from AND :to
           ORDER BY s.currentPeriodEnd ASC
           """)
    List<Subscription> findRenewalsBetween(Collection<Subscription.SubscriptionStatus> liveStatuses,
                                           LocalDateTime from, LocalDateTime to);

    /** Echeances depassees et toujours non renouvelees — alertes d'impaye. */
    @Query("""
           SELECT s FROM Subscription s
           WHERE s.status IN :liveStatuses
             AND s.currentPeriodEnd < :moment
           ORDER BY s.currentPeriodEnd ASC
           """)
    List<Subscription> findOverdue(Collection<Subscription.SubscriptionStatus> liveStatuses, LocalDateTime moment);
}
