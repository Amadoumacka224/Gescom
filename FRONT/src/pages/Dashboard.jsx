import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  TrendingUp,
  ShoppingCart,
  Users,
  AlertTriangle,
  DollarSign,
  Package
} from 'lucide-react';
import { motion } from 'framer-motion';
import api from '../services/api';

const StatCard = ({ title, value, icon: Icon, color, trend }) => (
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
        {trend && (
          <p className={`text-sm mt-2 flex items-center gap-1 ${trend > 0 ? 'text-green-600' : 'text-red-600'}`}>
            <TrendingUp className="w-4 h-4" />
            {trend > 0 ? '+' : ''}{trend}%
          </p>
        )}
      </div>
      <div className={`p-4 bg-gradient-to-br ${color} rounded-xl`}>
        <Icon className="w-8 h-8 text-white" />
      </div>
    </div>
  </motion.div>
);

const Dashboard = () => {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalSales: 0,
    totalOrders: 0,
    totalClients: 0,
    lowStock: 0,
  });
  const [recentOrders, setRecentOrders] = useState([]);
  const [topProducts, setTopProducts] = useState([]);

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
        totalClients: data.totalClients || 0,
        lowStock: data.lowStock || 0,
      });

      setRecentOrders(data.recentOrders || []);
      setTopProducts(data.topProducts || []);
    } catch (error) {
      console.error('Error fetching dashboard data:', error);
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadge = (status) => {
    const badges = {
      PENDING: { class: 'badge-warning', text: 'En attente' },
      CONFIRMED: { class: 'badge-info', text: 'Confirmée' },
      DELIVERED: { class: 'badge-success', text: 'Livrée' },
      INVOICED: { class: 'badge-primary', text: 'Facturée' },
      CANCELED: { class: 'badge-danger', text: 'Annulée' }
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
          <p className="mt-4 text-gray-600">Chargement des données...</p>
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
          Voici un aperçu de votre activité commerciale
        </p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          title={t('dashboard.totalSales')}
          value={`${formatCurrency(stats.totalSales)} €`}
          icon={DollarSign}
          color="from-green-500 to-emerald-600"
        />
        <StatCard
          title={t('dashboard.totalOrders')}
          value={stats.totalOrders}
          icon={ShoppingCart}
          color="from-blue-500 to-cyan-600"
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
        />
      </div>

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Orders */}
        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          className="card"
        >
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-gray-900">
              {t('dashboard.recentOrders')}
            </h2>
            <button className="text-sm text-primary-600 hover:text-primary-700 font-medium">
              Voir tout
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
              <p className="text-center text-gray-500 py-8">Aucune commande récente</p>
            )}
          </div>
        </motion.div>

        {/* Top Products */}
        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          className="card"
        >
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-gray-900">
              {t('dashboard.topProducts')}
            </h2>
            <button className="text-sm text-primary-600 hover:text-primary-700 font-medium">
              Voir tout
            </button>
          </div>
          <div className="space-y-4">
            {topProducts.length > 0 ? (
              topProducts.map((product, index) => {
                const maxSales = topProducts[0]?.sales || 1;
                const percentage = (product.sales / maxSales) * 100;
                return (
                  <div key={product.id} className="flex items-center gap-4">
                    <div className="flex-1">
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-medium text-gray-900">{product.name}</span>
                        <span className="text-sm text-gray-600">{product.sales} en stock</span>
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
        </motion.div>
      </div>
    </div>
  );
};

export default Dashboard;
