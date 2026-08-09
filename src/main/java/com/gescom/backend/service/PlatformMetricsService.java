package com.gescom.backend.service;

import com.gescom.backend.dto.platform.PlatformDashboardResponse;
import com.gescom.backend.dto.platform.PlatformDashboardResponse.*;
import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.SaasPayment;
import com.gescom.backend.entity.Subscription;
import com.gescom.backend.entity.User;
import com.gescom.backend.mapper.PlatformMapper;
import com.gescom.backend.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calcul des indicateurs du tableau de bord proprietaire.
 *
 * Toutes les valeurs sont agregees en base a chaque appel. Rien n'est mis en cache ni
 * pre-calcule : un tableau de bord commercial dont les chiffres ne se retrouvent pas en SQL
 * n'est pas verifiable, et une valeur figee finit toujours par mentir.
 *
 * Les mesures de revenu somment en pleine precision et n'arrondissent qu'une fois, au
 * moment de construire la reponse — arrondir chaque contrat au centime derive de plusieurs
 * euros des que le parc atteint quelques centaines d'abonnes.
 */
@Service
@Transactional(readOnly = true)
public class PlatformMetricsService {

    private static final Locale FR = Locale.FRENCH;
    private static final int TREND_MONTHS = 12;

    private final CompanyRepository companyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SaasPaymentRepository saasPaymentRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ActivityLogRepository activityLogRepository;
    private final PlatformMapper platformMapper;

    public PlatformMetricsService(CompanyRepository companyRepository,
                                  SubscriptionRepository subscriptionRepository,
                                  SaasPaymentRepository saasPaymentRepository,
                                  UserRepository userRepository,
                                  OrderRepository orderRepository,
                                  ProductRepository productRepository,
                                  ActivityLogRepository activityLogRepository,
                                  PlatformMapper platformMapper) {
        this.companyRepository = companyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.saasPaymentRepository = saasPaymentRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.activityLogRepository = activityLogRepository;
        this.platformMapper = platformMapper;
    }

    public PlatformDashboardResponse buildDashboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime lastMonthStart = monthStart.minusMonths(1);

        CompanyStats companies = companyStats(monthStart, lastMonthStart);
        SubscriptionStats subscriptions = subscriptionStats(now, monthStart);
        RevenueStats revenue = revenueStats(now, monthStart, lastMonthStart);
        PaymentStats payments = paymentStats(now, monthStart);
        ActivityStats activity = activityStats(now);
        List<PlanBreakdown> planBreakdown = planBreakdown(revenue.mrr());
        List<MonthlyRevenuePoint> trend = revenueTrend(monthStart);
        List<PlatformAlert> alerts = alerts(now);
        HealthStats health = health(companies, alerts, now, monthStart);

        return new PlatformDashboardResponse(
                companies, subscriptions, revenue, payments, activity,
                planBreakdown, trend,
                recentCompanies(), recentPayments(),
                alerts, health, now
        );
    }

    // ── Entreprises ──────────────────────────────────────────────────────────

    private CompanyStats companyStats(LocalDateTime monthStart, LocalDateTime lastMonthStart) {
        Map<Company.CompanyStatus, Long> byStatus = new EnumMap<>(Company.CompanyStatus.class);
        for (Object[] row : companyRepository.countGroupedByStatus()) {
            byStatus.put((Company.CompanyStatus) row[0], ((Number) row[1]).longValue());
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        long newThisMonth = companyRepository.countByCreatedAtGreaterThanEqual(monthStart);
        // Le mois precedent se deduit par difference : « depuis le 1er du mois dernier »
        // moins « depuis le 1er de ce mois-ci », ce qui evite une seconde requete bornee.
        long newSinceLastMonth = companyRepository.countByCreatedAtGreaterThanEqual(lastMonthStart);
        long newLastMonth = newSinceLastMonth - newThisMonth;

        return new CompanyStats(
                total,
                byStatus.getOrDefault(Company.CompanyStatus.ACTIVE, 0L),
                byStatus.getOrDefault(Company.CompanyStatus.TRIAL, 0L),
                byStatus.getOrDefault(Company.CompanyStatus.SUSPENDED, 0L),
                byStatus.getOrDefault(Company.CompanyStatus.CANCELED, 0L),
                newThisMonth,
                newLastMonth,
                percentChange(newLastMonth, newThisMonth)
        );
    }

    // ── Abonnements et churn ─────────────────────────────────────────────────

    private SubscriptionStats subscriptionStats(LocalDateTime now, LocalDateTime monthStart) {
        Map<Subscription.SubscriptionStatus, Long> byStatus = new EnumMap<>(Subscription.SubscriptionStatus.class);
        for (Object[] row : subscriptionRepository.countGroupedByStatus()) {
            byStatus.put((Subscription.SubscriptionStatus) row[0], ((Number) row[1]).longValue());
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        long canceledThisMonth = subscriptionRepository.countByCanceledAtBetween(monthStart, now);
        long liveAtMonthStart = subscriptionRepository.countLiveAt(monthStart);
        long renewals = subscriptionRepository
                .findRenewalsBetween(Subscription.LIVE_STATUSES, now, now.plusDays(30)).size();

        return new SubscriptionStats(
                total,
                byStatus.getOrDefault(Subscription.SubscriptionStatus.ACTIVE, 0L),
                byStatus.getOrDefault(Subscription.SubscriptionStatus.TRIALING, 0L),
                byStatus.getOrDefault(Subscription.SubscriptionStatus.PAST_DUE, 0L),
                byStatus.getOrDefault(Subscription.SubscriptionStatus.CANCELED, 0L),
                byStatus.getOrDefault(Subscription.SubscriptionStatus.EXPIRED, 0L),
                ratioAsPercent(canceledThisMonth, liveAtMonthStart),
                canceledThisMonth,
                renewals
        );
    }

    // ── Revenus ──────────────────────────────────────────────────────────────

    private RevenueStats revenueStats(LocalDateTime now, LocalDateTime monthStart, LocalDateTime lastMonthStart) {
        BigDecimal mrrRaw = nz(subscriptionRepository.sumMonthlyRecurringRevenue(
                Subscription.LIVE_STATUSES, Subscription.BillingPeriod.YEARLY));
        BigDecimal mrr = mrrRaw.setScale(2, RoundingMode.HALF_UP);

        long liveCount = Subscription.LIVE_STATUSES.stream()
                .mapToLong(subscriptionRepository::countByStatus)
                .sum();

        BigDecimal thisMonth = nz(saasPaymentRepository.sumAmountByStatusAndPaidAtBetween(
                SaasPayment.SaasPaymentStatus.SUCCEEDED, monthStart, now));
        BigDecimal lastMonth = nz(saasPaymentRepository.sumAmountByStatusAndPaidAtBetween(
                SaasPayment.SaasPaymentStatus.SUCCEEDED, lastMonthStart, monthStart));
        BigDecimal allTime = nz(saasPaymentRepository.sumAmountByStatus(SaasPayment.SaasPaymentStatus.SUCCEEDED));

        BigDecimal arpa = liveCount == 0
                ? BigDecimal.ZERO
                : mrrRaw.divide(BigDecimal.valueOf(liveCount), 2, RoundingMode.HALF_UP);

        return new RevenueStats(
                mrr,
                mrrRaw.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP),
                thisMonth.setScale(2, RoundingMode.HALF_UP),
                lastMonth.setScale(2, RoundingMode.HALF_UP),
                allTime.setScale(2, RoundingMode.HALF_UP),
                arpa,
                "EUR"
        );
    }

    private List<MonthlyRevenuePoint> revenueTrend(LocalDateTime monthStart) {
        LocalDateTime since = monthStart.minusMonths(TREND_MONTHS - 1L);
        List<MonthlyRevenuePoint> points = new ArrayList<>();
        for (Object[] row : saasPaymentRepository.monthlyRevenueSince(
                SaasPayment.SaasPaymentStatus.SUCCEEDED, since)) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            points.add(new MonthlyRevenuePoint(
                    year, month,
                    Month.of(month).getDisplayName(TextStyle.SHORT, FR) + " " + year,
                    ((BigDecimal) row[2]).setScale(2, RoundingMode.HALF_UP),
                    ((Number) row[3]).longValue()
            ));
        }
        return points;
    }

    private List<PlanBreakdown> planBreakdown(BigDecimal totalMrr) {
        List<PlanBreakdown> breakdown = new ArrayList<>();
        for (Object[] row : subscriptionRepository.breakdownByPlan(
                Subscription.LIVE_STATUSES, Subscription.BillingPeriod.YEARLY)) {
            BigDecimal planMrr = nz((BigDecimal) row[3]).setScale(2, RoundingMode.HALF_UP);
            breakdown.add(new PlanBreakdown(
                    (String) row[0],
                    (String) row[1],
                    ((Number) row[2]).longValue(),
                    planMrr,
                    totalMrr.signum() == 0 ? BigDecimal.ZERO
                            : planMrr.multiply(BigDecimal.valueOf(100))
                                     .divide(totalMrr, 1, RoundingMode.HALF_UP)
            ));
        }
        return breakdown;
    }

    // ── Paiements ────────────────────────────────────────────────────────────

    private PaymentStats paymentStats(LocalDateTime now, LocalDateTime monthStart) {
        long succeededThisMonth = saasPaymentRepository.countByStatusAndCreatedAtBetween(
                SaasPayment.SaasPaymentStatus.SUCCEEDED, monthStart, now);
        long failedThisMonth = saasPaymentRepository.countByStatusAndCreatedAtBetween(
                SaasPayment.SaasPaymentStatus.FAILED, monthStart, now);

        return new PaymentStats(
                saasPaymentRepository.countByStatus(SaasPayment.SaasPaymentStatus.SUCCEEDED),
                saasPaymentRepository.countByStatus(SaasPayment.SaasPaymentStatus.FAILED),
                saasPaymentRepository.countByStatus(SaasPayment.SaasPaymentStatus.PENDING),
                saasPaymentRepository.countByStatus(SaasPayment.SaasPaymentStatus.REFUNDED),
                succeededThisMonth,
                failedThisMonth,
                ratioAsPercent(succeededThisMonth, succeededThisMonth + failedThisMonth)
        );
    }

    // ── Activite du parc ─────────────────────────────────────────────────────

    private ActivityStats activityStats(LocalDateTime now) {
        return new ActivityStats(
                // Le propriétaire de la plateforme n'est l'utilisateur d'aucune entreprise :
                // le compter parmi les utilisateurs fausserait l'indicateur.
                userRepository.countByRoleNot(User.Role.SUPER_ADMIN),
                userRepository.countByActiveTrueAndRoleNot(User.Role.SUPER_ADMIN),
                orderRepository.count(),
                productRepository.count(),
                activityLogRepository.countByCreatedAtGreaterThanEqual(now.minusDays(1)),
                activityLogRepository.countDistinctUsersSince(now.minusDays(7))
        );
    }

    // ── Alertes ──────────────────────────────────────────────────────────────

    /**
     * Evenements appelant une action : impayes, essais qui expirent, prelevements refuses.
     *
     * Les plus graves d'abord, chacun rattache a son entreprise pour que l'operateur puisse
     * y aller d'un clic depuis le tableau de bord.
     */
    private List<PlatformAlert> alerts(LocalDateTime now) {
        List<PlatformAlert> alerts = new ArrayList<>();

        for (Subscription s : subscriptionRepository.findOverdue(Subscription.LIVE_STATUSES, now)) {
            alerts.add(new PlatformAlert(
                    "SUBSCRIPTION_OVERDUE", "CRITICAL",
                    "Echeance depassee depuis le " + s.getCurrentPeriodEnd().toLocalDate(),
                    s.getCompany().getId(), s.getCompany().getName(), s.getCurrentPeriodEnd()));
        }

        for (SaasPayment p : saasPaymentRepository.findTop5ByStatusOrderByCreatedAtDesc(
                SaasPayment.SaasPaymentStatus.FAILED)) {
            alerts.add(new PlatformAlert(
                    "PAYMENT_FAILED", "CRITICAL",
                    "Paiement refuse (" + p.getReference() + ")"
                            + (p.getFailureMessage() != null ? " : " + p.getFailureMessage() : ""),
                    p.getCompany().getId(), p.getCompany().getName(), p.getCreatedAt()));
        }

        for (Company c : companyRepository.findTrialsEndingBefore(
                Company.CompanyStatus.TRIAL, now.plusDays(7))) {
            boolean expired = c.getTrialEndsAt().isBefore(now);
            alerts.add(new PlatformAlert(
                    expired ? "TRIAL_EXPIRED" : "TRIAL_ENDING",
                    expired ? "CRITICAL" : "WARNING",
                    expired ? "Periode d'essai expiree le " + c.getTrialEndsAt().toLocalDate()
                            : "Fin d'essai le " + c.getTrialEndsAt().toLocalDate(),
                    c.getId(), c.getName(), c.getTrialEndsAt()));
        }

        for (Company c : companyRepository.findByStatus(Company.CompanyStatus.SUSPENDED)) {
            alerts.add(new PlatformAlert(
                    "COMPANY_SUSPENDED", "WARNING",
                    "Compte suspendu", c.getId(), c.getName(), c.getUpdatedAt()));
        }

        alerts.sort((a, b) -> {
            int bySeverity = Integer.compare(severityRank(a.severity()), severityRank(b.severity()));
            return bySeverity != 0 ? bySeverity : b.occurredAt().compareTo(a.occurredAt());
        });
        return alerts;
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    // ── Sante ────────────────────────────────────────────────────────────────

    /**
     * Score sur 100 : part des entreprises operationnelles dans le parc, diminuee d'une
     * penalite par impaye et par echec de paiement du mois.
     *
     * Deliberement simple et explicable. Un indicateur de synthese que l'on ne sait pas
     * justifier devant un comite n'a pas sa place sur un tableau de bord de direction.
     */
    private HealthStats health(CompanyStats companies, List<PlatformAlert> alerts,
                               LocalDateTime now, LocalDateTime monthStart) {
        long overdue = alerts.stream().filter(a -> "SUBSCRIPTION_OVERDUE".equals(a.type())).count();
        long trialsEndingSoon = alerts.stream()
                .filter(a -> "TRIAL_ENDING".equals(a.type()) || "TRIAL_EXPIRED".equals(a.type())).count();
        long failedThisMonth = saasPaymentRepository.countByStatusAndCreatedAtBetween(
                SaasPayment.SaasPaymentStatus.FAILED, monthStart, now);

        int score;
        if (companies.total() == 0) {
            // Parc vide : rien ne va mal, mais rien ne prouve que tout va bien non plus.
            score = 100;
        } else {
            long operational = companies.active() + companies.trial();
            double base = (double) operational / companies.total() * 100d;
            score = (int) Math.round(Math.max(0d, base - overdue * 5d - failedThisMonth * 2d));
        }

        String status = score >= 90 ? "EXCELLENT"
                : score >= 75 ? "GOOD"
                : score >= 50 ? "WARNING"
                : "CRITICAL";

        return new HealthStats(score, status, overdue, trialsEndingSoon, failedThisMonth);
    }

    // ── Listes courtes ───────────────────────────────────────────────────────

    private List<com.gescom.backend.dto.platform.CompanyResponse> recentCompanies() {
        return companyRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5))
                .getContent().stream()
                .map(platformMapper::toResponse)
                .toList();
    }

    private List<com.gescom.backend.dto.platform.SaasPaymentResponse> recentPayments() {
        return saasPaymentRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(platformMapper::toResponse)
                .toList();
    }

    // ── Utilitaires de calcul ────────────────────────────────────────────────

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /** Part de {@code part} dans {@code total}, en pourcentage. Zero si le total est nul. */
    private static BigDecimal ratioAsPercent(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    /**
     * Variation entre deux periodes, en pourcentage.
     *
     * Partir de zero n'a pas de variation definie : on renvoie 100 % des qu'il y a du
     * nouveau, plutot qu'une division par zero ou un zero trompeur.
     */
    private static BigDecimal percentChange(long previous, long current) {
        if (previous == 0) {
            return current == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
    }
}
