import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  TrendingUp,
  ShoppingCart,
  Users,
  AlertTriangle,
  DollarSign,
  Package,
  FileText,
  Truck,
  CheckCircle,
  Clock,
  CreditCard,
  ArrowRight
} from 'lucide-react';
import { motion } from 'framer-motion';
import api from '../services/api';

const StatCard = ({ title, value, icon: Icon, color, subtitle }) => (
  <motion.div
    initial={{ opacity: 0, y: 20 }}
    animate={{ opacity: 1, y: 0 }}
    whileHover={{ y: -4 }}
    className="card"
  >
    <div className="flex items-center justify-between">
      <div>
        <p className="text-sm font-medium text-gray-600 mb-1">{title}</p>
        <h3 className="text-3xl font-bold text-gray-900">{value}</h3>
        {subtitle && (
          <p className="text-sm mt-2 text-gray-500">{subtitle}</p>
        )}
      </div>
      <div className={`p-4 bg-gradient-to-br ${color} rounded-xl`}>
        <Icon className="w-8 h-8 text-white" />
      </div>
    </div>
  </motion.div>
);

const MiniStat = ({ label, value, color }) => (
  <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
    <span className="text-sm text-gray-600">{label}</span>
    <span className={`text-sm font-bold ${color}`}>{value}</span>
  </div>
);

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
    pendingAmount: 0,
    unpaidInvoices: 0,
    paidInvoices: 0,
    totalDeliveries: 0,
    pendingDeliveries: 0,
    deliveredDeliveries: 0,
    canceledDeliveries: 0,
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
        pendingAmount: data.pendingAmount || 0,
        unpaidInvoices: data.unpaidInvoices || 0,
        paidInvoices: data.paidInvoices || 0,
        totalDeliveries: data.totalDeliveries || 0,
        pendingDeliveries: data.pendingDeliveries || 0,
        deliveredDeliveries: data.deliveredDeliveries || 0,
        canceledDeliveries: data.canceledDeliveries || 0,
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

  const getStatusBadge = (status) => {
    const badges = {
      PENDING: { class: 'badge-warning', text: 'En attente' },
      CONFIRMED: { class: 'badge-info', text: 'Confirmee' },
      INVOICED: { class: 'badge-primary', text: 'Facturee' },
      DELIVERED: { class: 'badge-success', text: 'Livree' },
      CANCELED: { class: 'badge-danger', text: 'Annulee' }
    };
    const badge = badges[status] || badges.PENDING;
    return <span className={badge.class}>{badge.text}</span>;
  };

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('fr-FR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">Chargement des donnees...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900 mb-2">
          {t('dashboard.welcome')}
        </h1>
        <p className="text-gray-600">
          Voici un apercu de votre activite commerciale
        </p>
      </div>

      {/* Stats principales */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          title={t('dashboard.totalSales')}
          value={`${formatCurrency(stats.totalSales)} €`}
          icon={DollarSign}
          color="from-green-500 to-emerald-600"
          subtitle={`${stats.totalOrders - stats.canceledOrders} commandes honorées`}
        />
        <StatCard
          title="Revenus encaisses"
          value={`${formatCurrency(stats.totalRevenue)} €`}
          icon={CreditCard}
          color="from-blue-500 to-cyan-600"
          subtitle={`${stats.paidInvoices} factures payees`}
        />
        <StatCard
          title={t('dashboard.totalClients')}
          value={stats.totalClients}
          icon={Users}
          color="from-purple-500 to-pink-600"
        />
        <StatCard
          title={t('dashboard.lowStock')}
          value={stats.lowStock}
          icon={AlertTriangle}
          color="from-orange-500 to-red-600"
          subtitle={stats.lowStock > 0 ? 'Attention requise' : 'Stock OK'}
        />
      </div>

      {/* Blocs de synthese : Commandes / Factures / Livraisons */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Commandes */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="card"
        >
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <ShoppingCart className="w-5 h-5 text-blue-600" />
              Commandes
            </h3>
            <span className="text-2xl font-bold text-blue-600">{stats.totalOrders}</span>
          </div>
          <div className="space-y-2">
            <MiniStat label="En attente" value={stats.pendingOrders} color="text-yellow-600" />
            <MiniStat label="Confirmees" value={stats.confirmedOrders} color="text-blue-600" />
            <MiniStat label="Facturees" value={stats.invoicedOrders} color="text-purple-600" />
            <MiniStat label="Livrees" value={stats.deliveredOrders} color="text-green-600" />
            <MiniStat label="Annulees" value={stats.canceledOrders} color="text-red-600" />
          </div>
          <button
            onClick={() => navigate('/orders')}
            className="w-full mt-4 text-sm text-blue-600 hover:text-blue-700 font-medium flex items-center justify-center gap-1 py-2 rounded-lg hover:bg-blue-50 transition-colors"
          >
            Voir les commandes <ArrowRight className="w-4 h-4" />
          </button>
        </motion.div>

        {/* Factures */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="card"
        >
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <FileText className="w-5 h-5 text-purple-600" />
              Factures
            </h3>
            <span className="text-2xl font-bold text-purple-600">{stats.totalInvoices}</span>
          </div>
          <div className="space-y-2">
            <MiniStat label="Payees" value={stats.paidInvoices} color="text-green-600" />
            <MiniStat label="Non payees" value={stats.unpaidInvoices} color="text-red-600" />
            <MiniStat label="En attente" value={`${formatCurrency(stats.pendingAmount)} €`} color="text-orange-600" />
          </div>
          <button
            onClick={() => navigate('/invoices')}
            className="w-full mt-4 text-sm text-purple-600 hover:text-purple-700 font-medium flex items-center justify-center gap-1 py-2 rounded-lg hover:bg-purple-50 transition-colors"
          >
            Voir les factures <ArrowRight className="w-4 h-4" />
          </button>
        </motion.div>

        {/* Livraisons */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="card"
        >
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              <Truck className="w-5 h-5 text-orange-600" />
              Livraisons
            </h3>
            <span className="text-2xl font-bold text-orange-600">{stats.totalDeliveries}</span>
          </div>
          <div className="space-y-2">
            <MiniStat label="En attente" value={stats.pendingDeliveries} color="text-yellow-600" />
            <MiniStat label="Livrees" value={stats.deliveredDeliveries} color="text-green-600" />
            <MiniStat label="Annulees" value={stats.canceledDeliveries} color="text-red-600" />
          </div>
          <button
            onClick={() => navigate('/deliveries')}
            className="w-full mt-4 text-sm text-orange-600 hover:text-orange-700 font-medium flex items-center justify-center gap-1 py-2 rounded-lg hover:bg-orange-50 transition-colors"
          >
            Voir les livraisons <ArrowRight className="w-4 h-4" />
          </button>
        </motion.div>
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
            <h2 className="text-xl font-bold text-gray-900">
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
                    <p className="font-semibold text-gray-900">{formatCurrency(order.finalAmount)} €</p>
                    {getStatusBadge(order.status)}
                  </div>
                </div>
              ))
            ) : (
              <p className="text-center text-gray-500 py-8">Aucune commande recente</p>
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
            <h2 className="text-xl font-bold text-gray-900">
              Stock le plus important
            </h2>
            <button
              onClick={() => navigate('/products')}
              className="text-sm text-primary-600 hover:text-primary-700 font-medium flex items-center gap-1"
            >
              Voir tout <ArrowRight className="w-4 h-4" />
            </button>
          </div>
          <p className="text-xs text-gray-500 mb-6">Produits avec les plus grandes quantites en stock — pas un classement des ventes</p>
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
              <p className="text-center text-gray-500 py-8">Aucun produit disponible</p>
            )}
          </div>

          {/* Alertes stock bas */}
          {lowStockProducts.length > 0 && (
            <div className="mt-6 pt-4 border-t border-gray-200">
              <h3 className="text-sm font-bold text-red-600 mb-3 flex items-center gap-2">
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
