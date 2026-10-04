package com.gescom.backend.service;

import com.gescom.backend.dto.platform.PlanRequest;
import com.gescom.backend.entity.Plan;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.exception.DuplicateResourceException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.PlanRepository;
import com.gescom.backend.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des formules d'abonnement.
 *
 * La regle principale de ce service : **modifier une formule ne change jamais les contrats
 * deja souscrits**. {@code Subscription.amount} garde le prix au moment de la souscription,
 * donc un changement de prix ne concerne que les prochaines souscriptions. Sinon, changer un
 * prix modifierait le MRR passe et ne correspondrait plus aux paiements deja recus.
 *
 * Du coup, pour retirer une formule on la desactive, on ne la supprime pas. La suppression
 * n'est possible que pour une formule que personne n'a jamais prise, sinon les anciens
 * contrats pointeraient vers rien.
 */
@Service
@Transactional
public class PlanService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;

    public PlanService(PlanRepository planRepository, SubscriptionRepository subscriptionRepository) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public List<Plan> getAll() {
        return planRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public Plan getById(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", id));
    }

    /** Nombre de contrats (tous statuts) lies a une formule. */
    @Transactional(readOnly = true)
    public long countSubscriptions(Long planId) {
        return subscriptionRepository.countByPlanId(planId);
    }

    public Plan create(PlanRequest request) {
        String code = request.code().trim().toUpperCase();
        if (planRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Plan", "code", code);
        }
        Plan plan = new Plan();
        plan.setCode(code);
        apply(plan, request);
        return planRepository.save(plan);
    }

    /**
     * Modifie une formule. Le code est ignore exprès : il identifie la formule et ne doit pas
     * changer, comme le slug d'une entreprise.
     */
    public Plan update(Long id, PlanRequest request) {
        Plan plan = getById(id);
        apply(plan, request);
        return planRepository.save(plan);
    }

    /**
     * Active ou desactive une formule.
     *
     * Desactiver ne change rien aux contrats en cours : ils continuent et comptent toujours
     * dans le MRR. On ne peut juste plus y souscrire, ce que {@code SubscriptionService.subscribe}
     * verifie deja pour une formule inactive.
     */
    public Plan setActive(Long id, boolean active) {
        Plan plan = getById(id);
        plan.setActive(active);
        return planRepository.save(plan);
    }

    /**
     * Supprime une formule jamais souscrite.
     *
     * On verifie *tous* les contrats, resilies compris : les anciens abonnements font
     * reference a la formule, et la supprimer laisserait des contrats sans prix ni nom. Une
     * formule qu'on ne veut plus vendre, on la desactive.
     */
    public void delete(Long id) {
        Plan plan = getById(id);
        long subscriptions = subscriptionRepository.countByPlanId(id);
        if (subscriptions > 0) {
            throw BusinessException.of("plan.inUse",
                    "La formule " + plan.getName() + " est rattachee a " + subscriptions
                            + " abonnement(s) : desactivez-la plutot que de la supprimer",
                    plan.getName(), subscriptions);
        }
        planRepository.delete(plan);
    }

    private void apply(Plan plan, PlanRequest request) {
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setMonthlyPrice(request.monthlyPrice());
        plan.setYearlyPrice(request.yearlyPrice());
        plan.setMaxUsers(request.maxUsers());
        plan.setMaxProducts(request.maxProducts());
        plan.setTrialDays(request.trialDays());
        plan.setActive(request.active() == null || request.active());
        plan.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
    }
}
