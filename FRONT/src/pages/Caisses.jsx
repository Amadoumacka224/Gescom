import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  DollarSign,
  ShoppingCart,
  Package,
  TrendingUp,
  Calendar,
  Users,
  Eye,
  RefreshCw,
  ChevronDown,
  ChevronUp,
  Award,
  Target,
  BarChart3
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { toast } from 'react-hot-toast';
import api from '../services/api';

const Caisses = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [users, setUsers] = useState([]);
  const [orders, setOrders] = useState([]);
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);
  const [expandedCashier, setExpandedCashier] = useState(null);
  const [cashiersStats, setCashiersStats] = useState([]);
  const [globalStats, setGlobalStats] = useState({
    totalSales: 0,
    totalOrders: 0,
    totalItems: 0,
    activeCashiers: 0
  });

  useEffect(() => {
    fetchUsers();
    fetchOrders();
  }, []);

  useEffect(() => {
    calculateAllStats();
  }, [selectedDate, orders, users]);

  const fetchUsers = async () => {
    try {
      const response = await api.get('/users');
      const cashiers = response.data.filter(u => u.role === 'CAISSIER');
      setUsers(cashiers);
    } catch (error) {
      console.error('Error fetching users:', error);
      toast.error('Erreur lors du chargement des caissiers');
    }
  };

  const fetchOrders = async () => {
    try {
      setLoading(true);
      const response = await api.get('/orders');
      setOrders(response.data);
    } catch (error) {
      console.error('Error fetching orders:', error);
      toast.error('Erreur lors du chargement des commandes');
    } finally {
      setLoading(false);
    }
  };

  const calculateAllStats = () => {
    if (!selectedDate || users.length === 0) return;

    const stats = users.map(cashier => {
      // Filter orders for this cashier and date
      const cashierOrders = orders.filter(order => {
        const createdById = order.createdBy?.id || order.user?.id;
        const orderDate = new Date(order.createdAt || order.orderDate);
        const selectedDateTime = new Date(selectedDate);

        return (
          createdById === cashier.id &&
          orderDate.getFullYear() === selectedDateTime.getFullYear() &&
          orderDate.getMonth() === selectedDateTime.getMonth() &&
          orderDate.getDate() === selectedDateTime.getDate()
        );
      });

      const totalSales = cashierOrders.reduce((sum, order) =>
        sum + (order.finalAmount || order.totalAmount || 0), 0
      );

      const totalOrders = cashierOrders.length;

      const totalItems = cashierOrders.reduce((sum, order) => {
        const items = order.items || order.orderItems || [];
        return sum + items.reduce((itemSum, item) => itemSum + (item.quantity || 0), 0);
      }, 0);

      const averageOrder = totalOrders > 0 ? totalSales / totalOrders : 0;

      return {
        cashier,
        totalSales,
        totalOrders,
        totalItems,
        averageOrder,
        orders: cashierOrders
      };
    });

    // Sort by total sales descending
    stats.sort((a, b) => b.totalSales - a.totalSales);
    setCashiersStats(stats);

    // Calculate global stats
    const global = {
      totalSales: stats.reduce((sum, s) => sum + s.totalSales, 0),
      totalOrders: stats.reduce((sum, s) => sum + s.totalOrders, 0),
      totalItems: stats.reduce((sum, s) => sum + s.totalItems, 0),
      activeCashiers: stats.filter(s => s.totalOrders > 0).length
    };
    setGlobalStats(global);
  };

  const handleRefresh = () => {
    fetchOrders();
    toast.success('Données actualisées');
  };

  const handleToday = () => {
    setSelectedDate(new Date().toISOString().split('T')[0]);
  };

  const toggleCashier = (cashierId) => {
    setExpandedCashier(expandedCashier === cashierId ? null : cashierId);
  };

  const getStatusBadgeColor = (status) => {
    const colors = {
      'PENDING': 'bg-yellow-100 text-yellow-800',
      'CONFIRMED': 'bg-blue-100 text-blue-800',
      'DELIVERED': 'bg-green-100 text-green-800',
      'INVOICED': 'bg-purple-100 text-purple-800',
      'CANCELED': 'bg-red-100 text-red-800'
    };
    return colors[status] || 'bg-gray-100 text-gray-800';
  };

  const getStatusLabel = (status) => {
    const labels = {
      'PENDING': 'En attente',
      'CONFIRMED': 'Confirmée',
      'DELIVERED': 'Livrée',
      'INVOICED': 'Facturée',
      'CANCELED': 'Annulée'
    };
    return labels[status] || status;
  };

  const getRankBadge = (index) => {
    if (index === 0) return { icon: Award, color: 'text-yellow-500', bg: 'bg-yellow-50' };
    if (index === 1) return { icon: Award, color: 'text-gray-400', bg: 'bg-gray-50' };
    if (index === 2) return { icon: Award, color: 'text-orange-500', bg: 'bg-orange-50' };
    return { icon: Target, color: 'text-blue-500', bg: 'bg-blue-50' };
  };

  const handleOrderClick = (orderId) => {
    navigate('/orders');
    // Store the selected order ID in localStorage to highlight it
    localStorage.setItem('selectedOrderId', orderId);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-50 via-white to-blue-50 p-6">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="max-w-7xl mx-auto"
      >
        {/* Header */}
        <div className="mb-8">
          <div className="flex items-center justify-between flex-wrap gap-4">
            <div className="flex items-center gap-3">
              <div className="p-3 bg-gradient-to-br from-purple-500 to-blue-600 rounded-xl">
                <BarChart3 className="w-8 h-8 text-white" />
              </div>
              <div>
                <h1 className="text-3xl font-bold text-gray-800">Supervision des Caisses</h1>
                <p className="text-gray-600 mt-1">Vue d'ensemble des performances des caissiers</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <button
                onClick={handleToday}
                className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Calendar className="w-5 h-5" />
                Aujourd'hui
              </button>
              <button
                onClick={handleRefresh}
                className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
              >
                <RefreshCw className={`w-5 h-5 ${loading ? 'animate-spin' : ''}`} />
                Actualiser
              </button>
            </div>
          </div>
        </div>

        {/* Date Filter */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="bg-white rounded-xl shadow-lg p-6 mb-6"
        >
          <div className="flex items-center gap-4 flex-wrap">
            <div className="flex items-center gap-2">
              <Calendar className="w-5 h-5 text-gray-500" />
              <span className="font-medium text-gray-700">Date:</span>
            </div>
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
            />
          </div>
        </motion.div>

        {/* Global Statistics Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="bg-gradient-to-br from-green-500 to-green-600 rounded-xl shadow-lg p-6 text-white"
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-green-100 text-sm font-medium">Ventes Totales</p>
                <p className="text-3xl font-bold mt-2">{globalStats.totalSales.toFixed(2)} €</p>
              </div>
              <div className="w-14 h-14 bg-white/20 rounded-full flex items-center justify-center">
                <DollarSign className="w-8 h-8" />
              </div>
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3 }}
            className="bg-gradient-to-br from-blue-500 to-blue-600 rounded-xl shadow-lg p-6 text-white"
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-blue-100 text-sm font-medium">Total Commandes</p>
                <p className="text-3xl font-bold mt-2">{globalStats.totalOrders}</p>
              </div>
              <div className="w-14 h-14 bg-white/20 rounded-full flex items-center justify-center">
                <ShoppingCart className="w-8 h-8" />
              </div>
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.4 }}
            className="bg-gradient-to-br from-purple-500 to-purple-600 rounded-xl shadow-lg p-6 text-white"
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-purple-100 text-sm font-medium">Articles Vendus</p>
                <p className="text-3xl font-bold mt-2">{globalStats.totalItems}</p>
              </div>
              <div className="w-14 h-14 bg-white/20 rounded-full flex items-center justify-center">
                <Package className="w-8 h-8" />
              </div>
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.5 }}
            className="bg-gradient-to-br from-orange-500 to-orange-600 rounded-xl shadow-lg p-6 text-white"
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="text-orange-100 text-sm font-medium">Caissiers Actifs</p>
                <p className="text-3xl font-bold mt-2">{globalStats.activeCashiers}</p>
              </div>
              <div className="w-14 h-14 bg-white/20 rounded-full flex items-center justify-center">
                <Users className="w-8 h-8" />
              </div>
            </div>
          </motion.div>
        </div>

        {/* Cashiers List */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.6 }}
          className="space-y-4"
        >
          <h2 className="text-2xl font-bold text-gray-800 mb-4">Détail par Caissier</h2>

          {loading ? (
            <div className="bg-white rounded-xl shadow-lg p-12 text-center">
              <RefreshCw className="w-12 h-12 animate-spin text-purple-600 mx-auto mb-4" />
              <p className="text-gray-600">Chargement des données...</p>
            </div>
          ) : cashiersStats.length === 0 ? (
            <div className="bg-white rounded-xl shadow-lg p-12 text-center">
              <Users className="w-12 h-12 text-gray-300 mx-auto mb-4" />
              <p className="text-gray-600 font-medium">Aucun caissier trouvé</p>
            </div>
          ) : (
            cashiersStats.map((stat, index) => {
              const rank = getRankBadge(index);
              const RankIcon = rank.icon;
              const isExpanded = expandedCashier === stat.cashier.id;

              return (
                <motion.div
                  key={stat.cashier.id}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: index * 0.05 }}
                  className="bg-white rounded-xl shadow-lg overflow-hidden"
                >
                  {/* Cashier Header */}
                  <div
                    onClick={() => toggleCashier(stat.cashier.id)}
                    className="p-6 cursor-pointer hover:bg-gray-50 transition-colors"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-4 flex-1">
                        {/* Rank Badge */}
                        <div className={`w-12 h-12 ${rank.bg} rounded-full flex items-center justify-center`}>
                          <RankIcon className={`w-6 h-6 ${rank.color}`} />
                        </div>

                        {/* Cashier Info */}
                        <div className="flex-1">
                          <div className="flex items-center gap-3">
                            <h3 className="text-lg font-bold text-gray-900">
                              {stat.cashier.firstName} {stat.cashier.lastName}
                            </h3>
                            <span className="text-sm text-gray-500">#{index + 1}</span>
                          </div>
                          <p className="text-sm text-gray-600">{stat.cashier.email}</p>
                        </div>

                        {/* Stats Grid */}
                        <div className="hidden md:grid grid-cols-4 gap-6 flex-1">
                          <div className="text-center">
                            <p className="text-sm text-gray-500">Ventes</p>
                            <p className="text-lg font-bold text-green-600">
                              {stat.totalSales.toFixed(2)} €
                            </p>
                          </div>
                          <div className="text-center">
                            <p className="text-sm text-gray-500">Commandes</p>
                            <p className="text-lg font-bold text-blue-600">{stat.totalOrders}</p>
                          </div>
                          <div className="text-center">
                            <p className="text-sm text-gray-500">Articles</p>
                            <p className="text-lg font-bold text-purple-600">{stat.totalItems}</p>
                          </div>
                          <div className="text-center">
                            <p className="text-sm text-gray-500">Panier Moyen</p>
                            <p className="text-lg font-bold text-orange-600">
                              {stat.averageOrder.toFixed(2)} €
                            </p>
                          </div>
                        </div>

                        {/* Expand Icon */}
                        <div className="ml-4">
                          {isExpanded ? (
                            <ChevronUp className="w-6 h-6 text-gray-400" />
                          ) : (
                            <ChevronDown className="w-6 h-6 text-gray-400" />
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Mobile Stats */}
                    <div className="md:hidden mt-4 grid grid-cols-2 gap-4">
                      <div className="text-center p-3 bg-green-50 rounded-lg">
                        <p className="text-xs text-gray-600">Ventes</p>
                        <p className="text-sm font-bold text-green-600">
                          {stat.totalSales.toFixed(2)} €
                        </p>
                      </div>
                      <div className="text-center p-3 bg-blue-50 rounded-lg">
                        <p className="text-xs text-gray-600">Commandes</p>
                        <p className="text-sm font-bold text-blue-600">{stat.totalOrders}</p>
                      </div>
                      <div className="text-center p-3 bg-purple-50 rounded-lg">
                        <p className="text-xs text-gray-600">Articles</p>
                        <p className="text-sm font-bold text-purple-600">{stat.totalItems}</p>
                      </div>
                      <div className="text-center p-3 bg-orange-50 rounded-lg">
                        <p className="text-xs text-gray-600">Panier Moyen</p>
                        <p className="text-sm font-bold text-orange-600">
                          {stat.averageOrder.toFixed(2)} €
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Expanded Orders Table */}
                  <AnimatePresence>
                    {isExpanded && (
                      <motion.div
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: 'auto', opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={{ duration: 0.3 }}
                        className="border-t border-gray-200"
                      >
                        <div className="p-6 bg-gray-50">
                          {stat.orders.length === 0 ? (
                            <p className="text-center text-gray-500 py-8">
                              Aucune commande pour cette date
                            </p>
                          ) : (
                            <div className="overflow-x-auto">
                              <table className="min-w-full divide-y divide-gray-200">
                                <thead>
                                  <tr>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                                      Heure
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                                      N° Commande
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                                      Client
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                                      Articles
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                                      Statut
                                    </th>
                                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                                      Montant
                                    </th>
                                  </tr>
                                </thead>
                                <tbody className="bg-white divide-y divide-gray-200">
                                  {stat.orders.map((order) => (
                                    <tr
                                      key={order.id}
                                      onClick={() => handleOrderClick(order.id)}
                                      className="hover:bg-blue-50 cursor-pointer transition-colors"
                                    >
                                      <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                                        {order.createdAt
                                          ? new Date(order.createdAt).toLocaleTimeString('fr-FR', {
                                              hour: '2-digit',
                                              minute: '2-digit'
                                            })
                                          : new Date(order.orderDate || Date.now()).toLocaleTimeString(
                                              'fr-FR',
                                              { hour: '2-digit', minute: '2-digit' }
                                            )}
                                      </td>
                                      <td className="px-4 py-3 whitespace-nowrap text-sm font-medium text-blue-600">
                                        {order.orderNumber ||
                                          `CMD${order.id.toString().padStart(5, '0')}`}
                                      </td>
                                      <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                                        {order.client?.name || order.client?.firstName
                                          ? `${order.client.firstName || ''} ${
                                              order.client.lastName || ''
                                            }`.trim()
                                          : 'Client anonyme'}
                                      </td>
                                      <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                                        {order.items?.reduce((sum, item) => sum + (item.quantity || 0), 0) ||
                                         order.orderItems?.reduce(
                                           (sum, item) => sum + (item.quantity || 0),
                                           0
                                         ) ||
                                         0}
                                      </td>
                                      <td className="px-4 py-3 whitespace-nowrap">
                                        <span
                                          className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusBadgeColor(
                                            order.status
                                          )}`}
                                        >
                                          {getStatusLabel(order.status)}
                                        </span>
                                      </td>
                                      <td className="px-4 py-3 whitespace-nowrap text-sm font-semibold text-gray-900">
                                        {(order.finalAmount || order.totalAmount || 0).toFixed(2)} €
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          )}
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </motion.div>
              );
            })
          )}
        </motion.div>
      </motion.div>
    </div>
  );
};

export default Caisses;
