package com.gescom.backend.dto.platform;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tableau de bord du super admin.
 *
 * Tout est calcule en base a chaque appel : rien n'est fige, estime ou mis en cache. Chaque
 * chiffre affiche peut etre retrouve directement en SQL, c'est le seul moyen d'avoir un
 * tableau de bord commercial qu'on peut verifier.
 *
 * La reponse est decoupee en sous-blocs plutot qu'une centaine de champs a plat : chaque
 * bloc correspond a une zone de l'ecran, et le front peut en afficher une partie sans
 * connaitre le reste.
 */
public record PlatformDashboardResponse(
        CompanyStats companies,
        SubscriptionStats subscriptions,
        RevenueStats revenue,
        PaymentStats payments,
        ActivityStats activity,
        List<PlanBreakdown> planBreakdown,
        List<MonthlyRevenuePoint> revenueTrend,
        List<CompanyResponse> recentCompanies,
        List<SaasPaymentResponse> recentPayments,
        List<PlatformAlert> alerts,
        HealthStats health,
        LocalDateTime generatedAt
) {

    /** Etat des entreprises clientes. */
    public record CompanyStats(
            long total,
            long active,
            long trial,
            long suspended,
            long canceled,
            long newThisMonth,
            long newLastMonth,
            /** Evolution des nouvelles entreprises par rapport au mois d'avant, en pourcentage. */
            BigDecimal growthRate
    ) {
    }

    public record SubscriptionStats(
            long total,
            long active,
            long trialing,
            long pastDue,
            long canceled,
            long expired,
            /**
             * Taux de resiliation du mois : nombre de resiliations divise par le nombre de
             * contrats actifs le premier jour du mois. On divise bien par le nombre en debut
             * de mois et pas par celui d'aujourd'hui, sinon un mois avec beaucoup de
             * resiliations ferait baisser son propre taux.
             */
            BigDecimal churnRate,
            long canceledThisMonth,
            /**
             * Echeances dans la periode configuree. Le nom ne contient pas de duree exprès :
             * elle est configurable, et un champ appele « next30Days » deviendrait faux des
             * qu'on changerait le reglage.
             */
            long renewalsUpcoming,
            /** Periode utilisee, pour que le front puisse l'afficher. */
            int renewalWindowDays
    ) {
    }

    public record RevenueStats(
            /** Revenu mensuel recurrent : contrats actifs, les annuels ramenes au mois. */
            BigDecimal mrr,
            /** Revenu annuel recurrent, c'est-a-dire douze fois le MRR. */
            BigDecimal arr,
            BigDecimal revenueThisMonth,
            BigDecimal revenueLastMonth,
            BigDecimal revenueTotal,
            /** Revenu moyen par compte actif : MRR divise par le nombre de contrats actifs. */
            BigDecimal arpa,
            String currency
    ) {
    }

    public record PaymentStats(
            long succeeded,
            long failed,
            long pending,
            long refunded,
            long succeededThisMonth,
            long failedThisMonth,
            /** Pourcentage de paiements reussis sur le mois. */
            BigDecimal successRate
    ) {
    }

    /** Activite de toutes les entreprises : ce qu'elles font vraiment avec l'outil. */
    public record ActivityStats(
            long totalUsers,
            long activeUsers,
            long totalOrders,
            long totalProducts,
            long actionsLast24h,
            long usersActiveLast7Days
    ) {
    }

    public record PlanBreakdown(
            String planCode,
            String planName,
            long subscriptions,
            BigDecimal mrr,
            /** Part de cette formule dans le MRR total, en pourcentage. */
            BigDecimal share
    ) {
    }

    public record MonthlyRevenuePoint(
            int year,
            int month,
            String label,
            BigDecimal amount,
            long paymentCount
    ) {
    }

    /**
     * Evenement qui demande une action de l'operateur.
     *
     * {@code severity} vaut CRITICAL, WARNING ou INFO ; {@code type} donne le type d'alerte
     * pour que le front puisse la traduire au lieu d'afficher le texte brut.
     */
    public record PlatformAlert(
            String type,
            String severity,
            String message,
            Long companyId,
            String companyName,
            LocalDateTime occurredAt
    ) {
    }

    /**
     * Sante generale, resumee en une note sur 100.
     *
     * Volontairement simple et facile a expliquer : le pourcentage d'entreprises qui
     * fonctionnent, moins une penalite par impaye et par echec de paiement recent. Un
     * indicateur qu'on ne sait pas expliquer ne sert a rien sur un tableau de bord.
     */
    public record HealthStats(
            int score,
            String status,
            long overdueSubscriptions,
            long trialsEndingSoon,
            long failedPaymentsThisMonth
    ) {
    }
}
