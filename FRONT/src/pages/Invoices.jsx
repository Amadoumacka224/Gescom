import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { Plus, FileText, DollarSign, Download, Eye, X, CreditCard, User, MapPin, Phone, Mail, Calendar, Package } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import api from '../services/api';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Pagination from '../components/Pagination';
import { generateInvoicePDF } from '../utils/pdfGenerator';

const Invoices = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const [invoices, setInvoices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [orders, setOrders] = useState([]);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [createForm, setCreateForm] = useState({
    orderId: '',
    invoiceDate: new Date().toISOString().split('T')[0],
    dueDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    paymentMethod: 'CASH',
    taxRate: '20.00',
    notes: ''
  });
  const [paymentData, setPaymentData] = useState({
    amount: '',
    paymentMethod: 'CASH',
    paymentDate: new Date().toISOString().split('T')[0]
  });

  useEffect(() => {
    fetchInvoices();
  }, []);

  useEffect(() => {
    // If navigated from delivery with invoice ID
    if (location.state?.invoiceId) {
      const invoice = invoices.find(inv => inv.id === location.state.invoiceId);
      if (invoice) {
        handleViewDetails(invoice);
        // Clear the state
        navigate(location.pathname, { replace: true, state: {} });
      }
    }
  }, [location.state, invoices]);

  const fetchInvoices = async () => {
    try {
      setLoading(true);
      const response = await api.get('/invoices');
      setInvoices(response.data);
    } catch (error) {
      console.error('Error fetching invoices:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchOrders = async () => {
    try {
      const response = await api.get('/orders');
      // Only show DELIVERED orders that don't have an invoice yet
      const availableOrders = response.data.filter(order =>
        order.status === 'DELIVERED' && !invoices.some(inv => inv.order?.id === order.id)
      );
      setOrders(availableOrders);
    } catch (error) {
      console.error('Error fetching orders:', error);
    }
  };

  const handleOpenCreateModal = () => {
    fetchOrders();
    setShowCreateModal(true);
  };

  const handleOrderSelect = async (orderId) => {
    if (!orderId) {
      setSelectedOrder(null);
      setCreateForm({ ...createForm, orderId: '' });
      return;
    }

    try {
      // Fetch full order details with client info
      const response = await api.get(`/orders/${orderId}`);
      setSelectedOrder(response.data);
      setCreateForm({ ...createForm, orderId });
    } catch (error) {
      console.error('Error fetching order details:', error);
      const order = orders.find(o => o.id === parseInt(orderId));
      setSelectedOrder(order);
      setCreateForm({ ...createForm, orderId });
    }
  };

  const handleCreateInvoice = () => {
    if (!createForm.orderId) {
      alert('Veuillez sélectionner une commande');
      return;
    }

    setShowConfirmModal(true);
  };

  const confirmCreateInvoice = async () => {
    try {
      const invoiceData = {
        order: { id: parseInt(createForm.orderId) },
        invoiceDate: createForm.invoiceDate,
        dueDate: createForm.dueDate,
        paymentMethod: createForm.paymentMethod,
        taxRate: parseFloat(createForm.taxRate),
        notes: createForm.notes
      };

      await api.post('/invoices', invoiceData);
      setShowCreateModal(false);
      setCreateForm({
        orderId: '',
        invoiceDate: new Date().toISOString().split('T')[0],
        dueDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        paymentMethod: 'CASH',
        taxRate: '20.00',
        notes: ''
      });
      setSelectedOrder(null);
      fetchInvoices();
      alert('✅ Facture créée avec succès!');
    } catch (error) {
      console.error('Error creating invoice:', error);
      const errorMessage = error.response?.data || error.message || 'Erreur lors de la création de la facture';
      alert('❌ Erreur: ' + errorMessage);
    }
  };

  const handleViewDetails = async (invoice) => {
    try {
      const response = await api.get(`/invoices/${invoice.id}`);
      setSelectedInvoice(response.data);
      setShowDetailsModal(true);
    } catch (error) {
      console.error('Error fetching invoice details:', error);
      alert('Erreur lors du chargement des détails de la facture');
    }
  };

  const handleOpenPaymentModal = (invoice) => {
    setSelectedInvoice(invoice);
    setPaymentData({
      amount: (invoice.totalAmount - invoice.paidAmount).toFixed(2),
      paymentMethod: invoice.paymentMethod || 'CASH',
      paymentDate: new Date().toISOString().split('T')[0]
    });
    setShowPaymentModal(true);
  };

  const handlePayment = async () => {
    try {
      await api.patch(`/invoices/${selectedInvoice.id}/payment`, paymentData);
      setShowPaymentModal(false);
      fetchInvoices();
      alert('Paiement enregistré avec succès!');
    } catch (error) {
      console.error('Error processing payment:', error);
      alert('Erreur lors de l\'enregistrement du paiement');
    }
  };

  const handleDownloadPDF = async (invoice) => {
    try {
      // Fetch full invoice details if needed
      const response = await api.get(`/invoices/${invoice.id}`);
      const fullInvoice = response.data;

      // Generate and download PDF
      generateInvoicePDF(fullInvoice);
    } catch (error) {
      console.error('Error generating PDF:', error);
      alert('Erreur lors de la génération du PDF');
    }
  };

  const getStatusInfo = (status) => {
    const statuses = {
      PAID: { class: 'badge-success', text: 'Payée' },
      UNPAID: { class: 'badge-danger', text: 'Non payée' },
      PARTIALLY_PAID: { class: 'badge-warning', text: 'Partiellement payée' },
      CANCELED: { class: 'badge-secondary', text: 'Annulée' },
    };
    return statuses[status] || statuses.UNPAID;
  };

  const getPaymentMethodText = (method) => {
    const methods = {
      CASH: 'Espèces',
      CREDIT_CARD: 'Carte de crédit',
      DEBIT_CARD: 'Carte de débit',
      BANK_TRANSFER: 'Virement bancaire',
      CHECK: 'Chèque',
      MOBILE_PAYMENT: 'Paiement mobile'
    };
    return methods[method] || method;
  };

  const totalRevenue = invoices.reduce((sum, inv) => sum + (inv.paidAmount || 0), 0);
  const pendingAmount = invoices.reduce((sum, inv) => sum + ((inv.totalAmount || 0) - (inv.paidAmount || 0)), 0);

  // Pagination
  const totalPages = Math.ceil(invoices.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const paginatedInvoices = invoices.slice(startIndex, endIndex);

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const handleItemsPerPageChange = (newItemsPerPage) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">{t('invoices.title')}</h1>
          <p className="text-gray-600 mt-1">Gérez vos factures et paiements</p>
        </div>
        <motion.button
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.95 }}
          onClick={handleOpenCreateModal}
          className="btn-primary flex items-center gap-2"
        >
          <Plus className="w-5 h-5" />
          {t('invoices.addInvoice')}
        </motion.button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="card bg-gradient-to-br from-green-50 to-emerald-50 border-green-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-green-600 font-medium">Revenus encaissés</p>
              <p className="text-3xl font-bold text-green-700">{totalRevenue.toFixed(2)}€</p>
            </div>
            <DollarSign className="w-12 h-12 text-green-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-orange-50 to-red-50 border-orange-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-orange-600 font-medium">En attente</p>
              <p className="text-3xl font-bold text-orange-700">{pendingAmount.toFixed(2)}€</p>
            </div>
            <FileText className="w-12 h-12 text-orange-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-blue-50 to-cyan-50 border-blue-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-blue-600 font-medium">Total factures</p>
              <p className="text-3xl font-bold text-blue-700">{invoices.length}</p>
            </div>
            <FileText className="w-12 h-12 text-blue-600 opacity-50" />
          </div>
        </div>
      </div>

      {/* Invoices Table */}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">N° Facture</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Client</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Échéance</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Montant</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Payé</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Statut</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {paginatedInvoices.map((invoice) => (
                <motion.tr
                  key={invoice.id}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  className="hover:bg-gray-50"
                >
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center gap-2">
                      <FileText className="w-4 h-4 text-gray-400" />
                      <span className="font-medium text-gray-900">{invoice.invoiceNumber}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">{invoice.client}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">{invoice.invoiceDate}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">{invoice.dueDate}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-gray-900">
                    {invoice.totalAmount.toFixed(2)}€
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-green-600">
                    {invoice.paidAmount.toFixed(2)}€
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`badge ${getStatusInfo(invoice.status).class}`}>
                      {getStatusInfo(invoice.status).text}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleViewDetails(invoice)}
                        className="text-primary-600 hover:text-primary-900"
                        title="Voir"
                      >
                        <Eye className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDownloadPDF(invoice)}
                        className="text-gray-600 hover:text-gray-900"
                        title="Télécharger PDF"
                      >
                        <Download className="w-4 h-4" />
                      </button>
                      {invoice.status !== 'PAID' && invoice.status !== 'CANCELED' && (
                        <button
                          onClick={() => handleOpenPaymentModal(invoice)}
                          className="btn-primary px-3 py-1 text-xs"
                        >
                          Paiement
                        </button>
                      )}
                    </div>
                  </td>
                </motion.tr>
              ))}
            </tbody>
          </table>

          {/* Pagination */}
          {invoices.length > 0 && (
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalItems={invoices.length}
              itemsPerPage={itemsPerPage}
              onPageChange={handlePageChange}
              onItemsPerPageChange={handleItemsPerPageChange}
            />
          )}
        </div>
      </div>

      {/* Invoice Details Modal */}
      <AnimatePresence>
        {showDetailsModal && selectedInvoice && (
          <Modal
            isOpen={showDetailsModal}
            onClose={() => setShowDetailsModal(false)}
            title="Détails de la facture"
          >
            <div className="space-y-6">
              {/* Invoice Info */}
              <div className="grid grid-cols-2 gap-4 p-4 bg-gray-50 rounded-lg">
                <div>
                  <p className="text-sm text-gray-600">Numéro de facture</p>
                  <p className="font-semibold text-gray-900">{selectedInvoice.invoiceNumber}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Statut</p>
                  <span className={`badge ${getStatusInfo(selectedInvoice.status).class}`}>
                    {getStatusInfo(selectedInvoice.status).text}
                  </span>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Date de facture</p>
                  <p className="font-semibold text-gray-900 flex items-center gap-1">
                    <Calendar className="w-4 h-4" />
                    {selectedInvoice.invoiceDate}
                  </p>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Date d'échéance</p>
                  <p className="font-semibold text-gray-900 flex items-center gap-1">
                    <Calendar className="w-4 h-4" />
                    {selectedInvoice.dueDate}
                  </p>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Méthode de paiement</p>
                  <p className="font-semibold text-gray-900 flex items-center gap-1">
                    <CreditCard className="w-4 h-4" />
                    {getPaymentMethodText(selectedInvoice.paymentMethod)}
                  </p>
                </div>
                {selectedInvoice.paymentDate && (
                  <div>
                    <p className="text-sm text-gray-600">Date de paiement</p>
                    <p className="font-semibold text-gray-900">{selectedInvoice.paymentDate}</p>
                  </div>
                )}
              </div>

              {/* Client Info */}
              {selectedInvoice.order?.client && (
                <div>
                  <h3 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                    <User className="w-5 h-5" />
                    Informations client
                  </h3>
                  <div className="grid grid-cols-2 gap-4 p-4 bg-blue-50 rounded-lg">
                    <div>
                      <p className="text-sm text-gray-600">Nom</p>
                      <p className="font-semibold text-gray-900">{selectedInvoice.order.client.name}</p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Email</p>
                      <p className="font-semibold text-gray-900 flex items-center gap-1">
                        <Mail className="w-4 h-4" />
                        {selectedInvoice.order.client.email}
                      </p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Téléphone</p>
                      <p className="font-semibold text-gray-900 flex items-center gap-1">
                        <Phone className="w-4 h-4" />
                        {selectedInvoice.order.client.phone}
                      </p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Adresse</p>
                      <p className="font-semibold text-gray-900 flex items-center gap-1">
                        <MapPin className="w-4 h-4" />
                        {selectedInvoice.order.client.address}
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {/* Order Items */}
              {selectedInvoice.order?.orderItems && (
                <div>
                  <h3 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                    <Package className="w-5 h-5" />
                    Articles
                  </h3>
                  <div className="overflow-x-auto">
                    <table className="w-full">
                      <thead className="bg-gray-50">
                        <tr>
                          <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Produit</th>
                          <th className="px-4 py-2 text-right text-xs font-medium text-gray-500">Prix unitaire</th>
                          <th className="px-4 py-2 text-right text-xs font-medium text-gray-500">Quantité</th>
                          <th className="px-4 py-2 text-right text-xs font-medium text-gray-500">Total</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-200">
                        {selectedInvoice.order.orderItems.map((item, index) => (
                          <tr key={index}>
                            <td className="px-4 py-3 text-sm text-gray-900">{item.product?.name || 'Produit'}</td>
                            <td className="px-4 py-3 text-sm text-right text-gray-900">{item.unitPrice?.toFixed(2)}€</td>
                            <td className="px-4 py-3 text-sm text-right text-gray-900">{item.quantity}</td>
                            <td className="px-4 py-3 text-sm text-right font-semibold text-gray-900">
                              {(item.unitPrice * item.quantity).toFixed(2)}€
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* Financial Summary */}
              <div className="border-t pt-4">
                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-600">Sous-total</span>
                    <span className="font-semibold">{selectedInvoice.subtotal?.toFixed(2)}€</span>
                  </div>
                  {selectedInvoice.discount > 0 && (
                    <div className="flex justify-between text-sm">
                      <span className="text-gray-600">Remise</span>
                      <span className="font-semibold text-red-600">-{selectedInvoice.discount?.toFixed(2)}€</span>
                    </div>
                  )}
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-600">TVA ({selectedInvoice.taxRate}%)</span>
                    <span className="font-semibold">{selectedInvoice.taxAmount?.toFixed(2)}€</span>
                  </div>
                  <div className="flex justify-between text-lg font-bold border-t pt-2">
                    <span>Total</span>
                    <span className="text-primary-600">{selectedInvoice.totalAmount?.toFixed(2)}€</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-600">Montant payé</span>
                    <span className="font-semibold text-green-600">{selectedInvoice.paidAmount?.toFixed(2)}€</span>
                  </div>
                  <div className="flex justify-between text-lg font-bold">
                    <span>Reste à payer</span>
                    <span className="text-orange-600">{selectedInvoice.remainingAmount?.toFixed(2)}€</span>
                  </div>
                </div>
              </div>

              {/* Notes */}
              {selectedInvoice.notes && (
                <div>
                  <h3 className="font-semibold text-gray-900 mb-2">Notes</h3>
                  <p className="text-sm text-gray-600 bg-gray-50 p-3 rounded-lg">{selectedInvoice.notes}</p>
                </div>
              )}

              {/* Download PDF Button */}
              <div className="flex justify-end pt-4 border-t">
                <button
                  onClick={() => handleDownloadPDF(selectedInvoice)}
                  className="btn-primary flex items-center gap-2"
                >
                  <Download className="w-4 h-4" />
                  Télécharger la facture (PDF)
                </button>
              </div>
            </div>
          </Modal>
        )}
      </AnimatePresence>

      {/* Create Invoice Modal */}
      <AnimatePresence>
        {showCreateModal && (
          <Modal
            isOpen={showCreateModal}
            onClose={() => {
              setShowCreateModal(false);
              setSelectedOrder(null);
              setCreateForm({
                orderId: '',
                invoiceDate: new Date().toISOString().split('T')[0],
                dueDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
                paymentMethod: 'CASH',
                taxRate: '20.00',
                notes: ''
              });
            }}
            title="Créer une nouvelle facture"
            size="xl"
          >
            <div className="space-y-4">
              {/* Order Selection */}
              <div className="bg-gradient-to-r from-blue-600 to-indigo-600 p-5 rounded-lg shadow-lg">
                <label className="flex items-center gap-2 text-white font-semibold mb-3 text-base">
                  <Package className="w-5 h-5" />
                  Sélectionner la commande à facturer
                </label>
                <select
                  value={createForm.orderId}
                  onChange={(e) => handleOrderSelect(e.target.value)}
                  className="w-full px-4 py-3 text-base border-2 border-blue-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all bg-white shadow-sm"
                  required
                >
                  <option value="">-- Sélectionnez une commande --</option>
                  {orders.map((order) => (
                    <option key={order.id} value={order.id}>
                      #{order.orderNumber} - {order.client?.name} - {order.totalAmount?.toFixed(2)}€
                    </option>
                  ))}
                </select>
              </div>

              {/* Order & Client Details */}
              {selectedOrder && (
                <>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {/* Client Info */}
                    <div className="bg-gradient-to-br from-green-50 to-emerald-50 p-5 rounded-lg border-2 border-green-300 shadow-sm">
                      <h3 className="font-bold text-green-800 mb-3 flex items-center gap-2 text-base">
                        <User className="w-5 h-5" />
                        Client
                      </h3>
                      <div className="space-y-2 text-sm">
                        <div className="flex justify-between">
                          <span className="text-gray-600">Nom:</span>
                          <span className="font-semibold text-gray-900">{selectedOrder.client?.name}</span>
                        </div>
                        <div className="flex justify-between items-center gap-2">
                          <span className="text-gray-600">Email:</span>
                          <span className="font-medium text-gray-900 flex items-center gap-1 text-xs">
                            <Mail className="w-3 h-3" />
                            {selectedOrder.client?.email}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-gray-600">Téléphone:</span>
                          <span className="font-medium text-gray-900 flex items-center gap-1">
                            <Phone className="w-3 h-3" />
                            {selectedOrder.client?.phone}
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Order Info */}
                    <div className="bg-gradient-to-br from-purple-50 to-pink-50 p-5 rounded-lg border-2 border-purple-300 shadow-sm">
                      <h3 className="font-bold text-purple-800 mb-3 flex items-center gap-2 text-base">
                        <FileText className="w-5 h-5" />
                        Commande
                      </h3>
                      <div className="space-y-2 text-sm">
                        <div className="flex justify-between">
                          <span className="text-gray-600">Numéro:</span>
                          <span className="font-semibold text-gray-900">{selectedOrder.orderNumber}</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-gray-600">Date:</span>
                          <span className="font-medium text-gray-900">{new Date(selectedOrder.createdAt).toLocaleDateString('fr-FR')}</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-gray-600">Articles:</span>
                          <span className="font-medium text-gray-900">{selectedOrder.items?.length || 0} article(s)</span>
                        </div>
                        <div className="flex justify-between items-center pt-2 border-t border-purple-200">
                          <span className="text-gray-600 font-semibold">Total:</span>
                          <span className="text-xl font-bold text-purple-700">{selectedOrder.totalAmount?.toFixed(2)}€</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Invoice Details */}
                  <div className="bg-gradient-to-br from-amber-50 to-orange-50 p-5 rounded-lg border-2 border-amber-300 shadow-sm">
                    <h3 className="font-bold text-amber-800 mb-4 flex items-center gap-2 text-base">
                      <CreditCard className="w-5 h-5" />
                      Informations de facturation
                    </h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div>
                        <label className="block text-sm font-semibold text-gray-700 mb-2">
                          Date de facture
                        </label>
                        <input
                          type="date"
                          value={createForm.invoiceDate}
                          onChange={(e) => setCreateForm({ ...createForm, invoiceDate: e.target.value })}
                          className="w-full px-3 py-2.5 text-sm border-2 border-amber-200 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-amber-500 transition-all"
                          required
                        />
                      </div>
                      <div>
                        <label className="block text-sm font-semibold text-gray-700 mb-2">
                          Date d'échéance
                        </label>
                        <input
                          type="date"
                          value={createForm.dueDate}
                          onChange={(e) => setCreateForm({ ...createForm, dueDate: e.target.value })}
                          className="w-full px-3 py-2.5 text-sm border-2 border-amber-200 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-amber-500 transition-all"
                          required
                        />
                      </div>
                      <div>
                        <label className="block text-sm font-semibold text-gray-700 mb-2">
                          Méthode de paiement
                        </label>
                        <select
                          value={createForm.paymentMethod}
                          onChange={(e) => setCreateForm({ ...createForm, paymentMethod: e.target.value })}
                          className="w-full px-3 py-2.5 text-sm border-2 border-amber-200 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-amber-500 transition-all bg-white"
                          required
                        >
                          <option value="CASH">Espèces</option>
                          <option value="CREDIT_CARD">Carte de crédit</option>
                          <option value="DEBIT_CARD">Carte de débit</option>
                          <option value="BANK_TRANSFER">Virement bancaire</option>
                          <option value="CHECK">Chèque</option>
                          <option value="MOBILE_PAYMENT">Paiement mobile</option>
                        </select>
                      </div>
                      <div>
                        <label className="block text-sm font-semibold text-gray-700 mb-2">
                          Taux de TVA (%)
                        </label>
                        <input
                          type="number"
                          step="0.01"
                          value={createForm.taxRate}
                          onChange={(e) => setCreateForm({ ...createForm, taxRate: e.target.value })}
                          className="w-full px-3 py-2.5 text-sm border-2 border-amber-200 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-amber-500 transition-all"
                          required
                        />
                      </div>
                      <div className="md:col-span-2">
                        <label className="block text-sm font-semibold text-gray-700 mb-2">
                          Notes additionnelles
                        </label>
                        <textarea
                          value={createForm.notes}
                          onChange={(e) => setCreateForm({ ...createForm, notes: e.target.value })}
                          className="w-full px-3 py-2.5 text-sm border-2 border-amber-200 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-amber-500 transition-all resize-none"
                          rows="3"
                          placeholder="Entrez des notes ou commentaires pour cette facture..."
                        />
                      </div>
                    </div>
                  </div>

                  {/* Invoice Preview Calculation */}
                  <div className="bg-gradient-to-br from-gray-50 to-slate-100 p-5 rounded-lg border-2 border-gray-300 shadow-sm">
                    <h3 className="font-bold text-gray-800 mb-4 flex items-center gap-2 text-base">
                      <DollarSign className="w-5 h-5" />
                      Aperçu de la facture
                    </h3>
                    <div className="space-y-2">
                      <div className="flex justify-between text-sm">
                        <span className="text-gray-600">Sous-total (HT)</span>
                        <span className="font-semibold">{selectedOrder.totalAmount?.toFixed(2)}€</span>
                      </div>
                      <div className="flex justify-between text-sm">
                        <span className="text-gray-600">TVA ({createForm.taxRate}%)</span>
                        <span className="font-semibold text-blue-600">
                          +{((selectedOrder.totalAmount || 0) * parseFloat(createForm.taxRate || 0) / 100).toFixed(2)}€
                        </span>
                      </div>
                      <div className="flex justify-between text-lg font-bold border-t pt-2 mt-2">
                        <span>Total TTC</span>
                        <span className="text-green-600">
                          {((selectedOrder.totalAmount || 0) + (selectedOrder.totalAmount || 0) * parseFloat(createForm.taxRate || 0) / 100).toFixed(2)}€
                        </span>
                      </div>
                    </div>
                  </div>
                </>
              )}

              {/* Actions */}
              <div className="flex gap-3 pt-2">
                <button
                  onClick={() => {
                    setShowCreateModal(false);
                    setSelectedOrder(null);
                    setCreateForm({
                      orderId: '',
                      invoiceDate: new Date().toISOString().split('T')[0],
                      dueDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
                      paymentMethod: 'CASH',
                      taxRate: '20.00',
                      notes: ''
                    });
                  }}
                  className="flex-1 px-6 py-3 bg-gray-200 hover:bg-gray-300 text-gray-800 font-semibold rounded-lg transition-all shadow-sm"
                >
                  Annuler
                </button>
                <button
                  onClick={handleCreateInvoice}
                  className="flex-1 px-6 py-3 bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-700 hover:to-emerald-700 text-white font-semibold rounded-lg transition-all shadow-md disabled:opacity-50 disabled:cursor-not-allowed"
                  disabled={!createForm.orderId}
                >
                  Créer la facture
                </button>
              </div>
            </div>
          </Modal>
        )}
      </AnimatePresence>

      {/* Payment Modal */}
      <AnimatePresence>
        {showPaymentModal && selectedInvoice && (
          <Modal
            isOpen={showPaymentModal}
            onClose={() => setShowPaymentModal(false)}
            title="Enregistrer un paiement"
          >
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Montant à payer
                </label>
                <div className="flex items-center gap-2">
                  <input
                    type="number"
                    step="0.01"
                    value={paymentData.amount}
                    onChange={(e) => setPaymentData({ ...paymentData, amount: e.target.value })}
                    className="input flex-1"
                    placeholder="Montant"
                  />
                  <span className="text-gray-600">€</span>
                </div>
                <p className="text-sm text-gray-500 mt-1">
                  Reste à payer: {(selectedInvoice.totalAmount - selectedInvoice.paidAmount).toFixed(2)}€
                </p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Méthode de paiement
                </label>
                <select
                  value={paymentData.paymentMethod}
                  onChange={(e) => setPaymentData({ ...paymentData, paymentMethod: e.target.value })}
                  className="input w-full"
                >
                  <option value="CASH">Espèces</option>
                  <option value="CREDIT_CARD">Carte de crédit</option>
                  <option value="DEBIT_CARD">Carte de débit</option>
                  <option value="BANK_TRANSFER">Virement bancaire</option>
                  <option value="CHECK">Chèque</option>
                  <option value="MOBILE_PAYMENT">Paiement mobile</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Date de paiement
                </label>
                <input
                  type="date"
                  value={paymentData.paymentDate}
                  onChange={(e) => setPaymentData({ ...paymentData, paymentDate: e.target.value })}
                  className="input w-full"
                />
              </div>

              <div className="flex gap-3 pt-4">
                <button
                  onClick={() => setShowPaymentModal(false)}
                  className="btn-secondary flex-1"
                >
                  Annuler
                </button>
                <button
                  onClick={handlePayment}
                  className="btn-primary flex-1"
                >
                  Enregistrer le paiement
                </button>
              </div>
            </div>
          </Modal>
        )}
      </AnimatePresence>

      {/* Confirmation Modal */}
      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmCreateInvoice}
        title="Confirmer la création"
        message={`Voulez-vous vraiment créer cette facture${selectedOrder ? ` pour la commande ${selectedOrder.orderNumber}` : ''} ?`}
        type="info"
      />
    </div>
  );
};

export default Invoices;
