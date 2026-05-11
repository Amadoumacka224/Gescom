import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Plus, Truck, MapPin, Calendar, Edit, Trash2, Clock, CheckCircle, XCircle, User, Phone, Hash, FileText } from 'lucide-react';
import api from '../services/api';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Pagination from '../components/Pagination';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import Button from '../components/Button';
import Table from '../components/Table';

const TERMINAL_STATUSES = ['INVOICED', 'CANCELED'];

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
      const availableOrders = response.data.filter(order => order.status === 'CONFIRMED');
      setOrders(availableOrders);
    } catch (error) {
      console.error('Error fetching orders:', error);
      setOrders([]);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));

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
      alert(t('deliveries.selectOrder'));
      return;
    }

    if (!formData.deliveryAddress || !formData.contactName || !formData.contactPhone || !formData.scheduledDate) {
      alert(t('deliveries.fillRequiredFields'));
      return;
    }

    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    try {
      const deliveryData = {
        orderId: parseInt(formData.orderId),
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
        alert(t('deliveries.updatedSuccess'));
      } else {
        await api.post('/deliveries', deliveryData);
        alert(t('deliveries.createdSuccess'));
      }

      handleCloseModal();
      fetchDeliveries();
      fetchOrders();
    } catch (error) {
      console.error('Error saving delivery:', error);
      const errorMessage = error.response?.data || error.message || t('deliveries.saveError');
      alert(t('common.errorPrefix') + errorMessage);
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
    if (window.confirm(t('deliveries.confirmDelete'))) {
      try {
        await api.delete(`/deliveries/${id}`);
        alert(t('deliveries.deleteSuccess'));
        fetchDeliveries();
      } catch (error) {
        console.error('Error deleting delivery:', error);
        alert(t('deliveries.deleteError'));
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
      PENDING: { class: 'badge-warning', key: 'deliveries.statusPending', icon: Clock },
      DELIVERED: { class: 'badge-success', key: 'deliveries.statusDelivered', icon: CheckCircle },
      INVOICED: { class: 'bg-purple-100 text-purple-700 border-purple-200', key: 'deliveries.statusInvoiced', icon: CheckCircle },
      CANCELED: { class: 'badge-danger', key: 'deliveries.statusCanceled', icon: XCircle }
    };
    const badge = badges[status] || badges.PENDING;
    const Icon = badge.icon;

    return (
      <span className={`badge ${badge.class} flex items-center gap-1`}>
        <Icon className="w-3 h-3" />
        {t(badge.key)}
      </span>
    );
  };

  const handleCreateInvoice = async (deliveryId) => {
    if (!window.confirm(t('deliveries.createInvoiceConfirmation'))) {
      return;
    }

    try {
      const response = await api.post(`/deliveries/${deliveryId}/create-invoice`);
      const invoice = response.data;

      alert(t('deliveries.invoiceCreatedSuccess'));
      fetchDeliveries();
      navigate('/invoices', { state: { invoiceId: invoice.id } });
    } catch (error) {
      console.error('Error creating invoice:', error);
      const errorMessage = error.response?.data || t('deliveries.createInvoiceError');
      alert(errorMessage);
    }
  };

  const stats = {
    total: deliveries.length,
    pending: deliveries.filter(d => d.status === 'PENDING').length,
    delivered: deliveries.filter(d => d.status === 'DELIVERED').length
  };

  const totalPages = Math.ceil(deliveries.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const paginatedDeliveries = deliveries.slice(startIndex, endIndex);

  const handlePageChange = (page) => setCurrentPage(page);
  const handleItemsPerPageChange = (newItemsPerPage) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1);
  };

  const statusOptions = [
    { value: 'PENDING', label: t('deliveries.statusPending') },
    { value: 'DELIVERED', label: t('deliveries.statusDelivered') },
    { value: 'CANCELED', label: t('deliveries.statusCanceled') }
  ];

  const columns = [
    {
      key: 'deliveryNumber',
      label: t('deliveries.deliveryNumber'),
      render: (delivery) => (
        <div className="flex items-center gap-2">
          <Hash className="w-4 h-4 text-gray-400" />
          <span className="font-medium text-gray-900">{delivery.deliveryNumber}</span>
        </div>
      )
    },
    {
      key: 'order',
      label: t('deliveries.order'),
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
      label: t('deliveries.address'),
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
      label: t('deliveries.columnContact'),
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
      label: t('deliveries.scheduledDate'),
      render: (delivery) => (
        <div className="flex items-center gap-2 text-sm">
          <Calendar className="w-4 h-4 text-gray-400" />
          <span className="text-gray-900">{delivery.scheduledDate}</span>
        </div>
      )
    },
    {
      key: 'status',
      label: t('deliveries.status'),
      render: (delivery) => getStatusBadge(delivery.status)
    }
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">{t('deliveries.title')}</h1>
          <p className="text-gray-600 mt-1">{t('deliveries.subtitle')}</p>
        </div>
        <Button variant="primary" icon={Plus} onClick={() => setShowModal(true)}>
          {t('deliveries.addDelivery')}
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="card bg-gradient-to-br from-blue-50 to-cyan-50 border-blue-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-blue-600 font-medium">{t('deliveries.totalCount')}</p>
              <p className="text-3xl font-bold text-blue-700">{stats.total}</p>
            </div>
            <Truck className="w-12 h-12 text-blue-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-yellow-50 to-orange-50 border-yellow-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-yellow-600 font-medium">{t('deliveries.countPending')}</p>
              <p className="text-3xl font-bold text-yellow-700">{stats.pending}</p>
            </div>
            <Clock className="w-12 h-12 text-yellow-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-green-50 to-emerald-50 border-green-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-green-600 font-medium">{t('deliveries.countDelivered')}</p>
              <p className="text-3xl font-bold text-green-700">{stats.delivered}</p>
            </div>
            <CheckCircle className="w-12 h-12 text-green-600 opacity-50" />
          </div>
        </div>
      </div>

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
                  title={t('deliveries.createInvoiceTooltip')}
                >
                  <FileText className="w-4 h-4" />
                  <span>{t('deliveries.invoiceShortButton')}</span>
                </button>
              )}
              <button
                onClick={() => handleEdit(delivery)}
                className="text-primary-600 hover:text-primary-900 p-2 hover:bg-primary-50 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                title={t('common.edit')}
                disabled={TERMINAL_STATUSES.includes(delivery.status)}
              >
                <Edit className="w-4 h-4" />
              </button>
              <button
                onClick={() => handleDelete(delivery.id)}
                className="text-red-600 hover:text-red-900 p-2 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                title={t('common.delete')}
                disabled={TERMINAL_STATUSES.includes(delivery.status)}
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          )}
        />

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

      <Modal
        isOpen={showModal}
        onClose={handleCloseModal}
        title={editingDelivery ? t('deliveries.editTitle') : t('deliveries.createTitle')}
        size="lg"
      >
        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <FormSelect
              label={t('deliveries.orderLabel')}
              name="orderId"
              value={formData.orderId}
              onChange={handleInputChange}
              required
              options={orders.map(order => ({
                value: order.id.toString(),
                label: `${order.orderNumber} - ${order.client.firstName} ${order.client.lastName} (${order.totalAmount.toFixed(2)}€)`
              }))}
              placeholder={t('deliveries.orderPlaceholder')}
            />
          </div>

          <div className="border-t border-gray-200 pt-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <MapPin className="w-5 h-5 text-primary-600" />
              {t('deliveries.addressSectionTitle')}
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="md:col-span-2">
                <FormInput
                  label={t('deliveries.addressLabel')}
                  name="deliveryAddress"
                  value={formData.deliveryAddress}
                  onChange={handleInputChange}
                  placeholder={t('deliveries.addressPlaceholder')}
                  required
                  icon={MapPin}
                />
              </div>

              <FormInput
                label={t('deliveries.cityLabel')}
                name="deliveryCity"
                value={formData.deliveryCity}
                onChange={handleInputChange}
                placeholder={t('deliveries.cityPlaceholder')}
                required
                icon={MapPin}
              />

              <FormInput
                label={t('deliveries.postalCodeLabel')}
                name="deliveryPostalCode"
                value={formData.deliveryPostalCode}
                onChange={handleInputChange}
                placeholder={t('deliveries.postalCodePlaceholder')}
                required
              />

              <FormInput
                label={t('deliveries.countryLabel')}
                name="deliveryCountry"
                value={formData.deliveryCountry}
                onChange={handleInputChange}
                placeholder={t('deliveries.countryPlaceholder')}
                required
              />

              <FormInput
                label={t('deliveries.scheduledDateLabel')}
                name="scheduledDate"
                type="date"
                value={formData.scheduledDate}
                onChange={handleInputChange}
                required
                icon={Calendar}
              />
            </div>
          </div>

          <div className="border-t border-gray-200 pt-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <User className="w-5 h-5 text-primary-600" />
              {t('deliveries.contactSectionTitle')}
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <FormInput
                label={t('deliveries.contactNameLabel')}
                name="contactName"
                value={formData.contactName}
                onChange={handleInputChange}
                placeholder={t('deliveries.contactNamePlaceholder')}
                required
                icon={User}
              />

              <FormInput
                label={t('deliveries.contactPhoneLabel')}
                name="contactPhone"
                type="tel"
                value={formData.contactPhone}
                onChange={handleInputChange}
                placeholder={t('deliveries.contactPhonePlaceholder')}
                required
                icon={Phone}
              />
            </div>
          </div>

          <div className="border-t border-gray-200 pt-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
              <FormSelect
                label={t('deliveries.statusLabel')}
                name="status"
                value={formData.status}
                onChange={handleInputChange}
                required
                options={statusOptions}
              />
            </div>

            <FormInput
              label={t('deliveries.notesLabel')}
              name="notes"
              type="textarea"
              value={formData.notes}
              onChange={handleInputChange}
              placeholder={t('deliveries.notesPlaceholder')}
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200">
            <Button variant="secondary" onClick={handleCloseModal} type="button">
              {t('common.cancel')}
            </Button>
            <Button variant="primary" type="submit" icon={editingDelivery ? Edit : Plus}>
              {editingDelivery ? t('common.edit') : t('common.create')}
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmSubmit}
        title={editingDelivery ? t('deliveries.confirmEditTitle') : t('deliveries.confirmCreateTitle')}
        message={editingDelivery ? t('deliveries.confirmEditMessage') : t('deliveries.confirmCreateMessage')}
        type="info"
      />
    </div>
  );
};

export default Deliveries;
