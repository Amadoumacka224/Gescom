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
 * Catalogue commercial.
 *
 * Une regle domine tout ce service : **modifier une formule ne touche jamais aux contrats
 * deja souscrits**. {@code Subscription.amount} fige le tarif a la souscription, de sorte
 * qu'une revision du catalogue n'affecte que les souscriptions a venir. Sans cela, changer
 * un prix reecrirait retroactivement le MRR et contredirait les paiements deja encaisses.
 *
 * Corollaire : retirer une formule du catalogue se fait en la desactivant, pas en la
 * supprimant. La suppression n'est acceptee que sur une formule que personne n'a jamais
 * souscrite — sinon l'historique des contrats pointerait dans le vide.
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

    /** Nombre de contrats — tous statuts — rattaches a une formule. */
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
     * Met a jour une formule. Le code est volontairement ignore : il identifie la formule et
     * doit rester stable, comme le slug d'une entreprise.
     */
    public Plan update(Long id, PlanRequest request) {
        Plan plan = getById(id);
        apply(plan, request);
        return planRepository.save(plan);
    }

    /**
     * Retire ou remet une formule au catalogue.
     *
     * Desactiver n'a aucun effet sur les contrats en cours : ils continuent de courir et de
     * peser dans le MRR. Seule la souscription est fermee, ce que
     * {@code SubscriptionService.subscribe} refuse deja sur une formule inactive.
     */
    public Plan setActive(Long id, boolean active) {
        Plan plan = getById(id);
        plan.setActive(active);
        return planRepository.save(plan);
    }

    /**
     * Supprime une formule jamais souscrite.
     *
     * Le garde-fou porte sur *tous* les contrats, resilies compris : l'historique des
     * abonnements reference la formule, et l'effacer laisserait des contrats sans tarif ni
     * intitule. Une formule qu'on ne veut plus vendre se desactive.
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
