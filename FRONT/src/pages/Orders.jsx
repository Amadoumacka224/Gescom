import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Plus, Search, Eye, CheckCircle, XCircle, Clock, User, Mail, Phone,
  MapPin, Package, Calendar, DollarSign, Hash, Truck, ShoppingCart,
  ChevronLeft, ChevronRight, Edit, Trash2, TrendingUp, AlertCircle
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import api from '../services/api';
import clientService from '../services/clientService';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Pagination from '../components/Pagination';
import Button from '../components/Button';
import FormSelect from '../components/FormSelect';
import { toast } from 'react-hot-toast';

const Orders = () => {
  const { t } = useTranslation();
  const [orders, setOrders] = useState([]);
  const [products, setProducts] = useState([]);
  const [clients, setClients] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState('ALL');
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showCreateClientModal, setShowCreateClientModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [editForm, setEditForm] = useState({
    status: '',
    totalAmount: '',
    orderItems: []
  });
  const [createForm, setCreateForm] = useState({
    clientId: '',
    status: 'PENDING',
    orderItems: [],
    totalAmount: 0
  });
  const [newClientForm, setNewClientForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    address: '',
    company: '',
    type: 'PARTICULIER',
    active: true
  });

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);

  useEffect(() => {
    fetchOrders();
    fetchProducts();
    fetchClients();
  }, []);

  useEffect(() => {
    // Check if there's a selected order ID from Reports page
    const selectedOrderId = localStorage.getItem('selectedOrderId');
    if (selectedOrderId && orders.length > 0) {
      const order = orders.find(o => o.id === parseInt(selectedOrderId));
      if (order) {
        setSelectedOrder(order);
        setShowDetailsModal(true);
        // Scroll to the order in the list
        setTimeout(() => {
          const element = document.getElementById(`order-${selectedOrderId}`);
          if (element) {
            element.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }, 100);
      }
      // Clear the stored ID
      localStorage.removeItem('selectedOrderId');
    }
  }, [orders]);

  const fetchProducts = async () => {
    try {
      const response = await api.get('/products/active');
      setProducts(response.data);
    } catch (error) {
      console.error('Error fetching products:', error);
      setProducts([]);
    }
  };

  const fetchClients = async () => {
    try {
      const response = await clientService.getActiveClients();
      setClients(response.data);
    } catch (error) {
      console.error('Error fetching clients:', error);
      setClients([]);
    }
  };

  const fetchOrders = async () => {
    try {
      setLoading(true);
      const response = await api.get('/orders');
      setOrders(response.data);
    } catch (error) {
      console.error('Error fetching orders:', error);
      // Données de démonstration en cas d'erreur
      setOrders([
        {
          id: 1,
          orderNumber: 'CMD-2024-001',
          client: {
            firstName: 'Jean',
            lastName: 'Dupont',
            email: 'jean.dupont@email.com',
            phone: '0612345678',
            address: '123 Rue de la Paix'
          },
          createdAt: '2024-11-15T14:30:00',
          totalAmount: 1250.00,
          status: 'CONFIRMED',
          items: [
            { id: 1, product: { name: 'Produit A' }, quantity: 2, unitPrice: 250.00 },
            { id: 2, product: { name: 'Produit B' }, quantity: 3, unitPrice: 150.00 }
          ]
        },
        {
          id: 2,
          orderNumber: 'CMD-2024-002',
          client: {
            firstName: 'Marie',
            lastName: 'Martin',
            email: 'marie.martin@email.com',
            phone: '0698765432'
          },
          createdAt: '2024-11-14T10:15:00',
          totalAmount: 890.50,
          status: 'PENDING',
          items: []
        },
        {
          id: 3,
          orderNumber: 'CMD-2024-003',
          client: {
            firstName: 'Pierre',
            lastName: 'Bernard',
            email: 'pierre.bernard@email.com',
            phone: '0687654321'
          },
          createdAt: '2024-11-13T16:45:00',
          totalAmount: 2100.00,
          status: 'DELIVERED',
          items: []
        }
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleViewDetails = (order) => {
    // Optimisation: Utiliser directement les données si disponibles
    setSelectedOrder(order);
    setShowDetailsModal(true);
  };

  const handleEdit = (order) => {
    setSelectedOrder(order);
    // Transformer les items pour avoir le bon format avec productId
    const formattedItems = (order.items || []).map(item => ({
      productId: item.product?.id?.toString() || '',
      unitPrice: item.unitPrice || 0,
      quantity: item.quantity || 1,
      product: item.product // Garder l'objet product pour l'affichage
    }));

    setEditForm({
      clientId: order.client?.id?.toString() || '',
      status: order.status || 'PENDING',
      totalAmount: order.totalAmount || 0,
      orderItems: formattedItems
    });
    setShowEditModal(true);
  };

  const handleUpdateOrder = async () => {
    try {
      // Vérifier que tous les articles ont un produit sélectionné
      const hasEmptyProduct = editForm.orderItems.some(item => !item.productId);
      if (hasEmptyProduct) {
        toast.error('Veuillez sélectionner un produit pour chaque article');
        return;
      }

      if (editForm.orderItems.length === 0) {
        toast.error('Veuillez ajouter au moins un article à la commande');
        return;
      }

      // Transformer les items pour le backend
      const transformedItems = editForm.orderItems.map(item => ({
        product: { id: parseInt(item.productId) },
        quantity: parseInt(item.quantity),
        unitPrice: parseFloat(item.unitPrice)
      }));

      // Préparer les données pour la mise à jour
      const updateData = {
        status: editForm.status,
        items: transformedItems
      };

      await api.put(`/orders/${selectedOrder.id}`, updateData);
      toast.success('Commande modifiée avec succès');
      setShowEditModal(false);
      fetchOrders();
    } catch (error) {
      console.error('Error updating order:', error);
      if (error.response?.status === 401) {
        toast.error('Session expirée, veuillez vous reconnecter');
      } else {
        const errorMessage = error.response?.data || error.message;
        toast.error('Erreur: ' + errorMessage);
      }
    }
  };

  const handleEditFormChange = (field, value) => {
    setEditForm(prev => ({ ...prev, [field]: value }));
  };

  const handleEditItemChange = (index, field, value) => {
    const updatedItems = [...editForm.orderItems];

    // Si on change le produit, mettre à jour le prix automatiquement
    if (field === 'productId') {
      const selectedProduct = products.find(p => p.id === parseInt(value));
      if (selectedProduct) {
        updatedItems[index] = {
          ...updatedItems[index],
          productId: value,
          unitPrice: selectedProduct.sellingPrice
        };
      } else {
        updatedItems[index] = { ...updatedItems[index], [field]: value };
      }
    } else {
      updatedItems[index] = { ...updatedItems[index], [field]: value };
    }

    // Recalculer le total
    const newTotal = updatedItems.reduce((sum, item) =>
      sum + (item.unitPrice * item.quantity), 0
    );

    setEditForm(prev => ({
      ...prev,
      orderItems: updatedItems,
      totalAmount: newTotal
    }));
  };

  const handleRemoveItemFromEdit = (index) => {
    const updatedItems = editForm.orderItems.filter((_, i) => i !== index);
    const newTotal = updatedItems.reduce((sum, item) =>
      sum + (item.unitPrice * item.quantity), 0
    );

    setEditForm(prev => ({
      ...prev,
      orderItems: updatedItems,
      totalAmount: newTotal
    }));
  };

  const handleAddItemToEdit = () => {
    setEditForm(prev => ({
      ...prev,
      orderItems: [
        ...prev.orderItems,
        { productId: '', unitPrice: 0, quantity: 1 }
      ]
    }));
  };

  const handleCreateOrder = () => {
    setCreateForm({
      clientId: '',
      status: 'PENDING',
      orderItems: [],
      totalAmount: 0
    });
    setShowCreateModal(true);
  };

  const handleCreateClient = () => {
    setNewClientForm({
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      address: '',
      company: '',
      type: 'PARTICULIER',
      active: true
    });
    setShowCreateClientModal(true);
  };

  const handleNewClientFormChange = (field, value) => {
    setNewClientForm(prev => ({ ...prev, [field]: value }));
  };

  const handleSubmitNewClient = async () => {
    try {
      if (!newClientForm.firstName || !newClientForm.lastName || !newClientForm.phone) {
        toast.error('Veuillez remplir tous les champs obligatoires (Prénom, Nom, Téléphone)');
        return;
      }

      const response = await clientService.createClient(newClientForm);
      toast.success('Client créé avec succès');
      setShowCreateClientModal(false);

      // Recharger la liste des clients et sélectionner le nouveau client
      await fetchClients();
      setCreateForm(prev => ({ ...prev, clientId: response.data.id }));
    } catch (error) {
      console.error('Error creating client:', error);
      toast.error('Erreur lors de la création du client');
    }
  };

  const handleCreateFormChange = (field, value) => {
    setCreateForm(prev => ({ ...prev, [field]: value }));
  };

  const handleSubmitCreateOrder = () => {
    // Validate form
    if (!createForm.clientId) {
      toast.error('Veuillez sélectionner un client');
      return;
    }

    if (createForm.orderItems.length === 0) {
      toast.error('Veuillez ajouter au moins un article à la commande');
      return;
    }

    // Vérifier que tous les articles ont un produit sélectionné
    const hasEmptyProduct = createForm.orderItems.some(item => !item.productId);
    if (hasEmptyProduct) {
      toast.error('Veuillez sélectionner un produit pour chaque article');
      return;
    }

    setShowConfirmModal(true);
  };

  const confirmCreateOrder = async () => {
    try {
      // Transformer les items pour le backend
      const transformedItems = createForm.orderItems.map(item => ({
        product: { id: parseInt(item.productId) },
        quantity: item.quantity,
        unitPrice: item.unitPrice
      }));

      const orderData = {
        client: { id: parseInt(createForm.clientId) },
        status: createForm.status,
        totalAmount: createForm.totalAmount,
        items: transformedItems
      };

      await api.post('/orders', orderData);
      toast.success('✅ Commande créée avec succès');
      setShowCreateModal(false);
      fetchOrders();
    } catch (error) {
      console.error('Error creating order:', error);
      const errorMessage = error.response?.data || error.message || 'Erreur lors de la création de la commande';
      toast.error('❌ Erreur: ' + errorMessage);
    }
  };

  const handleAddItemToCreate = () => {
    setCreateForm(prev => ({
      ...prev,
      orderItems: [
        ...prev.orderItems,
        { productId: '', unitPrice: 0, quantity: 1 }
      ]
    }));
  };

  const handleCreateItemChange = (index, field, value) => {
    const updatedItems = [...createForm.orderItems];

    // Si on change le produit, mettre à jour le prix automatiquement
    if (field === 'productId') {
      const selectedProduct = products.find(p => p.id === parseInt(value));
      if (selectedProduct) {
        updatedItems[index] = {
          ...updatedItems[index],
          productId: value,
          unitPrice: selectedProduct.sellingPrice
        };
      } else {
        updatedItems[index] = { ...updatedItems[index], [field]: value };
      }
    } else {
      updatedItems[index] = { ...updatedItems[index], [field]: value };
    }

    // Recalculer le total
    const newTotal = updatedItems.reduce((sum, item) =>
      sum + (item.unitPrice * item.quantity), 0
    );

    setCreateForm(prev => ({
      ...prev,
      orderItems: updatedItems,
      totalAmount: newTotal
    }));
  };

  const handleRemoveItemFromCreate = (index) => {
    const updatedItems = createForm.orderItems.filter((_, i) => i !== index);
    const newTotal = updatedItems.reduce((sum, item) =>
      sum + (item.unitPrice * item.quantity), 0
    );

    setCreateForm(prev => ({
      ...prev,
      orderItems: updatedItems,
      totalAmount: newTotal
    }));
  };

  const handleDelete = async (order) => {
    if (window.confirm(`Êtes-vous sûr de vouloir supprimer la commande ${order.orderNumber} ?`)) {
      try {
        await api.delete(`/orders/${order.id}`);
        toast.success('Commande supprimée avec succès');
        fetchOrders();
      } catch (error) {
        console.error('Error deleting order:', error);
        toast.error('Erreur lors de la suppression');
      }
    }
  };

  const getStatusBadge = (status) => {
    const badges = {
      PENDING: { class: 'bg-yellow-100 text-yellow-700 border-yellow-200', text: 'En attente', icon: Clock },
      CONFIRMED: { class: 'bg-blue-100 text-blue-700 border-blue-200', text: 'Confirmée', icon: CheckCircle },
      DELIVERED: { class: 'bg-green-100 text-green-700 border-green-200', text: 'Livrée', icon: Truck },
      INVOICED: { class: 'bg-purple-100 text-purple-700 border-purple-200', text: 'Facturée', icon: DollarSign },
      CANCELED: { class: 'bg-red-100 text-red-700 border-red-200', text: 'Annulée', icon: XCircle }
    };
    const badge = badges[status] || badges.PENDING;
    const Icon = badge.icon;
    return (
      <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium border ${badge.class}`}>
        <Icon className="w-3 h-3" />
        {badge.text}
      </span>
    );
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('fr-FR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  // Filtrage
  const filteredOrders = orders.filter(order => {
    const matchesSearch =
      order.orderNumber?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      `${order.client?.firstName} ${order.client?.lastName}`.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = filterStatus === 'ALL' || order.status === filterStatus;
    return matchesSearch && matchesStatus;
  });

  // Pagination
  const indexOfLastItem = currentPage * itemsPerPage;
  const indexOfFirstItem = indexOfLastItem - itemsPerPage;
  const currentOrders = filteredOrders.slice(indexOfFirstItem, indexOfLastItem);
  const totalPages = Math.ceil(filteredOrders.length / itemsPerPage);

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const handleItemsPerPageChange = (newItemsPerPage) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1);
  };

  // Stats
  const stats = {
    total: orders.length,
    pending: orders.filter(o => o.status === 'PENDING').length,
    confirmed: orders.filter(o => o.status === 'CONFIRMED').length,
    delivered: orders.filter(o => o.status === 'DELIVERED').length,
    canceled: orders.filter(o => o.status === 'CANCELED').length,
    totalRevenue: orders.reduce((sum, order) => sum + (order.totalAmount || 0), 0)
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Commandes</h1>
          <p className="text-gray-600 mt-1">Gérez vos commandes clients</p>
        </div>
        <Button variant="primary" icon={Plus} onClick={handleCreateOrder}>
          Nouvelle commande
        </Button>
      </div>

      {/* Statistics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="card"
        >
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600">Total Commandes</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">{stats.total}</p>
            </div>
            <div className="w-12 h-12 bg-blue-100 rounded-xl flex items-center justify-center">
              <ShoppingCart className="w-6 h-6 text-blue-600" />
            </div>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="card"
        >
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600">Chiffre d'affaires</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">
                {stats.totalRevenue.toFixed(2)} €
              </p>
            </div>
            <div className="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center">
              <TrendingUp className="w-6 h-6 text-green-600" />
            </div>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="card"
        >
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600">En attente</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">{stats.pending}</p>
            </div>
            <div className="w-12 h-12 bg-yellow-100 rounded-xl flex items-center justify-center">
              <Clock className="w-6 h-6 text-yellow-600" />
            </div>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="card"
        >
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600">Livrées</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">{stats.delivered}</p>
            </div>
            <div className="w-12 h-12 bg-purple-100 rounded-xl flex items-center justify-center">
              <Truck className="w-6 h-6 text-purple-600" />
            </div>
          </div>
        </motion.div>
      </div>

      {/* Status Filter Buttons */}
      <div className="flex flex-wrap gap-2">
        <button
          onClick={() => setFilterStatus('ALL')}
          className={`px-4 py-2 rounded-lg font-medium transition-colors ${
            filterStatus === 'ALL'
              ? 'bg-primary-600 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100'
          }`}
        >
          Toutes ({stats.total})
        </button>
        <button
          onClick={() => setFilterStatus('PENDING')}
          className={`px-4 py-2 rounded-lg font-medium transition-colors ${
            filterStatus === 'PENDING'
              ? 'bg-yellow-600 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100'
          }`}
        >
          En attente ({stats.pending})
        </button>
        <button
          onClick={() => setFilterStatus('CONFIRMED')}
          className={`px-4 py-2 rounded-lg font-medium transition-colors ${
            filterStatus === 'CONFIRMED'
              ? 'bg-blue-600 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100'
          }`}
        >
          Confirmées ({stats.confirmed})
        </button>
        <button
          onClick={() => setFilterStatus('DELIVERED')}
          className={`px-4 py-2 rounded-lg font-medium transition-colors ${
            filterStatus === 'DELIVERED'
              ? 'bg-green-600 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100'
          }`}
        >
          Livrées ({stats.delivered})
        </button>
        <button
          onClick={() => setFilterStatus('CANCELED')}
          className={`px-4 py-2 rounded-lg font-medium transition-colors ${
            filterStatus === 'CANCELED'
              ? 'bg-red-600 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-100'
          }`}
        >
          Annulées ({stats.canceled})
        </button>
      </div>

      {/* Search */}
      <div className="card shadow-md">
        <div className="relative">
          <Search className="absolute left-4 top-1/2 transform -translate-y-1/2 w-5 h-5 text-blue-500" />
          <input
            type="text"
            placeholder="Rechercher par numéro de commande ou client..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="input pl-12 pr-4 py-3 w-full border-2 border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-200 rounded-lg text-sm"
          />
          {searchTerm && (
            <button
              onClick={() => setSearchTerm('')}
              className="absolute right-4 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-gray-600"
            >
              <XCircle className="w-5 h-5" />
            </button>
          )}
        </div>
      </div>

      {/* Orders Table */}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">N° Commande</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Client</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Montant</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Statut</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {currentOrders.map((order) => {
                const isHighlighted = selectedOrder?.id === order.id && showDetailsModal;
                return (
                <motion.tr
                  key={order.id}
                  id={`order-${order.id}`}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  onClick={() => handleViewDetails(order)}
                  className={`transition-colors cursor-pointer ${
                    isHighlighted
                      ? 'bg-blue-50 ring-2 ring-blue-400'
                      : 'hover:bg-gray-50'
                  }`}
                >
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center gap-2">
                      <ShoppingCart className="w-4 h-4 text-gray-400" />
                      <span className="font-medium text-gray-900">{order.orderNumber}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div>
                      <p className="font-medium text-gray-900">
                        {order.client?.firstName} {order.client?.lastName}
                      </p>
                      <p className="text-sm text-gray-500">{order.client?.email}</p>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                    {formatDate(order.createdAt)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className="font-semibold text-gray-900">
                      {order.totalAmount?.toFixed(2)} €
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    {getStatusBadge(order.status)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleViewDetails(order);
                        }}
                        className="text-blue-600 hover:text-blue-900 transition-colors"
                        title="Voir détails"
                      >
                        <Eye className="w-4 h-4" />
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleEdit(order);
                        }}
                        className="text-green-600 hover:text-green-900 transition-colors"
                        title="Modifier"
                      >
                        <Edit className="w-4 h-4" />
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDelete(order);
                        }}
                        className="text-red-600 hover:text-red-900 transition-colors"
                        title="Supprimer"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </motion.tr>
                );
              })}
            </tbody>
          </table>

          {/* Empty State */}
          {currentOrders.length === 0 && (
            <div className="text-center py-12">
              <ShoppingCart className="w-16 h-16 text-gray-300 mx-auto mb-4" />
              <p className="text-gray-500">Aucune commande trouvée</p>
            </div>
          )}
        </div>

        {/* Pagination */}
        {filteredOrders.length > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={filteredOrders.length}
            itemsPerPage={itemsPerPage}
            onPageChange={handlePageChange}
            onItemsPerPageChange={handleItemsPerPageChange}
          />
        )}
      </div>

      {/* Order Details Modal */}
      <AnimatePresence>
        {showDetailsModal && selectedOrder && (
          <Modal
            isOpen={showDetailsModal}
            onClose={() => setShowDetailsModal(false)}
            title="Détails de la commande"
          >
            <div className="space-y-6">
              {/* Order Info */}
              <div className="grid grid-cols-2 gap-4 p-4 bg-gray-50 rounded-lg">
                <div>
                  <p className="text-sm text-gray-600">Numéro de commande</p>
                  <p className="font-semibold text-gray-900">{selectedOrder.orderNumber}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Date</p>
                  <p className="font-semibold text-gray-900">{formatDate(selectedOrder.createdAt)}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Montant total</p>
                  <p className="font-semibold text-gray-900">{selectedOrder.totalAmount?.toFixed(2)} €</p>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Statut</p>
                  {getStatusBadge(selectedOrder.status)}
                </div>
              </div>

              {/* Client Info */}
              {selectedOrder.client && (
                <div>
                  <h3 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                    <User className="w-5 h-5" />
                    Informations client
                  </h3>
                  <div className="grid grid-cols-2 gap-4 p-4 bg-blue-50 rounded-lg">
                    <div>
                      <p className="text-sm text-gray-600">Nom</p>
                      <p className="font-semibold text-gray-900">
                        {selectedOrder.client.firstName} {selectedOrder.client.lastName}
                      </p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Email</p>
                      <p className="font-semibold text-gray-900 flex items-center gap-1">
                        <Mail className="w-4 h-4" />
                        {selectedOrder.client.email}
                      </p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Téléphone</p>
                      <p className="font-semibold text-gray-900 flex items-center gap-1">
                        <Phone className="w-4 h-4" />
                        {selectedOrder.client.phone}
                      </p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-600">Adresse</p>
                      <p className="font-semibold text-gray-900 flex items-center gap-1">
                        <MapPin className="w-4 h-4" />
                        {selectedOrder.client.address}
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {/* Order Items */}
              {selectedOrder.items && selectedOrder.items.length > 0 && (
                <div>
                  <h3 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                    <Package className="w-5 h-5" />
                    Articles commandés
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
                        {selectedOrder.items.map((item, index) => (
                          <tr key={index}>
                            <td className="px-4 py-3 text-sm text-gray-900">
                              {item.product?.name || item.productName || 'Produit'}
                            </td>
                            <td className="px-4 py-3 text-sm text-right text-gray-900">
                              {item.unitPrice?.toFixed(2)} €
                            </td>
                            <td className="px-4 py-3 text-sm text-right text-gray-900">
                              {item.quantity}
                            </td>
                            <td className="px-4 py-3 text-sm text-right font-semibold text-gray-900">
                              {(item.unitPrice * item.quantity).toFixed(2)} €
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          </Modal>
        )}
      </AnimatePresence>

      {/* Edit Order Modal */}
      <AnimatePresence>
        {showEditModal && selectedOrder && (
          <Modal
            isOpen={showEditModal}
            onClose={() => setShowEditModal(false)}
            title={`Modifier la commande ${selectedOrder.orderNumber}`}
            size="fullscreen"
          >
            <div className="space-y-5">
              {/* Order Number & Date Info */}
              <div className="bg-gradient-to-r from-gray-50 to-gray-100 p-4 rounded-xl border-2 border-gray-300 shadow-sm">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-xs text-gray-600 mb-1 font-semibold">Numéro de commande</p>
                    <p className="font-bold text-gray-900">{selectedOrder.orderNumber}</p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-600 mb-1 font-semibold">Date de commande</p>
                    <p className="font-semibold text-gray-900">{formatDate(selectedOrder.createdAt)}</p>
                  </div>
                </div>
              </div>

              {/* Client Info Display */}
              <div className="bg-gradient-to-br from-blue-50 via-indigo-50 to-blue-50 p-5 rounded-xl border-2 border-blue-300 shadow-sm">
                <div className="flex items-center gap-2 mb-3">
                  <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center">
                    <User className="w-5 h-5 text-white" />
                  </div>
                  <h3 className="font-bold text-gray-900 text-base">Client</h3>
                </div>
                {selectedOrder.client && (
                  <div className="p-4 bg-white rounded-lg border-2 border-blue-200 shadow-sm">
                    <div className="space-y-2">
                      <div className="flex items-center gap-2 pb-2 border-b border-blue-100">
                        <User className="w-4 h-4 text-blue-600" />
                        <span className="font-bold text-gray-900">
                          {selectedOrder.client.firstName} {selectedOrder.client.lastName}
                        </span>
                        {selectedOrder.client.company && (
                          <span className="ml-auto text-xs font-medium text-blue-600 bg-blue-100 px-2 py-1 rounded">
                            {selectedOrder.client.company}
                          </span>
                        )}
                      </div>
                      <div className="grid grid-cols-1 gap-2 text-sm">
                        <div className="flex items-center gap-2">
                          <Mail className="w-4 h-4 text-gray-500" />
                          <span className="text-gray-600">Email:</span>
                          <span className="font-semibold text-gray-900">{selectedOrder.client.email}</span>
                        </div>
                        {selectedOrder.client.phone && (
                          <div className="flex items-center gap-2">
                            <Phone className="w-4 h-4 text-gray-500" />
                            <span className="text-gray-600">Téléphone:</span>
                            <span className="font-semibold text-gray-900">{selectedOrder.client.phone}</span>
                          </div>
                        )}
                        {selectedOrder.client.address && (
                          <div className="flex items-start gap-2">
                            <MapPin className="w-4 h-4 text-gray-500 mt-0.5" />
                            <span className="text-gray-600">Adresse:</span>
                            <span className="font-semibold text-gray-900 flex-1">{selectedOrder.client.address}</span>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                )}
              </div>

              {/* Order Status */}
              <div className="bg-gradient-to-br from-purple-50 via-pink-50 to-purple-50 p-5 rounded-xl border-2 border-purple-300 shadow-sm">
                <div className="flex items-center gap-2 mb-4">
                  <div className="w-8 h-8 bg-purple-600 rounded-lg flex items-center justify-center">
                    <AlertCircle className="w-5 h-5 text-white" />
                  </div>
                  <h3 className="font-bold text-gray-900 text-base">Statut de la commande</h3>
                </div>
                <select
                  value={editForm.status}
                  onChange={(e) => handleEditFormChange('status', e.target.value)}
                  className="w-full px-4 py-2.5 bg-white border-2 border-purple-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-purple-600 focus:ring-4 focus:ring-purple-100 transition-all"
                >
                  <option value="PENDING">⏳ En attente</option>
                  <option value="CONFIRMED">✅ Confirmée</option>
                  <option value="DELIVERED">🚚 Livrée</option>
                  <option value="INVOICED">💳 Facturée</option>
                  <option value="CANCELED">❌ Annulée</option>
                </select>
              </div>

              {/* Order Items */}
              <div className="bg-gradient-to-br from-green-50 via-emerald-50 to-green-50 p-5 rounded-xl border-2 border-green-300 shadow-sm">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 bg-green-600 rounded-lg flex items-center justify-center">
                      <Package className="w-5 h-5 text-white" />
                    </div>
                    <h3 className="font-bold text-gray-900 text-base">Articles de la commande</h3>
                  </div>
                  <button
                    onClick={handleAddItemToEdit}
                    className="flex items-center gap-1.5 px-3 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-semibold rounded-lg transition-all shadow-sm hover:shadow-md"
                  >
                    <Plus className="w-4 h-4" />
                    Ajouter
                  </button>
                </div>

                {editForm.orderItems.length === 0 ? (
                  <div className="text-center py-10 bg-white rounded-xl border-2 border-dashed border-green-300">
                    <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-3">
                      <Package className="w-8 h-8 text-green-600" />
                    </div>
                    <p className="text-gray-700 font-semibold text-sm mb-1">Aucun article ajouté</p>
                    <p className="text-gray-500 text-xs">Cliquez sur "Ajouter" pour commencer</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {editForm.orderItems.map((item, index) => {
                      const selectedProduct = products.find(p => p.id === parseInt(item.productId));
                      return (
                        <div key={index} className="p-4 bg-white rounded-xl border-2 border-green-200 shadow-md hover:shadow-lg transition-shadow">
                          <div className="grid grid-cols-1 md:grid-cols-12 gap-3 items-end">
                            <div className="md:col-span-5">
                              <label className="block text-xs font-bold text-gray-700 mb-2">
                                Produit <span className="text-red-600">*</span>
                              </label>
                              <select
                                value={item.productId}
                                onChange={(e) => handleEditItemChange(index, 'productId', e.target.value)}
                                className="w-full px-3 py-2 bg-white border-2 border-green-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-green-600 focus:ring-4 focus:ring-green-100 transition-all"
                              >
                                <option value="">-- Sélectionner --</option>
                                {products.map((product) => (
                                  <option key={product.id} value={product.id}>
                                    {product.name} • {product.sellingPrice.toFixed(2)} €
                                  </option>
                                ))}
                              </select>
                            </div>
                            <div className="md:col-span-3">
                              <label className="block text-xs font-bold text-gray-700 mb-2">Prix unitaire (€)</label>
                              <input
                                type="number"
                                step="0.01"
                                value={item.unitPrice}
                                onChange={(e) => handleEditItemChange(index, 'unitPrice', parseFloat(e.target.value) || 0)}
                                className="w-full px-3 py-2 bg-white border-2 border-green-300 rounded-lg text-sm font-semibold text-gray-900 focus:outline-none focus:border-green-600 focus:ring-4 focus:ring-green-100 transition-all"
                              />
                            </div>
                            <div className="md:col-span-2">
                              <label className="block text-xs font-bold text-gray-700 mb-2">Quantité</label>
                              <input
                                type="number"
                                min="1"
                                value={item.quantity}
                                onChange={(e) => handleEditItemChange(index, 'quantity', parseInt(e.target.value) || 1)}
                                className="w-full px-3 py-2 bg-white border-2 border-green-300 rounded-lg text-sm font-semibold text-gray-900 focus:outline-none focus:border-green-600 focus:ring-4 focus:ring-green-100 transition-all"
                              />
                            </div>
                            <div className="md:col-span-2">
                              <button
                                onClick={() => handleRemoveItemFromEdit(index)}
                                className="w-full bg-red-600 hover:bg-red-700 text-white px-3 py-2 rounded-lg transition-all text-xs font-bold flex items-center justify-center gap-1.5 shadow-sm hover:shadow-md"
                                title="Supprimer l'article"
                              >
                                <Trash2 className="w-4 h-4" />
                                Retirer
                              </button>
                            </div>
                          </div>
                          <div className="mt-3 pt-3 border-t-2 border-green-200 flex justify-between items-center">
                            {selectedProduct && (
                              <div className="flex items-center gap-2 bg-green-50 px-3 py-1.5 rounded-lg border border-green-200">
                                <Package className="w-4 h-4 text-green-700" />
                                <span className="text-xs font-semibold text-gray-700">Stock:</span>
                                <span className="text-xs font-bold text-green-700">{selectedProduct.stockQuantity} {selectedProduct.unit}</span>
                              </div>
                            )}
                            <div className="ml-auto bg-gradient-to-r from-green-100 to-emerald-100 px-4 py-1.5 rounded-lg border-2 border-green-400">
                              <span className="text-xs font-semibold text-gray-700">Sous-total: </span>
                              <span className="text-base font-black text-green-700">
                                {(item.unitPrice * item.quantity).toFixed(2)} €
                              </span>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Total */}
              <div className="bg-gradient-to-br from-amber-50 via-yellow-50 to-amber-50 p-6 rounded-xl border-2 border-amber-400 shadow-lg">
                <div className="flex justify-between items-center">
                  <div className="flex items-center gap-2">
                    <div className="w-10 h-10 bg-amber-500 rounded-lg flex items-center justify-center">
                      <DollarSign className="w-6 h-6 text-white" />
                    </div>
                    <span className="text-lg font-bold text-gray-900">Montant total</span>
                  </div>
                  <div className="text-right">
                    <span className="text-4xl font-black text-amber-600">
                      {editForm.totalAmount.toFixed(2)} €
                    </span>
                  </div>
                </div>
              </div>

              {/* Actions */}
              <div className="flex gap-3 justify-end pt-2">
                <button
                  onClick={() => setShowEditModal(false)}
                  className="px-6 py-2.5 border-2 border-gray-300 text-gray-700 bg-white hover:bg-gray-100 rounded-lg font-semibold transition-all shadow-sm hover:shadow-md"
                >
                  Annuler
                </button>
                <button
                  onClick={handleUpdateOrder}
                  className="px-6 py-2.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white rounded-lg font-semibold transition-all flex items-center gap-2 shadow-md hover:shadow-lg"
                >
                  <CheckCircle className="w-5 h-5" />
                  Enregistrer les modifications
                </button>
              </div>
            </div>
          </Modal>
        )}
      </AnimatePresence>

      {/* Create Order Modal */}
      <AnimatePresence>
        {showCreateModal && (
          <Modal
            isOpen={showCreateModal}
            onClose={() => setShowCreateModal(false)}
            title="Créer une nouvelle commande"
            size="fullscreen"
          >
            <div className="space-y-5">
              {/* Client Selection */}
              <div className="bg-gradient-to-br from-blue-50 via-indigo-50 to-blue-50 p-5 rounded-xl border-2 border-blue-300 shadow-sm">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center">
                      <User className="w-5 h-5 text-white" />
                    </div>
                    <h3 className="font-bold text-gray-900 text-base">Client</h3>
                  </div>
                  <button
                    onClick={handleCreateClient}
                    className="flex items-center gap-1.5 px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-lg transition-all shadow-sm hover:shadow-md"
                  >
                    <Plus className="w-4 h-4" />
                    Nouveau
                  </button>
                </div>
                <div>
                  <label className="block text-sm font-semibold text-gray-700 mb-2">
                    Sélectionner un client <span className="text-red-600">*</span>
                  </label>
                  <select
                    value={createForm.clientId}
                    onChange={(e) => handleCreateFormChange('clientId', e.target.value)}
                    className="w-full px-4 py-2.5 bg-white border-2 border-blue-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100 transition-all"
                  >
                    <option value="">-- Sélectionner un client --</option>
                    {clients.map((client) => (
                      <option key={client.id} value={client.id}>
                        {client.firstName} {client.lastName} • {client.email}
                        {client.company && ` • ${client.company}`}
                      </option>
                    ))}
                  </select>
                  {createForm.clientId && (
                    <div className="mt-3 p-4 bg-white rounded-lg border-2 border-blue-200 shadow-sm">
                      {(() => {
                        const selectedClient = clients.find(c => c.id === parseInt(createForm.clientId));
                        return selectedClient ? (
                          <div className="space-y-2">
                            <div className="flex items-center gap-2 pb-2 border-b border-blue-100">
                              <User className="w-4 h-4 text-blue-600" />
                              <span className="font-bold text-gray-900">
                                {selectedClient.firstName} {selectedClient.lastName}
                              </span>
                              {selectedClient.company && (
                                <span className="ml-auto text-xs font-medium text-blue-600 bg-blue-100 px-2 py-1 rounded">
                                  {selectedClient.company}
                                </span>
                              )}
                            </div>
                            <div className="grid grid-cols-1 gap-2 text-sm">
                              <div className="flex items-center gap-2">
                                <Mail className="w-4 h-4 text-gray-500" />
                                <span className="text-gray-600">Email:</span>
                                <span className="font-semibold text-gray-900">{selectedClient.email}</span>
                              </div>
                              {selectedClient.phone && (
                                <div className="flex items-center gap-2">
                                  <Phone className="w-4 h-4 text-gray-500" />
                                  <span className="text-gray-600">Téléphone:</span>
                                  <span className="font-semibold text-gray-900">{selectedClient.phone}</span>
                                </div>
                              )}
                              {selectedClient.address && (
                                <div className="flex items-start gap-2">
                                  <MapPin className="w-4 h-4 text-gray-500 mt-0.5" />
                                  <span className="text-gray-600">Adresse:</span>
                                  <span className="font-semibold text-gray-900 flex-1">{selectedClient.address}</span>
                                </div>
                              )}
                            </div>
                          </div>
                        ) : null;
                      })()}
                    </div>
                  )}
                </div>
              </div>

              {/* Order Status */}
              <div className="bg-gradient-to-br from-purple-50 via-pink-50 to-purple-50 p-5 rounded-xl border-2 border-purple-300 shadow-sm">
                <div className="flex items-center gap-2 mb-4">
                  <div className="w-8 h-8 bg-purple-600 rounded-lg flex items-center justify-center">
                    <AlertCircle className="w-5 h-5 text-white" />
                  </div>
                  <h3 className="font-bold text-gray-900 text-base">Statut de la commande</h3>
                </div>
                <select
                  value={createForm.status}
                  onChange={(e) => handleCreateFormChange('status', e.target.value)}
                  className="w-full px-4 py-2.5 bg-white border-2 border-purple-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-purple-600 focus:ring-4 focus:ring-purple-100 transition-all"
                >
                  <option value="PENDING">⏳ En attente</option>
                  <option value="CONFIRMED">✅ Confirmée</option>
                  <option value="DELIVERED">🚚 Livrée</option>
                  <option value="INVOICED">💳 Facturée</option>
                  <option value="CANCELED">❌ Annulée</option>
                </select>
              </div>

              {/* Order Items */}
              <div className="bg-gradient-to-br from-green-50 via-emerald-50 to-green-50 p-5 rounded-xl border-2 border-green-300 shadow-sm">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 bg-green-600 rounded-lg flex items-center justify-center">
                      <Package className="w-5 h-5 text-white" />
                    </div>
                    <h3 className="font-bold text-gray-900 text-base">Articles de la commande</h3>
                  </div>
                  <button
                    onClick={handleAddItemToCreate}
                    className="flex items-center gap-1.5 px-3 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-semibold rounded-lg transition-all shadow-sm hover:shadow-md"
                  >
                    <Plus className="w-4 h-4" />
                    Ajouter
                  </button>
                </div>

                {createForm.orderItems.length === 0 ? (
                  <div className="text-center py-10 bg-white rounded-xl border-2 border-dashed border-green-300">
                    <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-3">
                      <Package className="w-8 h-8 text-green-600" />
                    </div>
                    <p className="text-gray-700 font-semibold text-sm mb-1">Aucun article ajouté</p>
                    <p className="text-gray-500 text-xs">Cliquez sur "Ajouter" pour commencer</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {createForm.orderItems.map((item, index) => {
                      const selectedProduct = products.find(p => p.id === parseInt(item.productId));
                      return (
                        <div key={index} className="p-4 bg-white rounded-xl border-2 border-green-200 shadow-md hover:shadow-lg transition-shadow">
                          <div className="grid grid-cols-1 md:grid-cols-12 gap-3 items-end">
                            <div className="md:col-span-5">
                              <label className="block text-xs font-bold text-gray-700 mb-2">
                                Produit <span className="text-red-600">*</span>
                              </label>
                              <select
                                value={item.productId}
                                onChange={(e) => handleCreateItemChange(index, 'productId', e.target.value)}
                                className="w-full px-3 py-2 bg-white border-2 border-green-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-green-600 focus:ring-4 focus:ring-green-100 transition-all"
                              >
                                <option value="">-- Sélectionner --</option>
                                {products.map((product) => (
                                  <option key={product.id} value={product.id}>
                                    {product.name} • {product.sellingPrice.toFixed(2)} €
                                  </option>
                                ))}
                              </select>
                            </div>
                            <div className="md:col-span-3">
                              <label className="block text-xs font-bold text-gray-700 mb-2">Prix unitaire (€)</label>
                              <input
                                type="number"
                                step="0.01"
                                value={item.unitPrice}
                                onChange={(e) => handleCreateItemChange(index, 'unitPrice', parseFloat(e.target.value) || 0)}
                                className="w-full px-3 py-2 bg-white border-2 border-green-300 rounded-lg text-sm font-semibold text-gray-900 focus:outline-none focus:border-green-600 focus:ring-4 focus:ring-green-100 transition-all"
                              />
                            </div>
                            <div className="md:col-span-2">
                              <label className="block text-xs font-bold text-gray-700 mb-2">Quantité</label>
                              <input
                                type="number"
                                min="1"
                                value={item.quantity}
                                onChange={(e) => handleCreateItemChange(index, 'quantity', parseInt(e.target.value) || 1)}
                                className="w-full px-3 py-2 bg-white border-2 border-green-300 rounded-lg text-sm font-semibold text-gray-900 focus:outline-none focus:border-green-600 focus:ring-4 focus:ring-green-100 transition-all"
                              />
                            </div>
                            <div className="md:col-span-2">
                              <button
                                onClick={() => handleRemoveItemFromCreate(index)}
                                className="w-full bg-red-600 hover:bg-red-700 text-white px-3 py-2 rounded-lg transition-all text-xs font-bold flex items-center justify-center gap-1.5 shadow-sm hover:shadow-md"
                                title="Supprimer l'article"
                              >
                                <Trash2 className="w-4 h-4" />
                                Retirer
                              </button>
                            </div>
                          </div>
                          <div className="mt-3 pt-3 border-t-2 border-green-200 flex justify-between items-center">
                            {selectedProduct && (
                              <div className="flex items-center gap-2 bg-green-50 px-3 py-1.5 rounded-lg border border-green-200">
                                <Package className="w-4 h-4 text-green-700" />
                                <span className="text-xs font-semibold text-gray-700">Stock:</span>
                                <span className="text-xs font-bold text-green-700">{selectedProduct.stockQuantity} {selectedProduct.unit}</span>
                              </div>
                            )}
                            <div className="ml-auto bg-gradient-to-r from-green-100 to-emerald-100 px-4 py-1.5 rounded-lg border-2 border-green-400">
                              <span className="text-xs font-semibold text-gray-700">Sous-total: </span>
                              <span className="text-base font-black text-green-700">
                                {(item.unitPrice * item.quantity).toFixed(2)} €
                              </span>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Total */}
              <div className="bg-gradient-to-br from-amber-50 via-yellow-50 to-amber-50 p-6 rounded-xl border-2 border-amber-400 shadow-lg">
                <div className="flex justify-between items-center">
                  <div className="flex items-center gap-2">
                    <div className="w-10 h-10 bg-amber-500 rounded-lg flex items-center justify-center">
                      <DollarSign className="w-6 h-6 text-white" />
                    </div>
                    <span className="text-lg font-bold text-gray-900">Montant total</span>
                  </div>
                  <div className="text-right">
                    <span className="text-4xl font-black text-amber-600">
                      {createForm.totalAmount.toFixed(2)} €
                    </span>
                  </div>
                </div>
              </div>

              {/* Actions */}
              <div className="flex gap-3 justify-end pt-2">
                <button
                  onClick={() => setShowCreateModal(false)}
                  className="px-6 py-2.5 border-2 border-gray-300 text-gray-700 bg-white hover:bg-gray-100 rounded-lg font-semibold transition-all shadow-sm hover:shadow-md"
                >
                  Annuler
                </button>
                <button
                  onClick={handleSubmitCreateOrder}
                  className="px-6 py-2.5 bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-700 hover:to-emerald-700 text-white rounded-lg font-semibold transition-all flex items-center gap-2 shadow-md hover:shadow-lg"
                >
                  <CheckCircle className="w-5 h-5" />
                  Créer la commande
                </button>
              </div>
            </div>
          </Modal>
        )}
      </AnimatePresence>

      {/* Create Client Modal */}
      <AnimatePresence>
        {showCreateClientModal && (
          <Modal
            isOpen={showCreateClientModal}
            onClose={() => setShowCreateClientModal(false)}
            title="Créer un nouveau client"
            size="lg"
          >
            <div className="space-y-5">
              <div className="bg-gradient-to-br from-blue-50 via-indigo-50 to-blue-50 p-5 rounded-xl border-2 border-blue-300 shadow-sm">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-bold text-gray-700 mb-2">
                      Type de client <span className="text-red-600">*</span>
                    </label>
                    <select
                      value={newClientForm.type}
                      onChange={(e) => handleNewClientFormChange('type', e.target.value)}
                      className="w-full px-4 py-2.5 bg-white border-2 border-blue-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100 transition-all"
                    >
                      <option value="PARTICULIER">👤 Particulier</option>
                      <option value="ENTREPRISE">🏢 Entreprise</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-bold text-gray-700 mb-2">
                      Société
                    </label>
                    <input
                      type="text"
                      value={newClientForm.company}
                      onChange={(e) => handleNewClientFormChange('company', e.target.value)}
                      className="w-full px-4 py-2.5 bg-white border-2 border-blue-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100 transition-all"
                      placeholder="Nom de la société (si entreprise)"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-bold text-gray-700 mb-2">
                      Prénom <span className="text-red-600">*</span>
                    </label>
                    <input
                      type="text"
                      value={newClientForm.firstName}
                      onChange={(e) => handleNewClientFormChange('firstName', e.target.value)}
                      className="w-full px-4 py-2.5 bg-white border-2 border-blue-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100 transition-all"
                      placeholder="Prénom"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-bold text-gray-700 mb-2">
                      Nom <span className="text-red-600">*</span>
                    </label>
                    <input
                      type="text"
                      value={newClientForm.lastName}
                      onChange={(e) => handleNewClientFormChange('lastName', e.target.value)}
                      className="w-full px-4 py-2.5 bg-white border-2 border-blue-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100 transition-all"
                      placeholder="Nom"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-bold text-gray-700 mb-2 flex items-center gap-1">
                      <Phone className="w-4 h-4 text-blue-600" />
                      Téléphone <span className="text-red-600">*</span>
                    </label>
                    <input
                      type="tel"
                      value={newClientForm.phone}
                      onChange={(e) => handleNewClientFormChange('phone', e.target.value)}
                      className="w-full px-4 py-2.5 bg-white border-2 border-blue-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100 transition-all"
                      placeholder="0612345678"
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-bold text-gray-700 mb-2 flex items-center gap-1">
                      <Mail className="w-4 h-4 text-blue-600" />
                      Email
                    </label>
                    <input
                      type="email"
                      value={newClientForm.email}
                      onChange={(e) => handleNewClientFormChange('email', e.target.value)}
                      className="w-full px-4 py-2.5 bg-white border-2 border-blue-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100 transition-all"
                      placeholder="email@exemple.com"
                    />
                  </div>
                  <div className="md:col-span-2">
                    <label className="block text-sm font-bold text-gray-700 mb-2 flex items-center gap-1">
                      <MapPin className="w-4 h-4 text-blue-600" />
                      Adresse
                    </label>
                    <textarea
                      value={newClientForm.address}
                      onChange={(e) => handleNewClientFormChange('address', e.target.value)}
                      className="w-full px-4 py-2.5 bg-white border-2 border-blue-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100 transition-all resize-none"
                      placeholder="Adresse complète"
                      rows="2"
                    />
                  </div>
                </div>
              </div>

              {/* Actions */}
              <div className="flex gap-3 justify-end pt-2">
                <button
                  onClick={() => setShowCreateClientModal(false)}
                  className="px-6 py-2.5 border-2 border-gray-300 text-gray-700 bg-white hover:bg-gray-100 rounded-lg font-semibold transition-all shadow-sm hover:shadow-md"
                >
                  Annuler
                </button>
                <button
                  onClick={handleSubmitNewClient}
                  className="px-6 py-2.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white rounded-lg font-semibold transition-all flex items-center gap-2 shadow-md hover:shadow-lg"
                >
                  <CheckCircle className="w-5 h-5" />
                  Créer le client
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
        onConfirm={confirmCreateOrder}
        title="Confirmer la création"
        message={`Voulez-vous vraiment créer cette commande avec ${createForm.orderItems.length} article(s) ?`}
        type="info"
      />
    </div>
  );
};

export default Orders;
