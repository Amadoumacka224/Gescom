import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Plus, Truck, MapPin, Calendar, Edit, Trash2, Package, Clock, CheckCircle, XCircle, User, Phone, Hash, FileText } from 'lucide-react';
import { motion } from 'framer-motion';
import api from '../services/api';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Pagination from '../components/Pagination';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import Button from '../components/Button';
import Table from '../components/Table';

const Deliveries = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [deliveries, setDeliveries] = useState([]);
  const [orders, setOrders] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [editingDelivery, setEditingDelivery] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [formData, setFormData] = useState({
    orderId: '',
    deliveryAddress: '',
    deliveryCity: '',
    deliveryPostalCode: '',
    deliveryCountry: 'France',
    scheduledDate: '',
    contactName: '',
    contactPhone: '',
    notes: '',
    status: 'PENDING'
  });

  useEffect(() => {
    fetchDeliveries();
    fetchOrders();
  }, []);

  const fetchDeliveries = async () => {
    try {
      const response = await api.get('/deliveries');
      setDeliveries(response.data);
    } catch (error) {
      console.error('Error fetching deliveries:', error);
      setDeliveries([]);
    }
  };

  const fetchOrders = async () => {
    try {
      const response = await api.get('/orders');
      // Filtrer uniquement les commandes confirmées (pas encore livrées)
      const availableOrders = response.data.filter(
        order => order.status === 'CONFIRMED'
      );
      setOrders(availableOrders);
    } catch (error) {
      console.error('Error fetching orders:', error);
      setOrders([]);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));

    // Auto-remplir les informations du contact quand on sélectionne une commande
    if (name === 'orderId' && value) {
      const selectedOrder = orders.find(o => o.id === parseInt(value));
      if (selectedOrder) {
        setFormData(prev => ({
          ...prev,
          contactName: `${selectedOrder.client.firstName} ${selectedOrder.client.lastName}`,
          contactPhone: selectedOrder.client.phone
        }));
      }
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();

    if (!formData.orderId) {
      alert('Veuillez sélectionner une commande');
      return;
    }

    if (!formData.deliveryAddress || !formData.contactName || !formData.contactPhone || !formData.scheduledDate) {
      alert('Veuillez remplir tous les champs obligatoires');
      return;
    }

    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    try {
      const deliveryData = {
        order: { id: parseInt(formData.orderId) },
        deliveryAddress: formData.deliveryAddress,
        deliveryCity: formData.deliveryCity,
        deliveryPostalCode: formData.deliveryPostalCode,
        deliveryCountry: formData.deliveryCountry,
        contactName: formData.contactName,
        contactPhone: formData.contactPhone,
        scheduledDate: new Date(formData.scheduledDate).toISOString(),
        status: formData.status,
        notes: formData.notes
      };

      if (editingDelivery) {
        await api.put(`/deliveries/${editingDelivery.id}`, deliveryData);
        alert('✅ Livraison modifiée avec succès!');
      } else {
        await api.post('/deliveries', deliveryData);
        alert('✅ Livraison créée avec succès!');
      }

      handleCloseModal();
      fetchDeliveries();
      fetchOrders();
    } catch (error) {
      console.error('Error saving delivery:', error);
      const errorMessage = error.response?.data || error.message || 'Erreur lors de l\'enregistrement';
      alert('❌ Erreur: ' + errorMessage);
    }
  };

  const handleEdit = (delivery) => {
    setEditingDelivery(delivery);
    setFormData({
      orderId: delivery.order.id.toString(),
      deliveryAddress: delivery.deliveryAddress,
      deliveryCity: delivery.deliveryCity,
      deliveryPostalCode: delivery.deliveryPostalCode,
      deliveryCountry: delivery.deliveryCountry,
      scheduledDate: delivery.scheduledDate,
      contactName: delivery.contactName,
      contactPhone: delivery.contactPhone,
      notes: delivery.notes || '',
      status: delivery.status
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (window.confirm('Êtes-vous sûr de vouloir supprimer cette livraison ?')) {
      try {
        await api.delete(`/deliveries/${id}`);
        alert('Livraison supprimée avec succès');
        fetchDeliveries();
      } catch (error) {
        console.error('Error deleting delivery:', error);
        alert('Erreur lors de la suppression');
      }
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingDelivery(null);
    setFormData({
      orderId: '',
      deliveryAddress: '',
      deliveryCity: '',
      deliveryPostalCode: '',
      deliveryCountry: 'France',
      scheduledDate: '',
      contactName: '',
      contactPhone: '',
      notes: '',
      status: 'PENDING'
    });
  };

  const getStatusBadge = (status) => {
    const badges = {
      PENDING: { class: 'badge-warning', text: 'En attente', icon: Clock },
      IN_TRANSIT: { class: 'badge-info', text: 'En transit', icon: Truck },
      DELIVERED: { class: 'badge-success', text: 'Livrée', icon: CheckCircle },
      INVOICED: { class: 'bg-purple-100 text-purple-700 border-purple-200', text: 'Facturée', icon: CheckCircle },
      CANCELED: { class: 'badge-danger', text: 'Annulée', icon: XCircle },
    };
    const badge = badges[status] || badges.PENDING;
    const Icon = badge.icon;

    return (
      <span className={`badge ${badge.class} flex items-center gap-1`}>
        <Icon className="w-3 h-3" />
        {badge.text}
      </span>
    );
  };

  const handleCreateInvoice = async (deliveryId) => {
    if (!window.confirm('Voulez-vous créer une facture pour cette livraison ?')) {
      return;
    }

    try {
      const response = await api.post(`/deliveries/${deliveryId}/create-invoice`);
      const invoice = response.data;

      alert('Facture créée avec succès');
      fetchDeliveries(); // Rafraîchir les données depuis le serveur

      // Naviguer vers la page des factures avec l'ID de la facture
      navigate('/invoices', { state: { invoiceId: invoice.id } });
    } catch (error) {
      console.error('Error creating invoice:', error);
      const errorMessage = error.response?.data || 'Erreur lors de la création de la facture';
      alert(errorMessage);
    }
  };

  // Statistiques
  const stats = {
    total: deliveries.length,
    pending: deliveries.filter(d => d.status === 'PENDING').length,
    inTransit: deliveries.filter(d => d.status === 'IN_TRANSIT').length,
    delivered: deliveries.filter(d => d.status === 'DELIVERED').length,
  };

  // Pagination
  const totalPages = Math.ceil(deliveries.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const paginatedDeliveries = deliveries.slice(startIndex, endIndex);

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const handleItemsPerPageChange = (newItemsPerPage) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1);
  };

  const columns = [
    {
      key: 'deliveryNumber',
      label: 'N° Livraison',
      render: (delivery) => (
        <div className="flex items-center gap-2">
          <Hash className="w-4 h-4 text-gray-400" />
          <span className="font-medium text-gray-900">{delivery.deliveryNumber}</span>
        </div>
      )
    },
    {
      key: 'order',
      label: 'Commande',
      render: (delivery) => (
        <div>
          <div className="font-medium text-gray-900">{delivery.order.orderNumber}</div>
          <div className="text-sm text-gray-500">
            {delivery.order.client.firstName} {delivery.order.client.lastName}
          </div>
        </div>
      )
    },
    {
      key: 'address',
      label: 'Adresse de livraison',
      render: (delivery) => (
        <div className="flex items-start gap-2">
          <MapPin className="w-4 h-4 text-gray-400 mt-0.5" />
          <div className="text-sm">
            <div className="text-gray-900">{delivery.deliveryAddress}</div>
            <div className="text-gray-500">
              {delivery.deliveryPostalCode} {delivery.deliveryCity}
            </div>
          </div>
        </div>
      )
    },
    {
      key: 'contact',
      label: 'Contact',
      render: (delivery) => (
        <div className="text-sm">
          <div className="flex items-center gap-1 text-gray-900">
            <User className="w-3 h-3 text-gray-400" />
            {delivery.contactName}
          </div>
          <div className="flex items-center gap-1 text-gray-500">
            <Phone className="w-3 h-3 text-gray-400" />
            {delivery.contactPhone}
          </div>
        </div>
      )
    },
    {
      key: 'scheduledDate',
      label: 'Date prévue',
      render: (delivery) => (
        <div className="flex items-center gap-2 text-sm">
          <Calendar className="w-4 h-4 text-gray-400" />
          <span className="text-gray-900">{delivery.scheduledDate}</span>
        </div>
      )
    },
    {
      key: 'status',
      label: 'Statut',
      render: (delivery) => getStatusBadge(delivery.status)
    }
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">{t('deliveries.title')}</h1>
          <p className="text-gray-600 mt-1">Suivez vos livraisons en temps réel</p>
        </div>
        <Button
          variant="primary"
          icon={Plus}
          onClick={() => setShowModal(true)}
        >
          {t('deliveries.addDelivery')}
        </Button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <div className="card bg-gradient-to-br from-blue-50 to-cyan-50 border-blue-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-blue-600 font-medium">Total Livraisons</p>
              <p className="text-3xl font-bold text-blue-700">{stats.total}</p>
            </div>
            <Truck className="w-12 h-12 text-blue-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-yellow-50 to-orange-50 border-yellow-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-yellow-600 font-medium">En attente</p>
              <p className="text-3xl font-bold text-yellow-700">{stats.pending}</p>
            </div>
            <Clock className="w-12 h-12 text-yellow-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-blue-50 to-indigo-50 border-blue-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-blue-600 font-medium">En transit</p>
              <p className="text-3xl font-bold text-blue-700">{stats.inTransit}</p>
            </div>
            <Truck className="w-12 h-12 text-blue-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-green-50 to-emerald-50 border-green-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-green-600 font-medium">Livrées</p>
              <p className="text-3xl font-bold text-green-700">{stats.delivered}</p>
            </div>
            <CheckCircle className="w-12 h-12 text-green-600 opacity-50" />
          </div>
        </div>
      </div>

      {/* Deliveries Table */}
      <div className="card overflow-hidden">
        <Table
          columns={columns}
          data={paginatedDeliveries}
          actions={(delivery) => (
            <div className="flex items-center gap-2">
              {delivery.status === 'DELIVERED' && (
                <button
                  onClick={() => handleCreateInvoice(delivery.id)}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-700 hover:to-indigo-700 text-white text-xs font-semibold rounded-lg transition-all shadow-sm hover:shadow-md"
                  title="Créer une facture"
                >
                  <FileText className="w-4 h-4" />
                  <span>Facturer</span>
                </button>
              )}
              <button
                onClick={() => handleEdit(delivery)}
                className="text-primary-600 hover:text-primary-900 p-2 hover:bg-primary-50 rounded-lg transition-colors"
                title="Modifier"
                disabled={delivery.status === 'INVOICED'}
              >
                <Edit className="w-4 h-4" />
              </button>
              <button
                onClick={() => handleDelete(delivery.id)}
                className="text-red-600 hover:text-red-900 p-2 hover:bg-red-50 rounded-lg transition-colors"
                title="Supprimer"
                disabled={delivery.status === 'INVOICED'}
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          )}
        />

        {/* Pagination */}
        {deliveries.length > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={deliveries.length}
            itemsPerPage={itemsPerPage}
            onPageChange={handlePageChange}
            onItemsPerPageChange={handleItemsPerPageChange}
          />
        )}
      </div>

      {/* Delivery Form Modal */}
      <Modal
        isOpen={showModal}
        onClose={handleCloseModal}
        title={editingDelivery ? 'Modifier la livraison' : 'Créer une livraison'}
        size="lg"
      >
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Order Selection */}
          <div>
            <FormSelect
              label="Commande"
              name="orderId"
              value={formData.orderId}
              onChange={handleInputChange}
              required
              options={orders.map(order => ({
                value: order.id.toString(),
                label: `${order.orderNumber} - ${order.client.firstName} ${order.client.lastName} (${order.totalAmount.toFixed(2)}€)`
              }))}
              placeholder="Sélectionner une commande..."
            />
          </div>

          {/* Delivery Address */}
          <div className="border-t border-gray-200 pt-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <MapPin className="w-5 h-5 text-primary-600" />
              Adresse de livraison
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="md:col-span-2">
                <FormInput
                  label="Adresse"
                  name="deliveryAddress"
                  value={formData.deliveryAddress}
                  onChange={handleInputChange}
                  placeholder="123 Rue Example"
                  required
                  icon={MapPin}
                />
              </div>

              <FormInput
                label="Ville"
                name="deliveryCity"
                value={formData.deliveryCity}
                onChange={handleInputChange}
                placeholder="Paris"
                required
                icon={MapPin}
              />

              <FormInput
                label="Code postal"
                name="deliveryPostalCode"
                value={formData.deliveryPostalCode}
                onChange={handleInputChange}
                placeholder="75000"
                required
              />

              <FormInput
                label="Pays"
                name="deliveryCountry"
                value={formData.deliveryCountry}
                onChange={handleInputChange}
                placeholder="France"
                required
              />

              <FormInput
                label="Date de livraison prévue"
                name="scheduledDate"
                type="date"
                value={formData.scheduledDate}
                onChange={handleInputChange}
                required
                icon={Calendar}
              />
            </div>
          </div>

          {/* Contact Information */}
          <div className="border-t border-gray-200 pt-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <User className="w-5 h-5 text-primary-600" />
              Contact de livraison
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <FormInput
                label="Nom du contact"
                name="contactName"
                value={formData.contactName}
                onChange={handleInputChange}
                placeholder="Jean Dupont"
                required
                icon={User}
              />

              <FormInput
                label="Téléphone du contact"
                name="contactPhone"
                type="tel"
                value={formData.contactPhone}
                onChange={handleInputChange}
                placeholder="0600000000"
                required
                icon={Phone}
              />
            </div>
          </div>

          {/* Status and Notes */}
          <div className="border-t border-gray-200 pt-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
              <FormSelect
                label="Statut de la livraison"
                name="status"
                value={formData.status}
                onChange={handleInputChange}
                required
                options={[
                  { value: 'PENDING', label: 'En attente' },
                  { value: 'IN_TRANSIT', label: 'En transit' },
                  { value: 'DELIVERED', label: 'Livrée' },
                  { value: 'INVOICED', label: 'Facturée' },
                  { value: 'CANCELED', label: 'Annulée' }
                ]}
              />
            </div>

            <FormInput
              label="Notes (optionnel)"
              name="notes"
              type="textarea"
              value={formData.notes}
              onChange={handleInputChange}
              placeholder="Instructions spéciales, code d'accès, etc."
            />
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200">
            <Button
              variant="secondary"
              onClick={handleCloseModal}
              type="button"
            >
              Annuler
            </Button>
            <Button
              variant="primary"
              type="submit"
              icon={editingDelivery ? Edit : Plus}
            >
              {editingDelivery ? 'Modifier' : 'Créer'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Confirmation Modal */}
      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmSubmit}
        title={editingDelivery ? "Confirmer la modification" : "Confirmer la création"}
        message={editingDelivery
          ? `Voulez-vous vraiment modifier cette livraison ?`
          : `Voulez-vous vraiment créer cette livraison ?`
        }
        type="info"
      />
    </div>
  );
};

export default Deliveries;
