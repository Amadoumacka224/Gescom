import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  ShoppingCart,
  Users,
  AlertTriangle,
  Euro,
  Package,
  FileText,
  Truck,
  CreditCard,
  ArrowRight,
  LayoutDashboard
} from 'lucide-react';
import { motion } from 'framer-motion';
import api from '../services/api';
import OrderStatusBadge from '../components/OrderStatusBadge';
import StatCard from '../components/StatCard';
import SummaryPanel from '../components/SummaryPanel';
import { formatCurrency, safeRatio } from '../utils/format';

const Dashboard = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalSales: 0,
    totalOrders: 0,
    pendingOrders: 0,
    confirmedOrders: 0,
    invoicedOrders: 0,
    deliveredOrders: 0,
    canceledOrders: 0,
    totalClients: 0,
    lowStock: 0,
    totalInvoices: 0,
    totalRevenue: 0,
    invoicedAmount: 0,
    pendingAmount: 0,
    unpaidInvoices: 0,
    partiallyPaidInvoices: 0,
    paidInvoices: 0,
    canceledInvoices: 0,
    totalDeliveries: 0,
    pendingDeliveries: 0,
    deliveredDeliveries: 0,
    ordersToSchedule: 0,
  });
  const [recentOrders, setRecentOrders] = useState([]);
  const [topStockProducts, setTopStockProducts] = useState([]);
  const [lowStockProducts, setLowStockProducts] = useState([]);

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    try {
      setLoading(true);
      const response = await api.get('/dashboard/overview');
      const data = response.data;

      setStats({
        totalSales: data.totalSales || 0,
        totalOrders: data.totalOrders || 0,
        pendingOrders: data.pendingOrders || 0,
        confirmedOrders: data.confirmedOrders || 0,
        invoicedOrders: data.invoicedOrders || 0,
        deliveredOrders: data.deliveredOrders || 0,
        canceledOrders: data.canceledOrders || 0,
        totalClients: data.totalClients || 0,
        lowStock: data.lowStock || 0,
        totalInvoices: data.totalInvoices || 0,
        totalRevenue: data.totalRevenue || 0,
        invoicedAmount: data.invoicedAmount || 0,
        pendingAmount: data.pendingAmount || 0,
        unpaidInvoices: data.unpaidInvoices || 0,
        partiallyPaidInvoices: data.partiallyPaidInvoices || 0,
        paidInvoices: data.paidInvoices || 0,
        canceledInvoices: data.canceledInvoices || 0,
        totalDeliveries: data.totalDeliveries || 0,
        pendingDeliveries: data.pendingDeliveries || 0,
        deliveredDeliveries: data.deliveredDeliveries || 0,
        ordersToSchedule: data.ordersToSchedule || 0,
      });

      setRecentOrders(data.recentOrders || []);
      setTopStockProducts(data.topStockProducts || []);
      setLowStockProducts(data.lowStockProducts || []);
    } catch (error) {
      console.error('Error fetching dashboard data:', error);
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadge = (order) => <OrderStatusBadge order={order} />;

  /* --- Dérivés des panneaux de synthèse ---
   *
   * Rien n'est reconstitué ici qui dépende d'un invariant du backend : les montants facturés
   * (`invoicedAmount`) et le reste à planifier (`ordersToSchedule`) sont calculés côté serveur,
   * sur les données réelles, et repris tels quels. Seules subsistent deux sommes de compteurs
   * exacts et exhaustifs (les cinq statuts de commande couvrent la table entière) :
   *   - `activeOrders` — hors annulées : le périmètre du taux de finalisation, identique à
   *     celui de `totalSales` et du sous-titre « N commandes honorées » ;
   *   - `ordersInProgress` — ni livrées ni annulées, donc encore à traiter.
   */
  const activeOrders = stats.totalOrders - stats.canceledOrders;
  const ordersInProgress = stats.pendingOrders + stats.confirmedOrders + stats.invoicedOrders;

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">{t('common.loading')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <LayoutDashboard aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('dashboard.welcome')}</h1>
            <p className="page-subtitle">{t('dashboard.subtitle')}</p>
          </div>
        </div>
      </div>

      {/* Stats principales — même grille que la supervision des caisses : quatre colonnes
          seulement à partir de `xl`, la barre latérale mangeant 288 px dès `lg`. */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 sm:gap-6">
        <StatCard
          title={t('dashboard.totalSales')}
          value={formatCurrency(stats.totalSales)}
          icon={Euro}
          tone="success"
          subtitle={`${activeOrders} commandes honorées`}
        />
        <StatCard
          title={t('dashboard.revenueCollected')}
          value={formatCurrency(stats.totalRevenue)}
          icon={CreditCard}
          tone="info"
          subtitle={`${stats.paidInvoices} factures payees`}
        />
        <StatCard
          title={t('dashboard.totalClients')}
          value={stats.totalClients}
          icon={Users}
          tone="accent"
        />
        <StatCard
          title={t('dashboard.lowStock')}
          value={stats.lowStock}
          icon={AlertTriangle}
          tone="warning"
          subtitle={stats.lowStock > 0 ? 'Attention requise' : 'Stock OK'}
        />
      </div>

      {/* Panneaux de synthèse : Commandes / Factures / Livraisons.
       *
       * Deux colonnes dès `md` et trois seulement à partir de `xl` : à 1024 px, la barre
       * latérale prend 288 px et trois colonnes ne laisseraient que ~220 px par panneau,
       * trop peu pour l'anneau et son libellé côte à côte. */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4 sm:gap-6">
        <SummaryPanel
          icon={ShoppingCart}
          tone="info"
          title={t('dashboard.orders')}
          total={stats.totalOrders}
          totalLabel={t('dashboard.ordersTotal')}
          rate={{
            ratio: safeRatio(stats.deliveredOrders, activeOrders),
            label: t('dashboard.completionRate'),
            caption: t('dashboard.completionCaption', {
              done: stats.deliveredOrders,
              active: activeOrders,
            }),
          }}
          highlight={{
            label: t('dashboard.inProgress'),
            value: ordersInProgress,
            tone: 'info',
          }}
          rows={[
            { key: 'PENDING', label: t('dashboard.status.pending'), value: stats.pendingOrders, tone: 'warning' },
            { key: 'CONFIRMED', label: t('dashboard.status.confirmed'), value: stats.confirmedOrders, tone: 'info' },
            { key: 'INVOICED', label: t('dashboard.status.invoiced'), value: stats.invoicedOrders, tone: 'accent' },
            { key: 'DELIVERED', label: t('dashboard.status.delivered'), value: stats.deliveredOrders, tone: 'success' },
            { key: 'CANCELED', label: t('dashboard.status.canceled'), value: stats.canceledOrders, tone: 'danger' },
          ]}
          actionLabel={t('dashboard.viewOrders')}
          onAction={() => navigate('/orders')}
        />

        <SummaryPanel
          icon={FileText}
          tone="accent"
          title={t('dashboard.invoices')}
          total={stats.totalInvoices}
          totalLabel={t('dashboard.invoicesTotal')}
          rate={{
            ratio: safeRatio(stats.totalRevenue, stats.invoicedAmount),
            label: t('dashboard.collectionRate'),
            caption: t('dashboard.collectionCaption', {
              collected: formatCurrency(stats.totalRevenue),
              total: formatCurrency(stats.invoicedAmount),
            }),
          }}
          highlight={{
            label: t('dashboard.toCollect'),
            value: formatCurrency(stats.pendingAmount),
            tone: 'warning',
          }}
          rows={[
            { key: 'PAID', label: t('dashboard.status.paid'), value: stats.paidInvoices, tone: 'success' },
            { key: 'PARTIALLY_PAID', label: t('dashboard.status.partiallyPaid'), value: stats.partiallyPaidInvoices, tone: 'warning' },
            { key: 'UNPAID', label: t('dashboard.status.unpaid'), value: stats.unpaidInvoices, tone: 'danger' },
            { key: 'CANCELED', label: t('dashboard.status.canceled'), value: stats.canceledInvoices, tone: 'neutral' },
          ]}
          actionLabel={t('dashboard.viewInvoices')}
          onAction={() => navigate('/invoices')}
          delay={0.08}
        />

        <SummaryPanel
          icon={Truck}
          tone="success"
          title={t('dashboard.deliveries')}
          total={stats.totalDeliveries}
          totalLabel={t('dashboard.deliveriesTotal')}
          rate={{
            ratio: safeRatio(stats.deliveredDeliveries, stats.totalDeliveries),
            label: t('dashboard.deliveryRate'),
            caption: t('dashboard.deliveryCaption', {
              done: stats.deliveredDeliveries,
              total: stats.totalDeliveries,
            }),
          }}
          highlight={{
            label: t('dashboard.toSchedule'),
            value: stats.ordersToSchedule,
            tone: 'accent',
          }}
          rows={[
            { key: 'PENDING', label: t('dashboard.status.pending'), value: stats.pendingDeliveries, tone: 'warning' },
            { key: 'DELIVERED', label: t('dashboard.status.delivered'), value: stats.deliveredDeliveries, tone: 'success' },
          ]}
          actionLabel={t('dashboard.viewDeliveries')}
          onAction={() => navigate('/deliveries')}
          delay={0.16}
        />
      </div>

      {/* Section inferieure */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Commandes recentes */}
        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          className="card"
        >
          <div className="flex items-center justify-between mb-6">
            <h2 className="section-title">
              {t('dashboard.recentOrders')}
            </h2>
            <button
              onClick={() => navigate('/orders')}
              className="text-sm text-primary-600 hover:text-primary-700 font-medium flex items-center gap-1"
            >
              Voir tout <ArrowRight className="w-4 h-4" />
            </button>
          </div>
          <div className="space-y-4">
            {recentOrders.length > 0 ? (
              recentOrders.map((order) => (
                <div
                  key={order.id}
                  className="flex items-center justify-between p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-primary-100 rounded-lg flex items-center justify-center">
                      <Package className="w-5 h-5 text-primary-600" />
                    </div>
                    <div>
                      <p className="font-medium text-gray-900">{order.orderNumber}</p>
                      <p className="text-sm text-gray-600">{order.clientName}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="subsection-title">{formatCurrency(order.finalAmount)}</p>
                    {getStatusBadge(order)}
                  </div>
                </div>
              ))
            ) : (
              <p className="text-center text-gray-500 py-8">{t('dashboard.noRecentOrders')}</p>
            )}
          </div>
        </motion.div>

        {/* Produits : stock + alertes */}
        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          className="card"
        >
          <div className="flex items-center justify-between mb-1">
            <h2 className="section-title">
              Stock le plus important
            </h2>
            <button
              onClick={() => navigate('/products')}
              className="text-sm text-primary-600 hover:text-primary-700 font-medium flex items-center gap-1"
            >
              Voir tout <ArrowRight className="w-4 h-4" />
            </button>
          </div>
          <p className="text-xs text-gray-500 mb-6">{t('dashboard.topStockHint')}</p>
          <div className="space-y-4">
            {topStockProducts.length > 0 ? (
              topStockProducts.map((product) => {
                const maxStock = topStockProducts[0]?.stock || 1;
                const percentage = (product.stock / maxStock) * 100;
                return (
                  <div key={product.id} className="flex items-center gap-4">
                    <div className="flex-1">
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-medium text-gray-900">{product.name}</span>
                        <span className="text-sm text-gray-600">{product.stock} en stock</span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className="bg-gradient-to-r from-primary-500 to-primary-600 h-2 rounded-full transition-all duration-300"
                          style={{ width: `${percentage}%` }}
                        ></div>
                      </div>
                    </div>
                  </div>
                );
              })
            ) : (
              <p className="text-center text-gray-500 py-8">{t('products.noProducts')}</p>
            )}
          </div>

          {/* Alertes stock bas */}
          {lowStockProducts.length > 0 && (
            <div className="mt-6 pt-4 border-t border-gray-200">
              <h3 className="subsection-title font-bold text-red-600 mb-3 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4" />
                Alertes stock bas
              </h3>
              <div className="space-y-2">
                {lowStockProducts.map((product) => (
                  <div key={product.id} className="flex items-center justify-between p-2 bg-red-50 rounded-lg">
                    <span className="text-sm font-medium text-gray-900">{product.name}</span>
                    <span className="text-sm font-bold text-red-600">{product.stock} restant(s)</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </motion.div>
      </div>
    </div>
  );
};

export default Dashboard;
