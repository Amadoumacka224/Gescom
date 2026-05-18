import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  DollarSign,
  ShoppingCart,
  Package,
  TrendingUp,
  Calendar,
  RefreshCw,
  FileText,
  CreditCard,
  Truck,
  Plus,
  AlertCircle,
  ArrowRight,
  Clock
} from 'lucide-react';
import { motion } from 'framer-motion';
import { toast } from 'react-hot-toast';
import api from '../services/api';

const formatCurrency = (amount) =>
  new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    .format(Number(amount) || 0);

const formatTime = (iso) => {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
};

const formatDate = (iso) => {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('fr-FR');
};

const ORDER_STATUS = {
  PENDING: { label: 'En attente', class: 'bg-yellow-100 text-yellow-800' },
  CONFIRMED: { label: 'Confirmée', class: 'bg-blue-100 text-blue-800' },
  INVOICED: { label: 'Facturée', class: 'bg-purple-100 text-purple-800' },
  DELIVERED: { label: 'Livrée', class: 'bg-green-100 text-green-800' },
  CANCELED: { label: 'Annulée', class: 'bg-red-100 text-red-800' },
};

const StatCard = ({ title, value, subtitle, icon: Icon, gradient }) => (
  <motion.div
    initial={{ opacity: 0, y: 12 }}
    animate={{ opacity: 1, y: 0 }}
    className={`rounded-xl shadow-lg p-6 text-white bg-gradient-to-br ${gradient}`}
  >
    <div className="flex items-center justify-between">
      <div className="min-w-0">
        <p className="text-white/80 text-sm font-medium">{title}</p>
        <p className="text-3xl font-bold mt-2 truncate">{value}</p>
        {subtitle && <p className="text-white/80 text-xs mt-1">{subtitle}</p>}
      </div>
      <div className="w-14 h-14 bg-white/20 rounded-full flex items-center justify-center flex-shrink-0">
        <Icon className="w-7 h-7" />
      </div>
    </div>
  </motion.div>
);

const Caisse = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);
  const [data, setData] = useState({
    selectedDate: '',
    daySales: 0,
    dayOrdersCount: 0,
    dayCanceledCount: 0,
    dayItemsCount: 0,
    averageBasket: 0,
    pendingInvoicesCount: 0,
    pendingInvoicesAmount: 0,
    pendingDeliveriesCount: 0,
    dayOrders: [],
    pendingInvoices: [],
    pendingDeliveries: [],
  });

  const fetchDashboard = useCallback(async (date) => {
    try {
      setLoading(true);
      const response = await api.get('/dashboard/cashier', { params: { date } });
      setData(response.data);
    } catch (error) {
      console.error('Error fetching cashier dashboard:', error);
      toast.error('Erreur lors du chargement du tableau de bord');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboard(selectedDate);
  }, [selectedDate, fetchDashboard]);

  const isToday = selectedDate === new Date().toISOString().split('T')[0];

  return (
    <div className="min-h-screen bg-gradient-to-br from-green-50 via-white to-blue-50 p-6">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div className="flex items-center gap-3">
            <div className="p-3 bg-gradient-to-br from-green-500 to-blue-600 rounded-xl">
              <DollarSign className="w-8 h-8 text-white" />
            </div>
            <div>
              <h1 className="text-3xl font-bold text-gray-800">Ma Caisse</h1>
              <p className="text-gray-600 mt-1">
                {user?.firstName} {user?.lastName}
                {isToday && <span className="ml-2 text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">Aujourd'hui</span>}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3 flex-wrap">
            <div className="flex items-center gap-2 bg-white border border-gray-300 rounded-lg px-3 py-2">
              <Calendar className="w-4 h-4 text-gray-500" />
              <input
                type="date"
                value={selectedDate}
                onChange={(e) => setSelectedDate(e.target.value)}
                className="bg-transparent text-sm focus:outline-none"
              />
            </div>
            {!isToday && (
              <button
                onClick={() => setSelectedDate(new Date().toISOString().split('T')[0])}
                className="px-3 py-2 text-sm bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
              >
                Aujourd'hui
              </button>
            )}
            <button
              onClick={() => fetchDashboard(selectedDate)}
              className="flex items-center gap-2 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
              disabled={loading}
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
              Actualiser
            </button>
            <button
              onClick={() => navigate('/orders')}
              className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-green-600 to-emerald-600 text-white rounded-lg hover:from-green-700 hover:to-emerald-700 transition-all shadow"
            >
              <Plus className="w-4 h-4" />
              Nouvelle commande
            </button>
          </div>
        </div>

        {/* Stats — Ma journée */}
        <div>
          <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">Ma journée</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              title="Chiffre d'affaires"
              value={`${formatCurrency(data.daySales)} €`}
              subtitle={data.dayCanceledCount > 0 ? `${data.dayCanceledCount} annulée(s) exclue(s)` : 'Commandes honorées'}
              icon={DollarSign}
              gradient="from-green-500 to-emerald-600"
            />
            <StatCard
              title="Commandes"
              value={data.dayOrdersCount}
              subtitle="Hors annulations"
              icon={ShoppingCart}
              gradient="from-blue-500 to-cyan-600"
            />
            <StatCard
              title="Articles vendus"
              value={data.dayItemsCount}
              subtitle="Unités totales"
              icon={Package}
              gradient="from-purple-500 to-pink-600"
            />
            <StatCard
              title="Panier moyen"
              value={`${formatCurrency(data.averageBasket)} €`}
              subtitle="CA / commandes"
              icon={TrendingUp}
              gradient="from-orange-500 to-red-600"
            />
          </div>
        </div>

        {/* À faire — file d'attente du caissier (toutes dates) */}
        <div>
          <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">À faire</h2>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Factures à encaisser */}
            <motion.div
              initial={{ opacity: 0, x: -12 }}
              animate={{ opacity: 1, x: 0 }}
              className="bg-white rounded-xl shadow-lg overflow-hidden"
            >
              <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <CreditCard className="w-5 h-5 text-orange-600" />
                  <h3 className="font-bold text-gray-800">Factures à encaisser</h3>
                  {data.pendingInvoicesCount > 0 && (
                    <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-orange-100 text-orange-700">
                      {data.pendingInvoicesCount}
                    </span>
                  )}
                </div>
                <span className="text-sm font-bold text-orange-600">
                  {formatCurrency(data.pendingInvoicesAmount)} €
                </span>
              </div>
              <div className="divide-y divide-gray-100">
                {data.pendingInvoices.length === 0 ? (
                  <div className="px-6 py-8 text-center text-sm text-gray-500">
                    <CreditCard className="w-8 h-8 mx-auto mb-2 text-gray-300" />
                    Aucune facture en attente
                  </div>
                ) : (
                  data.pendingInvoices.map((inv) => {
                    const overdue = inv.dueDate && new Date(inv.dueDate) < new Date(new Date().toDateString());
                    return (
                      <div
                        key={inv.id}
                        onClick={() => navigate('/invoices', { state: { invoiceId: inv.id } })}
                        className="px-6 py-3 hover:bg-orange-50 cursor-pointer flex items-center justify-between gap-3"
                      >
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2">
                            <span className="font-medium text-gray-900 truncate">{inv.invoiceNumber}</span>
                            {overdue && (
                              <span className="inline-flex items-center gap-0.5 text-xs px-1.5 py-0.5 rounded bg-red-100 text-red-700">
                                <AlertCircle className="w-3 h-3" /> En retard
                              </span>
                            )}
                          </div>
                          <p className="text-xs text-gray-500 truncate">
                            {inv.clientName} · échéance {formatDate(inv.dueDate)}
                          </p>
                        </div>
                        <div className="text-right">
                          <p className="font-semibold text-gray-900">{formatCurrency(inv.remainingAmount)} €</p>
                          <p className="text-xs text-gray-500">/ {formatCurrency(inv.totalAmount)} €</p>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
              {data.pendingInvoicesCount > data.pendingInvoices.length && (
                <button
                  onClick={() => navigate('/invoices')}
                  className="w-full px-6 py-3 text-sm text-orange-600 hover:text-orange-700 hover:bg-orange-50 transition-colors flex items-center justify-center gap-1 border-t border-gray-100"
                >
                  Voir toutes les factures ({data.pendingInvoicesCount}) <ArrowRight className="w-3 h-3" />
                </button>
              )}
            </motion.div>

            {/* Livraisons à préparer */}
            <motion.div
              initial={{ opacity: 0, x: 12 }}
              animate={{ opacity: 1, x: 0 }}
              className="bg-white rounded-xl shadow-lg overflow-hidden"
            >
              <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Truck className="w-5 h-5 text-blue-600" />
                  <h3 className="font-bold text-gray-800">Livraisons à préparer</h3>
                  {data.pendingDeliveriesCount > 0 && (
                    <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-blue-100 text-blue-700">
                      {data.pendingDeliveriesCount}
                    </span>
                  )}
                </div>
              </div>
              <div className="divide-y divide-gray-100">
                {data.pendingDeliveries.length === 0 ? (
                  <div className="px-6 py-8 text-center text-sm text-gray-500">
                    <Truck className="w-8 h-8 mx-auto mb-2 text-gray-300" />
                    Aucune livraison en attente
                  </div>
                ) : (
                  data.pendingDeliveries.map((d) => (
                    <div
                      key={d.id}
                      onClick={() => navigate('/deliveries')}
                      className="px-6 py-3 hover:bg-blue-50 cursor-pointer flex items-center justify-between gap-3"
                    >
                      <div className="min-w-0 flex-1">
                        <span className="font-medium text-gray-900 truncate block">{d.deliveryNumber}</span>
                        <p className="text-xs text-gray-500 truncate">
                          {d.contactName || d.clientName} · prévu {formatDate(d.scheduledDate)}
                        </p>
                      </div>
                      <ArrowRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                    </div>
                  ))
                )}
              </div>
              {data.pendingDeliveriesCount > data.pendingDeliveries.length && (
                <button
                  onClick={() => navigate('/deliveries')}
                  className="w-full px-6 py-3 text-sm text-blue-600 hover:text-blue-700 hover:bg-blue-50 transition-colors flex items-center justify-center gap-1 border-t border-gray-100"
                >
                  Voir toutes les livraisons ({data.pendingDeliveriesCount}) <ArrowRight className="w-3 h-3" />
                </button>
              )}
            </motion.div>
          </div>
        </div>

        {/* Commandes du jour */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-white rounded-xl shadow-lg overflow-hidden"
        >
          <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
            <h2 className="text-xl font-bold text-gray-800">
              Mes commandes du {formatDate(data.selectedDate)}
            </h2>
            <span className="text-sm text-gray-500">
              {data.dayOrders.length} commande(s)
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Heure</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">N° Commande</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Client</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Statut</th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">Montant</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {loading ? (
                  <tr>
                    <td colSpan="5" className="px-6 py-12 text-center text-gray-500">
                      <RefreshCw className="w-8 h-8 animate-spin mx-auto mb-2" />
                      Chargement...
                    </td>
                  </tr>
                ) : data.dayOrders.length === 0 ? (
                  <tr>
                    <td colSpan="5" className="px-6 py-12 text-center">
                      <FileText className="w-12 h-12 text-gray-300 mx-auto mb-3" />
                      <p className="text-gray-500 font-medium">Aucune commande pour cette date</p>
                      <button
                        onClick={() => navigate('/orders')}
                        className="mt-3 text-sm text-green-600 hover:text-green-700 font-medium inline-flex items-center gap-1"
                      >
                        Créer une commande <ArrowRight className="w-3 h-3" />
                      </button>
                    </td>
                  </tr>
                ) : (
                  data.dayOrders.map((order) => {
                    const status = ORDER_STATUS[order.status] || ORDER_STATUS.PENDING;
                    const isCanceled = order.status === 'CANCELED';
                    return (
                      <tr
                        key={order.id}
                        className={`hover:bg-gray-50 cursor-pointer ${isCanceled ? 'opacity-60' : ''}`}
                        onClick={() => navigate('/orders')}
                      >
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          <span className="inline-flex items-center gap-1">
                            <Clock className="w-3 h-3 text-gray-400" />
                            {formatTime(order.createdAt)}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-blue-600">
                          {order.orderNumber}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {order.clientName}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className={`px-3 py-1 rounded-full text-xs font-medium ${status.class}`}>
                            {status.label}
                          </span>
                        </td>
                        <td className={`px-6 py-4 whitespace-nowrap text-sm font-semibold text-right ${isCanceled ? 'line-through text-gray-500' : 'text-gray-900'}`}>
                          {formatCurrency(order.finalAmount)} €
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </motion.div>
      </div>
    </div>
  );
};

export default Caisse;
