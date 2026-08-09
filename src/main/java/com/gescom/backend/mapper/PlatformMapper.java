package com.gescom.backend.mapper;

import com.gescom.backend.dto.platform.CompanyResponse;
import com.gescom.backend.dto.platform.PlanResponse;
import com.gescom.backend.dto.platform.SaasPaymentResponse;
import com.gescom.backend.dto.platform.SubscriptionResponse;
import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.Plan;
import com.gescom.backend.entity.SaasPayment;
import com.gescom.backend.entity.Subscription;
import com.gescom.backend.repository.OrderRepository;
import com.gescom.backend.repository.SubscriptionRepository;
import com.gescom.backend.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversion des entites de la plateforme vers leurs DTO de reponse.
 *
 * Ecrit a la main comme les autres mappers du projet, sans MapStruct, et injectant ses
 * repositories a la maniere d'{@code OrderMapper} : la fiche d'une entreprise porte son
 * abonnement courant et ses compteurs d'usage, que le mapper resout lui-meme pour que les
 * controleurs restent minces.
 */
@Component
public class PlatformMapper {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public PlatformMapper(SubscriptionRepository subscriptionRepository,
                          UserRepository userRepository,
                          OrderRepository orderRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Fiche complete d'une entreprise : abonnement vivant et compteurs d'usage compris.
     *
     * Trois requetes par entreprise, ce qui est assume sur une fiche unitaire et sur les
     * listes courtes du tableau de bord. La liste paginee du parc emprunte le meme chemin :
     * a vingt-cinq lignes par page, la simplicite l'emporte sur l'optimisation.
     */
    public CompanyResponse toResponse(Company company) {
        if (company == null) {
            return null;
        }
        SubscriptionResponse subscription = subscriptionRepository
                .findLiveByCompany(company.getId(), Subscription.LIVE_STATUSES)
                .map(this::toResponse)
                .orElse(null);
        return toResponse(company, subscription,
                userRepository.countByOwnerCompanyId(company.getId()),
                orderRepository.countByOwnerCompanyId(company.getId()));
    }

    public CompanyResponse toResponse(Company company, SubscriptionResponse subscription,
                                      long userCount, long orderCount) {
        if (company == null) {
            return null;
        }
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getSlug(),
                company.getEmail(),
                company.getPhone(),
                company.getAddress(),
                company.getCity(),
                company.getPostalCode(),
                company.getCountry(),
                company.getTaxId(),
                company.getStatus().name(),
                company.getTrialEndsAt(),
                company.getCanceledAt(),
                company.getNotes(),
                company.getCreatedAt(),
                company.getUpdatedAt(),
                subscription,
                userCount,
                orderCount
        );
    }

    public SubscriptionResponse toResponse(Subscription subscription) {
        if (subscription == null) {
            return null;
        }
        Company company = subscription.getCompany();
        Plan plan = subscription.getPlan();
        return new SubscriptionResponse(
                subscription.getId(),
                company != null ? company.getId() : null,
                company != null ? company.getName() : null,
                plan != null ? plan.getId() : null,
                plan != null ? plan.getCode() : null,
                plan != null ? plan.getName() : null,
                subscription.getStatus().name(),
                subscription.getBillingPeriod().name(),
                subscription.getAmount(),
                // Arrondi au centime pour l'affichage seulement : les agregats de revenus
                // somment la valeur pleine precision et n'arrondissent qu'a la fin.
                subscription.monthlyAmount().setScale(2, RoundingMode.HALF_UP),
                subscription.getCurrency(),
                subscription.getStartedAt(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getCanceledAt(),
                subscription.getCancelReason()
        );
    }

    public SaasPaymentResponse toResponse(SaasPayment payment) {
        if (payment == null) {
            return null;
        }
        Company company = payment.getCompany();
        Subscription subscription = payment.getSubscription();
        return new SaasPaymentResponse(
                payment.getId(),
                company != null ? company.getId() : null,
                company != null ? company.getName() : null,
                subscription != null ? subscription.getId() : null,
                payment.getReference(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getMethod().name(),
                payment.getPeriodStart(),
                payment.getPeriodEnd(),
                payment.getPaidAt(),
                payment.getFailureMessage(),
                payment.getCreatedAt()
        );
    }

    /**
     * Fiche d'une formule, avec le nombre de contrats qui s'y rattachent — tous statuts
     * confondus, l'historique comptant autant que les contrats vivants pour decider si la
     * formule est encore supprimable.
     */
    public PlanResponse toResponse(Plan plan) {
        if (plan == null) {
            return null;
        }
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getMonthlyPrice(),
                plan.getYearlyPrice(),
                plan.getMaxUsers(),
                plan.getMaxProducts(),
                plan.getTrialDays(),
                plan.getActive(),
                plan.getSortOrder(),
                subscriptionRepository.countByPlanId(plan.getId())
        );
    }

    /** Tarif catalogue correspondant a une periodicite. */
    public BigDecimal priceFor(Plan plan, Subscription.BillingPeriod period) {
        return period == Subscription.BillingPeriod.YEARLY ? plan.getYearlyPrice() : plan.getMonthlyPrice();
    }
}
