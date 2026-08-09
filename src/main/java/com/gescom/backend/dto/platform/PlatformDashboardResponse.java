package com.gescom.backend.dto.platform;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tableau de bord du proprietaire de la plateforme.
 *
 * Tout ce qui suit est agrege en base a chaque appel : aucune valeur n'est figee, estimee ou
 * mise en cache. Un chiffre affiche ici est un chiffre lisible directement en SQL, ce qui
 * est la seule facon de rendre un tableau de bord commercial verifiable.
 *
 * La reponse est volontairement composee de sous-blocs plutot que d'une centaine de champs
 * a plat : chaque bloc correspond a une zone de l'ecran, et l'interface peut en afficher
 * une partie sans avoir a connaitre le reste.
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

    /** Etat du parc d'entreprises clientes. */
    public record CompanyStats(
            long total,
            long active,
            long trial,
            long suspended,
            long canceled,
            long newThisMonth,
            long newLastMonth,
            /** Variation du recrutement d'un mois sur l'autre, en pourcentage. */
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
             * Taux de resiliation du mois : resiliations rapportees au nombre de contrats
             * vivants au premier jour du mois. Le denominateur est bien l'effectif de debut
             * de periode, non celui d'aujourd'hui, sans quoi un mois de forte resiliation
             * ferait mecaniquement baisser son propre taux.
             */
            BigDecimal churnRate,
            long canceledThisMonth,
            /**
             * Echeances tombant dans la fenetre parametree. Le nom ne mentionne aucune duree :
             * elle est configurable, et un champ nomme « next30Days » aurait menti des la
             * premiere modification du reglage.
             */
            long renewalsUpcoming,
            /** Fenetre effectivement appliquee, pour que l'interface puisse l'annoncer. */
            int renewalWindowDays
    ) {
    }

    public record RevenueStats(
            /** Revenu mensuel recurrent : contrats vivants, contrats annuels mensualises. */
            BigDecimal mrr,
            /** Revenu annuel recurrent, soit douze fois le MRR. */
            BigDecimal arr,
            BigDecimal revenueThisMonth,
            BigDecimal revenueLastMonth,
            BigDecimal revenueTotal,
            /** Revenu moyen par compte actif — MRR rapporte au nombre de contrats vivants. */
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
            /** Part des paiements aboutis sur le mois, en pourcentage. */
            BigDecimal successRate
    ) {
    }

    /** Activite consolidee du parc — ce que les entreprises font reellement de l'outil. */
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
            /** Part de ce plan dans le MRR total, en pourcentage. */
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
     * Evenement appelant une action de l'operateur.
     *
     * {@code severity} vaut CRITICAL, WARNING ou INFO ; {@code type} identifie la nature de
     * l'alerte pour que l'interface puisse la traduire plutot que d'afficher le libelle brut.
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
     * Sante generale, resumee en un score sur 100.
     *
     * Volontairement simple et explicable : la part des entreprises operationnelles dans le
     * parc, moins une penalite par impaye et par echec de paiement recent. Un indicateur que
     * l'on ne sait pas justifier ne sert a rien sur un tableau de bord de direction.
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
