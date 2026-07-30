import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/AuthContext';
import {
  Euro,
  ShoppingCart,
  TrendingUp,
  Calendar,
  RefreshCw,
  FileText,
  CreditCard,
  Plus,
  ArrowRight,
  Clock,
  BarChart3,
} from 'lucide-react';
import { motion } from 'framer-motion';
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
import { formatCurrency, formatCompactCurrency, formatTime, formatDate, todayISO } from '../utils/format';

const EMPTY_DASHBOARD = {
  selectedDate: '',
  daySales: 0,
  dayCollected: 0,
  dayOrdersCount: 0,
  dayCanceledCount: 0,
  dayItemsCount: 0,
  averageBasket: 0,
  hourlySales: [],
  dayOrders: [],
};

const HourlyTooltip = ({ active, payload, t }) => {
  if (!active || !payload || payload.length === 0) return null;
  const d = payload[0].payload;
  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow px-3 py-2 text-sm">
      <p className="font-semibold text-gray-800 dark:text-gray-100">{d.label}</p>
      {/* Même teinte que la barre qu'il décrit (`primary`) : le vert du jeton `success` porte
          un ÉTAT, or une vente horaire n'en porte aucun. */}
      <p className="text-primary-700 dark:text-primary-300 font-medium">{formatCurrency(d.sales)}</p>
      {/* `ordersHint` compte des ARTICLES (« N article(s) vendu(s) ») : l'appliquer au nombre
          de commandes de la tranche horaire annonçait un volume d'articles qui n'en était pas un. */}
      <p className="text-gray-500 dark:text-gray-400 text-xs">{t('caisse.ordersCountHint', { count: d.orders })}</p>
    </div>
  );
};

const Caisse = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [selectedDate, setSelectedDate] = useState(todayISO);
  const [data, setData] = useState(EMPTY_DASHBOARD);

  const fetchDashboard = useCallback(async (date) => {
    try {
      setLoading(true);
      const response = await api.get('/dashboard/cashier', { params: { date } });
      setData(response.data);
    } catch (error) {
      console.error('Error fetching cashier dashboard:', error);
      toast.error(t('caisse.loadError'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    fetchDashboard(selectedDate);
  }, [selectedDate, fetchDashboard]);

  const today = todayISO();
  const isToday = selectedDate === today;

  const openOrder = (orderId) => navigate(`/orders?orderId=${orderId}`);

  return (
    <div className="space-y-6">
      {/* En-tête */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <Euro aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('caisse.title')}</h1>
            <p className="page-subtitle">
              {user?.firstName} {user?.lastName}
              {isToday && (
                <span className="badge-success ml-2">{t('caisse.today')}</span>
              )}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex items-center gap-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg px-3 py-2">
            <Calendar className="w-4 h-4 text-gray-500 dark:text-gray-400" aria-hidden="true" />
            <label htmlFor="caisse-date" className="sr-only">
              {t('caisse.selectDate')}
            </label>
            <input
              id="caisse-date"
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
            onClick={() => fetchDashboard(selectedDate)}
            className="flex items-center gap-2 px-4 py-2 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors disabled:opacity-60"
            disabled={loading}
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
            {t('caisse.refresh')}
          </button>

          <button
            type="button"
            onClick={() => navigate('/orders')}
            /* `py-2` : `btn-primary` monte à `py-2.5`, ce qui décalerait ce bouton
               de 4 px par rapport aux deux autres actions de l'en-tête. */
            className="btn-primary py-2"
          >
            <Plus className="w-4 h-4" aria-hidden="true" />
            {t('caisse.newOrder')}
          </button>
        </div>
      </div>

      {/* Indicateurs de la journée */}
      <section aria-labelledby="caisse-day-heading">
        <h2
          id="caisse-day-heading"
          className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3"
        >
          {t('caisse.myDay')}
        </h2>
        {/* Quatre colonnes seulement à partir de `xl` : la barre latérale prend 288 px dès `lg`. */}
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

      {/* Ventes par heure — pics d'affluence de la journée */}
      <motion.section
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        aria-labelledby="caisse-hourly-heading"
        className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-6"
      >
        <div className="flex items-center gap-2 mb-4">
          <BarChart3 className="w-5 h-5 text-primary-600 dark:text-primary-400" aria-hidden="true" />
          <h2 id="caisse-hourly-heading" className="section-title">
            {t('caisse.hourlySales')}
          </h2>
          <span className="text-xs text-gray-400 dark:text-gray-500">{t('caisse.hourlySalesHint')}</span>
        </div>

        {/* On distingue « en cours de chargement » de « aucune vente » : afficher l'état vide
            pendant la requête annonçait à tort une journée sans activité. */}
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
              {/* Bleu de marque (#2196F3, `primary-500`), comme la courbe d'évolution des
                * Rapports : une série de ventes horaires ne porte pas d'état, elle n'a donc pas
                * à emprunter le vert du jeton `success`. */}
              <Bar dataKey="sales" fill="#2196f3" radius={[4, 4, 0, 0]} maxBarSize={48} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </motion.section>

      {/* Commandes du jour */}
      <motion.section
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        aria-labelledby="caisse-orders-heading"
        className="bg-white dark:bg-gray-800 rounded-xl shadow-lg overflow-hidden"
      >
        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between flex-wrap gap-2">
          <h2 id="caisse-orders-heading" className="section-title">
            {t('caisse.ordersOfDay', { date: formatDate(data.selectedDate) })}
          </h2>
          <span className="text-sm text-gray-500 dark:text-gray-400">
            {t('caisse.ordersHonored', { count: data.dayOrdersCount })}
            {data.dayCanceledCount > 0 && ` · ${t('caisse.ordersCanceled', { count: data.dayCanceledCount })}`}
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
            <thead className="bg-gray-50 dark:bg-gray-900/40">
              <tr>
                <th scope="col" className="table-th">
                  {t('caisse.columns.time')}
                </th>
                <th scope="col" className="table-th">
                  {t('caisse.columns.orderNumber')}
                </th>
                <th scope="col" className="table-th">
                  {t('caisse.columns.client')}
                </th>
                <th scope="col" className="table-th">
                  {t('caisse.columns.status')}
                </th>
                <th scope="col" className="table-th-right">
                  {t('caisse.columns.amount')}
                </th>
              </tr>
            </thead>
            <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
              {loading ? (
                <tr>
                  <td colSpan="5" className="px-6 py-12 text-center text-gray-500 dark:text-gray-400">
                    <RefreshCw className="w-8 h-8 animate-spin mx-auto mb-2" aria-hidden="true" />
                    {t('common.loading')}
                  </td>
                </tr>
              ) : data.dayOrders.length === 0 ? (
                <tr>
                  <td colSpan="5" className="px-6 py-12 text-center">
                    <FileText className="empty-state-icon mb-3" aria-hidden="true" />
                    <p className="text-gray-500 dark:text-gray-400 font-medium">{t('caisse.noOrders')}</p>
                    <button
                      type="button"
                      onClick={() => navigate('/orders')}
                      className="mt-3 text-sm text-primary-600 dark:text-primary-400 hover:text-primary-700 dark:hover:text-primary-300 font-medium inline-flex items-center gap-1"
                    >
                      {t('caisse.createOrder')} <ArrowRight className="w-3 h-3" aria-hidden="true" />
                    </button>
                  </td>
                </tr>
              ) : (
                data.dayOrders.map((order) => {
                  const isCanceled = order.status === 'CANCELED';
                  return (
                    <tr
                      key={order.id}
                      /* La ligne reste cliquable à la souris, mais ce n'est plus elle qui porte
                         le focus. Une `<tr tabindex=0>` s'annonce « ligne » — jamais ce qu'elle
                         ouvre — et oblige à recâbler Entrée/Espace à la main. Le numéro de
                         commande ci-dessous est un vrai bouton, nommé : il est atteignable au
                         clavier, annoncé correctement, et hérite de l'anneau de focus commun
                         (`:focus-visible` dans index.css) sans gestion de touches. */
                      onClick={() => openOrder(order.id)}
                      className={`hover:bg-gray-50 dark:hover:bg-gray-700/40 cursor-pointer transition-colors ${
                        isCanceled ? 'opacity-60' : ''
                      }`}
                    >
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-gray-100">
                        <span className="inline-flex items-center gap-1">
                          <Clock className="w-3 h-3 text-gray-400 dark:text-gray-500" aria-hidden="true" />
                          {formatTime(order.createdAt)}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                        {/* Porteur de l'action de la ligne : c'est lui qui reçoit le focus et
                            qui annonce au lecteur d'écran ce que l'activation va faire. */}
                        <button
                          type="button"
                          onClick={() => openOrder(order.id)}
                          aria-label={t('caisse.openOrderAria', { number: order.orderNumber })}
                          className="rounded font-medium text-blue-600 hover:underline dark:text-blue-400"
                        >
                          {order.orderNumber}
                        </button>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-gray-100">
                        {/* Le backend renvoie « N/A » pour une commande sans client. */}
                        {!order.clientName || order.clientName === 'N/A'
                          ? t('caisse.unregisteredClient')
                          : order.clientName}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <OrderStatusBadge order={order} />
                      </td>
                      <td
                        className={`px-6 py-4 whitespace-nowrap text-sm font-semibold text-right ${
                          isCanceled ? 'line-through text-gray-500 dark:text-gray-400' : 'text-gray-900 dark:text-gray-100'
                        }`}
                      >
                        {formatCurrency(order.finalAmount)}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </motion.section>
    </div>
  );
};

export default Caisse;
