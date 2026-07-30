import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Euro,
  ShoppingCart,
  CreditCard,
  TrendingUp,
  Calendar,
  RefreshCw,
  Users,
  ChevronDown,
  Award,
  BarChart3,
  FileText,
  FileSpreadsheet,
  Clock,
  AlertTriangle,
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { toast } from 'react-hot-toast';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import api from '../services/api';
import StatCard from '../components/StatCard';
import OrderStatusBadge from '../components/OrderStatusBadge';
import SegmentedFilter from '../components/SegmentedFilter';
import { exportToCsv, exportToPdf } from '../utils/exportData';
import {
  formatCurrency,
  formatAmount,
  formatCompactCurrency,
  formatTime,
  formatDate,
  formatPercent,
  safeRatio,
  todayISO,
} from '../utils/format';

/**
 * Supervision des caisses (administrateur).
 *
 * Cet écran lisait auparavant `/users` et `/orders` — soit la table des commandes ENTIÈRE —
 * puis réagrégeait tout dans le navigateur. Il en découlait deux problèmes de fond :
 *
 *   1. Fiabilité. L'agrégation locale comptait les commandes annulées dans le chiffre
 *      d'affaires, alors que le backend les exclut partout ailleurs (`buildDayMetrics`,
 *      `/dashboard/overview`). Un responsable et son caissier lisaient donc deux totaux
 *      différents pour la même journée, sans qu'aucun des deux écrans ne signale l'écart.
 *      L'encaissé réel (factures réglées) n'était pas affiché du tout.
 *   2. Volume. Superviser une journée demandait de rapatrier tout l'historique.
 *
 * La page consomme désormais `/dashboard/cashiers`, qui existait déjà et que rien n'appelait :
 * une requête bornée à la date, et les mêmes chiffres que la vue caissier puisque les deux
 * passent par `buildDayMetrics`. Aucune règle métier n'est recalculée ici.
 */

const EMPTY_SUPERVISION = {
  selectedDate: '',
  daySales: 0,
  dayCollected: 0,
  dayOrdersCount: 0,
  dayCanceledCount: 0,
  dayItemsCount: 0,
  averageBasket: 0,
  activeCashiers: 0,
  hourlySales: [],
  cashiers: [],
};

const HourlyTooltip = ({ active, payload, t }) => {
  if (!active || !payload || payload.length === 0) return null;
  const point = payload[0].payload;
  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow px-3 py-2 text-sm">
      <p className="font-semibold text-gray-800 dark:text-gray-100">{point.label}</p>
      <p className="text-primary-600 dark:text-primary-400 font-medium">{formatCurrency(point.sales)}</p>
      <p className="text-gray-500 dark:text-gray-400 text-xs">
        {t('caisse.ordersCountHint', { count: point.orders })}
      </p>
    </div>
  );
};

const cashierName = (cashier) =>
  `${cashier.firstName || ''} ${cashier.lastName || ''}`.trim() || cashier.email || '—';

/**
 * Médaillon de classement. Or / argent / bronze est une convention de rang, pas un statut :
 * ces trois teintes ne peuvent être confondues avec un succès ou une alerte. Hors podium, on
 * reste neutre — le bleu y signalait « info » alors qu'il ne portait aucune information.
 *
 * Le médaillon n'apparaît QUE sur le classement par ventes : trié par nom, une médaille
 * décernée à la troisième ligne ne voudrait plus rien dire.
 */
const RANK_STYLE = [
  'bg-yellow-50 text-yellow-600 dark:bg-yellow-500/10 dark:text-yellow-400',
  'bg-gray-100 text-gray-500 dark:bg-gray-500/15 dark:text-gray-300',
  'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400',
];

const Caisses = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [selectedDate, setSelectedDate] = useState(todayISO);
  const [data, setData] = useState(EMPTY_SUPERVISION);
  const [expandedCashier, setExpandedCashier] = useState(null);
  const [scope, setScope] = useState('all');
  const [sortKey, setSortKey] = useState('sales');

  const fetchSupervision = useCallback(async (date) => {
    try {
      setLoading(true);
      const response = await api.get('/dashboard/cashiers', { params: { date } });
      setData({ ...EMPTY_SUPERVISION, ...response.data });
    } catch (error) {
      console.error('Error fetching cashiers dashboard:', error);
      toast.error(t('caisse.loadError'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    fetchSupervision(selectedDate);
  }, [selectedDate, fetchSupervision]);

  const today = todayISO();
  const isToday = selectedDate === today;

  /**
   * Le rang est figé sur le classement par ventes renvoyé par le backend, puis transporté
   * par la ligne : changer l'ordre d'affichage ne doit pas renuméroter les caissiers.
   */
  const rankedCashiers = useMemo(
    () => data.cashiers.map((cashier, index) => ({ ...cashier, rank: index + 1 })),
    [data.cashiers]
  );

  const visibleCashiers = useMemo(() => {
    const kept = scope === 'active'
      ? rankedCashiers.filter((c) => c.dayOrdersCount > 0)
      : rankedCashiers;

    const sorted = [...kept];
    if (sortKey === 'sales') sorted.sort((a, b) => b.daySales - a.daySales);
    else if (sortKey === 'orders') sorted.sort((a, b) => b.dayOrdersCount - a.dayOrdersCount);
    else if (sortKey === 'basket') sorted.sort((a, b) => b.averageBasket - a.averageBasket);
    else sorted.sort((a, b) => cashierName(a).localeCompare(cashierName(b), 'fr'));
    return sorted;
  }, [rankedCashiers, scope, sortKey]);

  /** Colonnes partagées par les deux formats : l'export ne peut pas diverger du tableau. */
  const exportColumns = useMemo(() => [
    { header: t('caisse.columns.cashier'), value: (c) => cashierName(c) },
    { header: t('common.email'), value: (c) => c.email || '' },
    { header: t('caisse.collected'), value: (c) => formatAmount(c.dayCollected), align: 'right' },
    { header: t('caisse.sales'), value: (c) => formatAmount(c.daySales), align: 'right' },
    { header: t('caisse.orders'), value: (c) => c.dayOrdersCount, align: 'right' },
    { header: t('caisse.canceled'), value: (c) => c.dayCanceledCount, align: 'right' },
    { header: t('caisse.items'), value: (c) => c.dayItemsCount, align: 'right' },
    { header: t('caisse.averageBasket'), value: (c) => formatAmount(c.averageBasket), align: 'right' },
    {
      header: t('caisse.shareOfSales'),
      value: (c) => formatPercent(safeRatio(c.daySales, data.daySales)),
      align: 'right',
    },
  ], [t, data.daySales]);

  const exportSubtitle = t('caisse.exportScope', {
    date: formatDate(selectedDate),
    count: visibleCashiers.length,
  });

  const handleExportCsv = () => {
    exportToCsv({
      filename: `supervision-caisses-${selectedDate}`,
      columns: exportColumns,
      rows: visibleCashiers,
    });
    toast.success(t('caisse.exportDone'));
  };

  const handleExportPdf = () => {
    exportToPdf({
      filename: `supervision-caisses-${selectedDate}`,
      title: t('caisse.supervisionTitle'),
      subtitle: exportSubtitle,
      summary: [
        { label: t('caisse.collected'), value: formatCurrency(data.dayCollected) },
        { label: t('caisse.sales'), value: formatCurrency(data.daySales) },
        { label: t('caisse.orders'), value: String(data.dayOrdersCount) },
        { label: t('caisse.averageBasket'), value: formatCurrency(data.averageBasket) },
      ],
      columns: exportColumns,
      rows: visibleCashiers,
    });
    toast.success(t('caisse.exportDone'));
  };

  const openOrder = (orderId) => navigate(`/orders?orderId=${orderId}`);

  const toggleCashier = (cashierId) =>
    setExpandedCashier((current) => (current === cashierId ? null : cashierId));

  const hasRows = visibleCashiers.length > 0;
  const canExport = hasRows && !loading;

  return (
    <div className="space-y-6">
      {/* En-tête */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <BarChart3 aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('caisse.supervisionTitle')}</h1>
            <p className="page-subtitle">
              {t('caisse.supervisionSubtitle')}
              {isToday && <span className="badge-success ml-2">{t('caisse.today')}</span>}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex items-center gap-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2">
            <Calendar className="w-4 h-4 text-gray-500 dark:text-gray-400" aria-hidden="true" />
            <label htmlFor="supervision-date" className="sr-only">
              {t('caisse.selectDate')}
            </label>
            {/* `max` borne à aujourd'hui : une journée future n'a par définition aucune vente,
                et l'écran affichait sinon des zéros qu'on pouvait lire comme un incident. */}
            <input
              id="supervision-date"
              type="date"
              value={selectedDate}
              max={today}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="bg-transparent text-sm text-gray-800 dark:text-gray-100 focus:outline-none"
            />
          </div>

          {!isToday && (
            <button
              type="button"
              onClick={() => setSelectedDate(today)}
              className="px-3 py-2 text-sm bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
            >
              {t('caisse.backToToday')}
            </button>
          )}

          <button
            type="button"
            onClick={() => fetchSupervision(selectedDate)}
            disabled={loading}
            className="flex items-center gap-2 px-4 py-2 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors disabled:opacity-60"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
            {t('caisse.refresh')}
          </button>

          {/* Exports désactivés tant qu'il n'y a rien à exporter : un fichier à en-tête seul
              se lit comme une journée vide plutôt que comme une absence de sélection. */}
          <button
            type="button"
            onClick={handleExportCsv}
            disabled={!canExport}
            className="btn-secondary py-2 disabled:opacity-50"
          >
            <FileSpreadsheet className="w-4 h-4" aria-hidden="true" />
            {t('caisse.exportCsv')}
          </button>

          <button
            type="button"
            onClick={handleExportPdf}
            disabled={!canExport}
            className="btn-primary py-2 disabled:opacity-50"
          >
            <FileText className="w-4 h-4" aria-hidden="true" />
            {t('caisse.exportPdf')}
          </button>
        </div>
      </div>

      {/* Indicateurs de la journée — mêmes libellés, mêmes jetons et même ordre que « Ma
          caisse » : le responsable et le caissier lisent la même grille de lecture. */}
      <section aria-labelledby="supervision-totals-heading">
        <h2
          id="supervision-totals-heading"
          className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3"
        >
          {t('caisse.dayTotals')}
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
          <StatCard
            title={t('caisse.collected')}
            value={formatCurrency(data.dayCollected)}
            subtitle={t('caisse.collectedHint')}
            icon={CreditCard}
            tone="success"
            loading={loading}
          />
          <StatCard
            title={t('caisse.sales')}
            value={formatCurrency(data.daySales)}
            subtitle={
              data.dayCanceledCount > 0
                ? t('caisse.salesHintCanceled', { count: data.dayCanceledCount })
                : t('caisse.salesHint')
            }
            icon={Euro}
            tone="info"
            loading={loading}
          />
          <StatCard
            title={t('caisse.orders')}
            value={data.dayOrdersCount}
            subtitle={t('caisse.ordersHint', { count: data.dayItemsCount })}
            icon={ShoppingCart}
            tone="accent"
            loading={loading}
          />
          <StatCard
            title={t('caisse.averageBasket')}
            value={formatCurrency(data.averageBasket)}
            subtitle={t('caisse.averageBasketHint')}
            icon={TrendingUp}
            tone="warning"
            loading={loading}
          />
        </div>
      </section>

      {/* Affluence de la journée, tous caissiers confondus. Le backend l'exposait déjà
          (`hourlySales`) sans que la supervision l'affiche. */}
      <motion.section
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        aria-labelledby="supervision-hourly-heading"
        className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-6"
      >
        <div className="flex items-center gap-2 mb-4">
          <BarChart3 className="w-5 h-5 text-primary-600 dark:text-primary-400" aria-hidden="true" />
          <h2 id="supervision-hourly-heading" className="section-title">
            {t('caisse.hourlySales')}
          </h2>
          <span className="text-xs text-gray-400 dark:text-gray-500">{t('caisse.hourlySalesHint')}</span>
        </div>

        {/* « En cours de chargement » et « aucune vente » sont deux états distincts : afficher
            le second pendant la requête annonce à tort une journée sans activité. */}
        {loading ? (
          <div className="h-60 rounded-lg bg-gray-100 dark:bg-gray-700/40 animate-pulse" />
        ) : data.hourlySales.length === 0 ? (
          <div className="py-10 text-center text-sm text-gray-500 dark:text-gray-400">
            <BarChart3 className="w-8 h-8 mx-auto mb-2 text-gray-300 dark:text-gray-600" aria-hidden="true" />
            {t('caisse.noSales')}
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={data.hourlySales} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="currentColor" className="text-gray-100 dark:text-gray-700" />
              <XAxis dataKey="label" tick={{ fontSize: 12, fill: '#647697' }} axisLine={false} tickLine={false} />
              <YAxis
                tick={{ fontSize: 12, fill: '#647697' }}
                axisLine={false}
                tickLine={false}
                width={48}
                tickFormatter={formatCompactCurrency}
              />
              <Tooltip content={<HourlyTooltip t={t} />} cursor={{ fill: 'rgba(33,150,243,0.08)' }} />
              {/* Même bleu de marque que la caisse et les rapports : une série de ventes
                  horaires ne porte pas d'état, elle n'emprunte donc pas le vert de `success`. */}
              <Bar dataKey="sales" fill="#2196f3" radius={[4, 4, 0, 0]} maxBarSize={48} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </motion.section>

      {/* Détail par caissier */}
      <section aria-labelledby="supervision-cashiers-heading" className="space-y-4">
        <div className="flex items-end justify-between flex-wrap gap-3">
          <div>
            <h2 id="supervision-cashiers-heading" className="section-title">
              {t('caisse.detailByCashier')}
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
              {t('caisse.cashiersActiveOf', {
                active: data.activeCashiers,
                total: rankedCashiers.length,
              })}
            </p>
          </div>

          <div className="flex items-center gap-3 flex-wrap">
            <SegmentedFilter
              label={t('caisse.scopeLabel')}
              value={scope}
              onChange={setScope}
              options={[
                { value: 'all', label: t('caisse.allCashiers'), count: rankedCashiers.length },
                {
                  value: 'active',
                  label: t('caisse.onlyActive'),
                  count: rankedCashiers.filter((c) => c.dayOrdersCount > 0).length,
                },
              ]}
            />

            <div className="flex items-center gap-2">
              <label htmlFor="supervision-sort" className="text-sm text-gray-500 dark:text-gray-400">
                {t('caisse.sortBy')}
              </label>
              <select
                id="supervision-sort"
                value={sortKey}
                onChange={(e) => setSortKey(e.target.value)}
                className="px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 dark:text-gray-100 focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
              >
                <option value="sales">{t('caisse.sales')}</option>
                <option value="orders">{t('caisse.orders')}</option>
                <option value="basket">{t('caisse.averageBasket')}</option>
                <option value="name">{t('caisse.sortName')}</option>
              </select>
            </div>
          </div>
        </div>

        {loading ? (
          // Squelettes plutôt qu'un libellé « chargement » : la page garde sa silhouette et
          // ne saute pas quand les lignes arrivent.
          <div className="space-y-4" aria-busy="true">
            {[0, 1, 2].map((index) => (
              <div key={index} className="skeleton h-24 rounded-xl" />
            ))}
          </div>
        ) : !hasRows ? (
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-12 text-center">
            <Users className="empty-state-icon mb-4" aria-hidden="true" />
            <p className="text-gray-600 dark:text-gray-300 font-medium">
              {scope === 'active' ? t('caisse.noActiveCashier') : t('caisse.noCashier')}
            </p>
          </div>
        ) : (
          visibleCashiers.map((cashier, index) => {
            const isExpanded = expandedCashier === cashier.cashierId;
            const isPodium = sortKey === 'sales' && cashier.rank <= 3 && cashier.daySales > 0;
            const rankStyle = isPodium ? RANK_STYLE[cashier.rank - 1] : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-300';
            const share = safeRatio(cashier.daySales, data.daySales);
            const panelId = `cashier-orders-${cashier.cashierId}`;
            // Le backend expose le rôle pour que l'UI puisse signaler une ligne non-caissier
            // (un admin en renfort) plutôt que de la présenter comme un caissier ordinaire.
            const isCashierRole = cashier.role === 'CAISSIER';

            return (
              <motion.article
                key={cashier.cashierId}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: Math.min(index * 0.04, 0.2) }}
                className="bg-white dark:bg-gray-800 rounded-xl shadow-lg overflow-hidden"
              >
                {/* En-tête dépliable.
                 *
                 * Le déclencheur est un vrai <button> — la version précédente n'était qu'un
                 * <div onClick>, hors du parcours clavier et muet pour un lecteur d'écran.
                 * Il ne porte QUE le libellé de bascule : un <button> n'accepte que du contenu
                 * de phrasé, et y enfermer le titre et la grille de chiffres (comme le ferait
                 * une ligne entièrement cliquable) produisait un nom accessible récitant tous
                 * les montants de la ligne à chaque tabulation.
                 *
                 * La ligne reste cliquable à la souris par commodité ; le clavier et les
                 * technologies d'assistance passent par le bouton, qui est le chemin complet. */}
                <div
                  onClick={() => toggleCashier(cashier.cashierId)}
                  className="p-5 sm:p-6 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/40 transition-colors"
                >
                  <div className="flex items-center gap-4">
                    <div className={`w-12 h-12 rounded-full flex items-center justify-center flex-shrink-0 ${rankStyle}`}>
                      {isPodium ? (
                        <Award className="w-6 h-6" aria-hidden="true" />
                      ) : (
                        <span className="text-sm font-bold tabular-nums">{cashier.rank}</span>
                      )}
                    </div>

                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="section-title truncate">{cashierName(cashier)}</h3>
                        <span className="text-xs text-gray-400 dark:text-gray-500">
                          {t('caisse.rank', { rank: cashier.rank })}
                        </span>
                        {!isCashierRole && (
                          <span className="badge-neutral" title={t('caisse.notCashierHint')}>
                            <AlertTriangle className="w-3 h-3" aria-hidden="true" />
                            {t('caisse.notCashierRole')}
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-gray-500 dark:text-gray-400 truncate">{cashier.email}</p>

                      {/* Part du chiffre de la journée : la comparaison entre caissiers se lit
                          d'un coup d'œil, sans reprendre les montants un par un. */}
                      <div className="mt-2 flex items-center gap-2 max-w-xs">
                        <div className="metric-track">
                          <div className="metric-bar-info" style={{ width: `${share * 100}%` }} />
                        </div>
                        <span className="text-xs text-gray-500 dark:text-gray-400 tabular-nums whitespace-nowrap">
                          {formatPercent(share)}
                        </span>
                      </div>
                    </div>

                    {/* Métriques repliées sous `lg` : quatre colonnes de chiffres derrière une
                        barre latérale de 288 px se chevauchaient. */}
                    <dl className="hidden lg:grid grid-cols-4 gap-6 flex-1">
                      <div className="text-center">
                        <dt className="text-xs text-gray-500 dark:text-gray-400">{t('caisse.collected')}</dt>
                        <dd className="text-base font-bold text-gray-900 dark:text-gray-100 tabular-nums">
                          {formatCurrency(cashier.dayCollected)}
                        </dd>
                      </div>
                      <div className="text-center">
                        <dt className="text-xs text-gray-500 dark:text-gray-400">{t('caisse.sales')}</dt>
                        <dd className="text-base font-bold text-gray-900 dark:text-gray-100 tabular-nums">
                          {formatCurrency(cashier.daySales)}
                        </dd>
                      </div>
                      <div className="text-center">
                        <dt className="text-xs text-gray-500 dark:text-gray-400">{t('caisse.orders')}</dt>
                        <dd className="text-base font-bold text-gray-900 dark:text-gray-100 tabular-nums">
                          {cashier.dayOrdersCount}
                          {cashier.dayCanceledCount > 0 && (
                            <span className="ml-1 text-xs font-normal text-gray-500 dark:text-gray-400">
                              (−{cashier.dayCanceledCount})
                            </span>
                          )}
                        </dd>
                      </div>
                      <div className="text-center">
                        <dt className="text-xs text-gray-500 dark:text-gray-400">{t('caisse.averageBasket')}</dt>
                        <dd className="text-base font-bold text-gray-900 dark:text-gray-100 tabular-nums">
                          {formatCurrency(cashier.averageBasket)}
                        </dd>
                      </div>
                    </dl>

                    {/* `stopPropagation` : sans lui, le clic remonterait au conteneur qui
                        porte la même bascule et l'annulerait aussitôt.
                        `aria-label` explicite car le libellé visible disparaît sous `sm`
                        (`display:none` le retire aussi de l'arbre d'accessibilité) — le bouton
                        y resterait sans nom. */}
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleCashier(cashier.cashierId);
                      }}
                      aria-expanded={isExpanded}
                      aria-controls={panelId}
                      aria-label={`${isExpanded ? t('caisse.hideOrders') : t('caisse.showOrders')} — ${cashierName(cashier)}`}
                      className="flex items-center gap-1 text-sm text-gray-400 dark:text-gray-500 flex-shrink-0 rounded-lg px-2 py-1 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                    >
                      <span className="hidden sm:inline">
                        {isExpanded ? t('caisse.hideOrders') : t('caisse.showOrders')}
                      </span>
                      <ChevronDown
                        className={`w-5 h-5 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                        aria-hidden="true"
                      />
                    </button>
                  </div>

                  {/* Rappel des chiffres sous `lg`, où la grille ci-dessus est masquée. */}
                  <dl className="lg:hidden mt-4 grid grid-cols-2 gap-3">
                    <div className="mini-stat-success">
                      <dt>{t('caisse.collected')}</dt>
                      <dd className="font-bold tabular-nums">{formatCurrency(cashier.dayCollected)}</dd>
                    </div>
                    <div className="mini-stat-info">
                      <dt>{t('caisse.sales')}</dt>
                      <dd className="font-bold tabular-nums">{formatCurrency(cashier.daySales)}</dd>
                    </div>
                    <div className="mini-stat-accent">
                      <dt>{t('caisse.orders')}</dt>
                      <dd className="font-bold tabular-nums">{cashier.dayOrdersCount}</dd>
                    </div>
                    <div className="mini-stat-warning">
                      <dt>{t('caisse.averageBasket')}</dt>
                      <dd className="font-bold tabular-nums">{formatCurrency(cashier.averageBasket)}</dd>
                    </div>
                  </dl>
                </div>

                <AnimatePresence initial={false}>
                  {isExpanded && (
                    <motion.div
                      id={panelId}
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={{ duration: 0.25 }}
                      className="border-t border-gray-200 dark:border-gray-700 overflow-hidden"
                    >
                      <div className="bg-gray-50 dark:bg-gray-900/40">
                        <div className="px-6 py-3 flex items-center justify-between flex-wrap gap-2">
                          <h4 className="subsection-title">
                            {t('caisse.ordersOfDayAll', { date: formatDate(data.selectedDate) })}
                          </h4>
                          <span className="text-xs text-gray-500 dark:text-gray-400">
                            {t('caisse.ordersHonored', { count: cashier.dayOrdersCount })}
                            {cashier.dayCanceledCount > 0
                              && ` · ${t('caisse.ordersCanceled', { count: cashier.dayCanceledCount })}`}
                          </span>
                        </div>

                        {cashier.dayOrders.length === 0 ? (
                          <p className="px-6 py-10 text-center text-sm text-gray-500 dark:text-gray-400">
                            {t('caisse.noOrders')}
                          </p>
                        ) : (
                          <div className="overflow-x-auto">
                            <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                              <thead>
                                <tr>
                                  <th scope="col" className="table-th">{t('caisse.columns.time')}</th>
                                  <th scope="col" className="table-th">{t('caisse.columns.orderNumber')}</th>
                                  <th scope="col" className="table-th">{t('caisse.columns.client')}</th>
                                  <th scope="col" className="table-th-right">{t('caisse.columns.items')}</th>
                                  <th scope="col" className="table-th">{t('caisse.columns.status')}</th>
                                  <th scope="col" className="table-th-right">{t('caisse.columns.amount')}</th>
                                </tr>
                              </thead>
                              <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                                {cashier.dayOrders.map((order) => {
                                  const isCanceled = order.status === 'CANCELED';
                                  return (
                                    <tr
                                      key={order.id}
                                      tabIndex={0}
                                      onClick={() => openOrder(order.id)}
                                      onKeyDown={(e) => {
                                        if (e.key === 'Enter' || e.key === ' ') {
                                          e.preventDefault();
                                          openOrder(order.id);
                                        }
                                      }}
                                      className={`hover:bg-gray-50 dark:hover:bg-gray-700/40 cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary-500 ${
                                        isCanceled ? 'opacity-60' : ''
                                      }`}
                                    >
                                      <td className="px-6 py-3 whitespace-nowrap text-sm text-gray-900 dark:text-gray-100">
                                        <span className="inline-flex items-center gap-1">
                                          <Clock className="w-3 h-3 text-gray-400 dark:text-gray-500" aria-hidden="true" />
                                          {formatTime(order.createdAt)}
                                        </span>
                                      </td>
                                      <td className="px-6 py-3 whitespace-nowrap text-sm font-medium text-primary-600 dark:text-primary-400">
                                        {order.orderNumber}
                                      </td>
                                      <td className="px-6 py-3 whitespace-nowrap text-sm text-gray-900 dark:text-gray-100">
                                        {/* Le backend renvoie « N/A » pour une commande sans client. */}
                                        {!order.clientName || order.clientName === 'N/A'
                                          ? t('caisse.unregisteredClient')
                                          : order.clientName}
                                      </td>
                                      <td className="px-6 py-3 whitespace-nowrap text-sm text-right text-gray-900 dark:text-gray-100 tabular-nums">
                                        {order.itemsCount ?? 0}
                                      </td>
                                      <td className="px-6 py-3 whitespace-nowrap">
                                        {/* Résolution partagée : une commande facturée puis réglée
                                            s'affiche « Payée » ici comme sur l'écran du caissier.
                                            Cette page tenait sa propre table de libellés, qui
                                            ignorait le règlement. */}
                                        <OrderStatusBadge order={order} />
                                      </td>
                                      <td
                                        className={`px-6 py-3 whitespace-nowrap text-sm font-semibold text-right tabular-nums ${
                                          isCanceled
                                            ? 'line-through text-gray-500 dark:text-gray-400'
                                            : 'text-gray-900 dark:text-gray-100'
                                        }`}
                                      >
                                        {formatCurrency(order.finalAmount)}
                                      </td>
                                    </tr>
                                  );
                                })}
                              </tbody>
                            </table>
                          </div>
                        )}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </motion.article>
            );
          })
        )}
      </section>
    </div>
  );
};

export default Caisses;
