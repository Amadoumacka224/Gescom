import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import {
  Building2,
  Users,
  BadgeEuro,
  TrendingUp,
  AlertTriangle,
  CheckCircle2,
  Receipt,
  Activity as ActivityIcon,
  ShoppingCart,
} from 'lucide-react';
import StatCard from '../../components/StatCard';
import { badgeClass, miniStatClass } from '../../constants/statusBadges';
import { formatCurrency, formatDate, safeRatio } from '../../utils/format';
import { extractErrorMessage } from '../../utils/apiError';
import platformService from '../../services/platformService';

/**
 * Tableau de bord du propriétaire de la plateforme.
 *
 * Chaque valeur affichée ici est agrégée en base par `PlatformMetricsService` au moment de
 * l'appel : aucun chiffre n'est calculé côté navigateur, ni figé. Une seule requête alimente
 * l'écran entier.
 */

const COMPANY_STATUS_TONE = {
  ACTIVE: 'success',
  TRIAL: 'info',
  SUSPENDED: 'warning',
  CANCELED: 'danger',
};

const HEALTH_TONE = {
  EXCELLENT: 'success',
  GOOD: 'success',
  WARNING: 'warning',
  CRITICAL: 'danger',
};

const SEVERITY_TONE = {
  CRITICAL: 'danger',
  WARNING: 'warning',
  INFO: 'info',
};

/** Variation d'un mois sur l'autre, avec son signe. Le serveur renvoie déjà un pourcentage. */
const TrendBadge = ({ value }) => {
  const numeric = Number(value) || 0;
  if (numeric === 0) return null;
  const positive = numeric > 0;
  return (
    <span className={badgeClass(positive ? 'success' : 'danger')}>
      {positive ? '+' : ''}
      {numeric}&nbsp;%
    </span>
  );
};

const SectionCard = ({ title, action, children }) => (
  <div className="card">
    <div className="flex items-center justify-between gap-3 border-b border-gray-200 px-5 py-3 dark:border-gray-700">
      <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100">{title}</h2>
      {action}
    </div>
    <div className="p-5">{children}</div>
  </div>
);

const PlatformDashboard = () => {
  const { t } = useTranslation();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const response = await platformService.getDashboard();
        if (!cancelled) setData(response.data);
      } catch (error) {
        if (!cancelled) {
          toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [t]);

  const companies = data?.companies;
  const subscriptions = data?.subscriptions;
  const revenue = data?.revenue;
  const payments = data?.payments;
  const activity = data?.activity;
  const health = data?.health;

  // Échelle du graphique : la plus haute barre remplit la colonne, les autres s'y rapportent.
  const trendMax = Math.max(1, ...(data?.revenueTrend ?? []).map((p) => Number(p.amount) || 0));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {t('platform.dashboard.title')}
        </h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {t('platform.dashboard.subtitle')}
        </p>
      </div>

      {/* Indicateurs de tête */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          title={t('platform.dashboard.totalCompanies')}
          value={companies?.total ?? 0}
          subtitle={t('platform.dashboard.newThisMonth', { count: companies?.newThisMonth ?? 0 })}
          icon={Building2}
          tone="info"
          loading={loading}
        />
        <StatCard
          title={t('platform.dashboard.mrr')}
          value={formatCurrency(revenue?.mrr)}
          subtitle={t('platform.dashboard.arrValue', {
            value: formatCurrency(revenue?.arr),
          })}
          icon={TrendingUp}
          tone="success"
          loading={loading}
        />
        <StatCard
          title={t('platform.dashboard.activeSubscriptions')}
          value={subscriptions?.active ?? 0}
          subtitle={t('platform.dashboard.trialingCount', { count: subscriptions?.trialing ?? 0 })}
          icon={BadgeEuro}
          tone="accent"
          loading={loading}
        />
        <StatCard
          title={t('platform.dashboard.totalUsers')}
          value={activity?.totalUsers ?? 0}
          subtitle={t('platform.dashboard.activeUsers', { count: activity?.activeUsers ?? 0 })}
          icon={Users}
          tone="info"
          loading={loading}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Répartition du parc */}
        <SectionCard title={t('platform.dashboard.companyBreakdown')}>
          <div className="space-y-2">
            {['ACTIVE', 'TRIAL', 'SUSPENDED', 'CANCELED'].map((status) => {
              const value =
                status === 'ACTIVE' ? companies?.active
                : status === 'TRIAL' ? companies?.trial
                : status === 'SUSPENDED' ? companies?.suspended
                : companies?.canceled;
              return (
                <div key={status} className={miniStatClass(COMPANY_STATUS_TONE[status])}>
                  <span>{t(`platform.companyStatus.${status}`)}</span>
                  <span className="font-semibold">{value ?? 0}</span>
                </div>
              );
            })}
          </div>
          <div className="mt-4 flex items-center justify-between border-t border-gray-200 pt-3 text-sm dark:border-gray-700">
            <span className="text-gray-500 dark:text-gray-400">
              {t('platform.dashboard.growth')}
            </span>
            <TrendBadge value={companies?.growthRate} />
          </div>
        </SectionCard>

        {/* Revenus */}
        <SectionCard title={t('platform.dashboard.revenue')}>
          <dl className="space-y-3 text-sm">
            <div className="flex items-center justify-between">
              <dt className="text-gray-500 dark:text-gray-400">
                {t('platform.dashboard.revenueThisMonth')}
              </dt>
              <dd className="font-semibold text-gray-900 dark:text-gray-100">
                {formatCurrency(revenue?.revenueThisMonth)}
              </dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-gray-500 dark:text-gray-400">
                {t('platform.dashboard.revenueLastMonth')}
              </dt>
              <dd className="font-semibold text-gray-900 dark:text-gray-100">
                {formatCurrency(revenue?.revenueLastMonth)}
              </dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-gray-500 dark:text-gray-400">
                {t('platform.dashboard.revenueTotal')}
              </dt>
              <dd className="font-semibold text-gray-900 dark:text-gray-100">
                {formatCurrency(revenue?.revenueTotal)}
              </dd>
            </div>
            <div className="flex items-center justify-between border-t border-gray-200 pt-3 dark:border-gray-700">
              <dt className="text-gray-500 dark:text-gray-400">
                {t('platform.dashboard.arpa')}
              </dt>
              <dd className="font-semibold text-gray-900 dark:text-gray-100">
                {formatCurrency(revenue?.arpa)}
              </dd>
            </div>
          </dl>
        </SectionCard>

        {/* Santé de la plateforme */}
        <SectionCard title={t('platform.dashboard.health')}>
          <div className="flex items-center gap-4">
            <div className="text-4xl font-bold text-gray-900 dark:text-gray-100">
              {health?.score ?? 0}
              <span className="text-lg text-gray-400">/100</span>
            </div>
            <span className={badgeClass(HEALTH_TONE[health?.status])}>
              {t(`platform.healthStatus.${health?.status ?? 'GOOD'}`)}
            </span>
          </div>
          <div className="mt-4 space-y-2">
            <div className={miniStatClass(health?.overdueSubscriptions > 0 ? 'danger' : 'neutral')}>
              <span>{t('platform.dashboard.overdue')}</span>
              <span className="font-semibold">{health?.overdueSubscriptions ?? 0}</span>
            </div>
            <div className={miniStatClass(health?.trialsEndingSoon > 0 ? 'warning' : 'neutral')}>
              <span>{t('platform.dashboard.trialsEnding')}</span>
              <span className="font-semibold">{health?.trialsEndingSoon ?? 0}</span>
            </div>
            <div className={miniStatClass(health?.failedPaymentsThisMonth > 0 ? 'danger' : 'neutral')}>
              <span>{t('platform.dashboard.failedPayments')}</span>
              <span className="font-semibold">{health?.failedPaymentsThisMonth ?? 0}</span>
            </div>
          </div>
        </SectionCard>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Courbe de revenus — barres proportionnelles, sans dépendance graphique */}
        <SectionCard title={t('platform.dashboard.revenueTrend')}>
          {(data?.revenueTrend ?? []).length === 0 ? (
            <p className="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
              {t('common.noData')}
            </p>
          ) : (
            <div className="flex h-48 items-end gap-2">
              {data.revenueTrend.map((point) => (
                <div
                  key={`${point.year}-${point.month}`}
                  className="flex flex-1 flex-col items-center gap-2"
                  title={`${point.label} — ${formatCurrency(point.amount)}`}
                >
                  <div className="flex w-full flex-1 items-end">
                    <div
                      className="w-full rounded-t bg-primary-500 transition-all dark:bg-primary-400"
                      style={{
                        height: `${Math.max(4, safeRatio(point.amount, trendMax) * 100)}%`,
                      }}
                    />
                  </div>
                  <span className="truncate text-[10px] text-gray-500 dark:text-gray-400">
                    {point.label}
                  </span>
                </div>
              ))}
            </div>
          )}
        </SectionCard>

        {/* Répartition par formule */}
        <SectionCard title={t('platform.dashboard.planBreakdown')}>
          {(data?.planBreakdown ?? []).length === 0 ? (
            <p className="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
              {t('common.noData')}
            </p>
          ) : (
            <div className="space-y-4">
              {data.planBreakdown.map((plan) => (
                <div key={plan.planCode}>
                  <div className="mb-1 flex items-center justify-between text-sm">
                    <span className="font-medium text-gray-900 dark:text-gray-100">
                      {plan.planName}
                    </span>
                    <span className="text-gray-500 dark:text-gray-400">
                      {t('platform.dashboard.planLine', {
                        count: plan.subscriptions,
                        mrr: formatCurrency(plan.mrr),
                      })}
                    </span>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
                    <div
                      className="h-full rounded-full bg-primary-500 dark:bg-primary-400"
                      style={{ width: `${Number(plan.share) || 0}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </SectionCard>
      </div>

      {/* Abonnements, paiements, usage */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          title={t('platform.dashboard.churnRate')}
          value={`${subscriptions?.churnRate ?? 0} %`}
          subtitle={t('platform.dashboard.canceledThisMonth', {
            count: subscriptions?.canceledThisMonth ?? 0,
          })}
          icon={AlertTriangle}
          tone={Number(subscriptions?.churnRate) > 5 ? 'danger' : 'success'}
          loading={loading}
        />
        <StatCard
          title={t('platform.dashboard.renewals', {
            days: subscriptions?.renewalWindowDays ?? 30,
          })}
          value={subscriptions?.renewalsUpcoming ?? 0}
          subtitle={t('platform.dashboard.renewalsHint')}
          icon={CheckCircle2}
          tone="info"
          loading={loading}
        />
        <StatCard
          title={t('platform.dashboard.paymentSuccess')}
          value={`${payments?.successRate ?? 0} %`}
          subtitle={t('platform.dashboard.paymentCounts', {
            succeeded: payments?.succeededThisMonth ?? 0,
            failed: payments?.failedThisMonth ?? 0,
          })}
          icon={Receipt}
          tone={Number(payments?.successRate) >= 90 ? 'success' : 'warning'}
          loading={loading}
        />
        <StatCard
          title={t('platform.dashboard.platformActivity')}
          value={activity?.actionsLast24h ?? 0}
          subtitle={t('platform.dashboard.activityHint', {
            users: activity?.usersActiveLast7Days ?? 0,
          })}
          icon={ActivityIcon}
          tone="accent"
          loading={loading}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Alertes */}
        <SectionCard title={t('platform.dashboard.alerts')}>
          {(data?.alerts ?? []).length === 0 ? (
            <p className="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
              {t('platform.dashboard.noAlerts')}
            </p>
          ) : (
            <ul className="space-y-2">
              {data.alerts.slice(0, 8).map((alert, index) => (
                <li
                  key={`${alert.type}-${alert.companyId}-${index}`}
                  className="flex items-start gap-3 rounded-lg border border-gray-200 p-3 dark:border-gray-700"
                >
                  <span className={badgeClass(SEVERITY_TONE[alert.severity])}>
                    {t(`platform.severity.${alert.severity}`)}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                      {alert.companyName}
                    </p>
                    <p className="text-xs text-gray-500 dark:text-gray-400">{alert.message}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </SectionCard>

        {/* Nouveaux clients */}
        <SectionCard
          title={t('platform.dashboard.recentCompanies')}
          action={
            <Link
              to="/platform/companies"
              className="text-xs font-medium text-primary-600 hover:underline dark:text-primary-400"
            >
              {t('common.view')}
            </Link>
          }
        >
          {(data?.recentCompanies ?? []).length === 0 ? (
            <p className="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
              {t('common.noData')}
            </p>
          ) : (
            <ul className="divide-y divide-gray-200 dark:divide-gray-700">
              {data.recentCompanies.map((company) => (
                <li key={company.id} className="flex items-center gap-3 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                      {company.name}
                    </p>
                    <p className="text-xs text-gray-500 dark:text-gray-400">
                      {formatDate(company.createdAt)}
                      {company.subscription ? ` · ${company.subscription.planName}` : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
                      <Users className="h-3.5 w-3.5" />
                      {company.userCount}
                    </span>
                    <span className="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
                      <ShoppingCart className="h-3.5 w-3.5" />
                      {company.orderCount}
                    </span>
                    <span className={badgeClass(COMPANY_STATUS_TONE[company.status])}>
                      {t(`platform.companyStatus.${company.status}`)}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </SectionCard>
      </div>
    </div>
  );
};

export default PlatformDashboard;
