package com.gescom.backend.service;

import com.gescom.backend.dto.platform.SubscriptionRequest;
import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.Plan;
import com.gescom.backend.entity.Subscription;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.CompanyRepository;
import com.gescom.backend.repository.PlanRepository;
import com.gescom.backend.repository.SubscriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Cycle de vie des abonnements.
 *
 * Une entreprise n'a qu'un contrat vivant a la fois — regle inscrite en base par l'index
 * partiel {@code uq_subscriptions_active_per_company}. Souscrire alors qu'un contrat court
 * deja n'est donc pas une erreur a signaler mais un changement de formule : l'ancien est
 * clos, le nouveau ouvert, et l'historique conserve les deux.
 */
@Service
@Transactional
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final CompanyRepository companyRepository;
    private final PlanRepository planRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               CompanyRepository companyRepository,
                               PlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.companyRepository = companyRepository;
        this.planRepository = planRepository;
    }

    // ── Lectures ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Subscription> getSubscriptions(Subscription.SubscriptionStatus status, Pageable pageable) {
        return status == null
                ? subscriptionRepository.findAllByOrderByCreatedAtDesc(pageable)
                : subscriptionRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public Subscription getById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
    }

    /** Contrat vivant d'une entreprise, s'il en existe un. */
    @Transactional(readOnly = true)
    public Optional<Subscription> getLiveForCompany(Long companyId) {
        return subscriptionRepository.findLiveByCompany(companyId, Subscription.LIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<Subscription> getHistoryForCompany(Long companyId) {
        return subscriptionRepository.findByCompanyIdOrderByStartedAtDesc(companyId);
    }

    // ── Ecritures ────────────────────────────────────────────────────────────

    public Subscription subscribe(SubscriptionRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", request.companyId()));
        Plan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan", request.planId()));
        return subscribe(company, plan, parsePeriod(request.billingPeriod()), request.amount(), false);
    }

    /**
     * Ouvre un contrat, en cloturant celui qui court eventuellement.
     *
     * @param amount tarif negocie ; {@code null} pour appliquer le tarif catalogue. Le montant
     *               retenu est ensuite fige sur le contrat, de sorte qu'une revision du
     *               catalogue ne reecrive pas retroactivement le MRR des contrats en cours.
     * @param trial  ouvre le contrat en TRIALING, sans facturation immediate.
     */
    public Subscription subscribe(Company company, Plan plan, Subscription.BillingPeriod period,
                                  BigDecimal amount, boolean trial) {
        if (Boolean.FALSE.equals(plan.getActive())) {
            throw BusinessException.of("plan.inactive",
                    "La formule " + plan.getName() + " n'est plus proposee a la souscription",
                    plan.getName());
        }
        // Un contrat vivant doit etre clos avant d'en ouvrir un autre : l'index partiel en
        // base refuserait le second, et un changement de formule est precisement cela.
        getLiveForCompany(company.getId())
                .ifPresent(current -> close(current, Subscription.SubscriptionStatus.CANCELED,
                        "Changement de formule"));

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = new Subscription();
        subscription.setCompany(company);
        subscription.setPlan(plan);
        subscription.setBillingPeriod(period);
        subscription.setAmount(amount != null ? amount : catalogPrice(plan, period));
        subscription.setStatus(trial ? Subscription.SubscriptionStatus.TRIALING
                                     : Subscription.SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(now);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(trial
                ? now.plusDays(plan.getTrialDays())
                : nextPeriodEnd(now, period));
        return subscriptionRepository.save(subscription);
    }

    /** Renouvelle le contrat pour une periode de plus. */
    public Subscription renew(Long id) {
        Subscription subscription = getById(id);
        if (!subscription.isLive()) {
            throw BusinessException.of("subscription.notLive",
                    "Un contrat resilie ou expire ne peut pas etre renouvele");
        }
        LocalDateTime start = subscription.getCurrentPeriodEnd().isAfter(LocalDateTime.now())
                ? subscription.getCurrentPeriodEnd()
                : LocalDateTime.now();
        subscription.setCurrentPeriodStart(start);
        subscription.setCurrentPeriodEnd(nextPeriodEnd(start, subscription.getBillingPeriod()));
        // Un renouvellement solde l'echeance impayee qui avait fait basculer en PAST_DUE.
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        return subscriptionRepository.save(subscription);
    }

    /** Marque l'echeance comme impayee, sans interrompre le contrat. */
    public Subscription markPastDue(Long id) {
        Subscription subscription = getById(id);
        if (!subscription.isLive()) {
            throw BusinessException.of("subscription.notLive",
                    "Un contrat resilie ou expire ne peut pas passer en impaye");
        }
        subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
        return subscriptionRepository.save(subscription);
    }

    public Subscription cancel(Long id, String reason) {
        Subscription subscription = getById(id);
        if (!subscription.isLive()) {
            throw BusinessException.of("subscription.alreadyClosed",
                    "Ce contrat est deja clos");
        }
        return close(subscription, Subscription.SubscriptionStatus.CANCELED, reason);
    }

    /** Cloture le contrat vivant d'une entreprise, s'il y en a un. Sans effet sinon. */
    public void cancelForCompany(Long companyId, String reason) {
        getLiveForCompany(companyId)
                .ifPresent(subscription -> close(subscription, Subscription.SubscriptionStatus.CANCELED, reason));
    }

    /**
     * Cloture un contrat en horodatant la resiliation.
     *
     * {@code canceledAt} n'est pas cosmetique : c'est le seul point d'appui du calcul du
     * churn, qui compte les resiliations d'une periode. Le laisser vide rendrait l'indicateur
     * muet.
     */
    private Subscription close(Subscription subscription, Subscription.SubscriptionStatus status, String reason) {
        subscription.setStatus(status);
        subscription.setCanceledAt(LocalDateTime.now());
        subscription.setCancelReason(reason);
        return subscriptionRepository.save(subscription);
    }

    private BigDecimal catalogPrice(Plan plan, Subscription.BillingPeriod period) {
        return period == Subscription.BillingPeriod.YEARLY ? plan.getYearlyPrice() : plan.getMonthlyPrice();
    }

    private LocalDateTime nextPeriodEnd(LocalDateTime from, Subscription.BillingPeriod period) {
        return period == Subscription.BillingPeriod.YEARLY ? from.plusYears(1) : from.plusMonths(1);
    }

    private Subscription.BillingPeriod parsePeriod(String value) {
        if (value == null || value.isBlank()) {
            return Subscription.BillingPeriod.MONTHLY;
        }
        try {
            return Subscription.BillingPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.of("subscription.billingPeriod.invalid",
                    "Periodicite de facturation invalide : " + value, value);
        }
    }
}
