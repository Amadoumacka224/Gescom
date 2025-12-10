import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Plus,
  Minus,
  RefreshCw,
  AlertTriangle,
  TrendingUp,
  TrendingDown,
  Package,
  Calendar,
  User,
  FileText,
  DollarSign,
  Edit2,
  Trash2,
  Search,
  Filter,
  Eye,
  BarChart3,
  List,
  Box,
  X
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import api from '../services/api';
import Modal from '../components/Modal';
import Pagination from '../components/Pagination';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import Button from '../components/Button';

const Stock = () => {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('stock'); // 'stock', 'movements', 'details'
  const [movements, setMovements] = useState([]);
  const [products, setProducts] = useState([]);
  const [statistics, setStatistics] = useState({
    totalProducts: 0,
    lowStockCount: 0,
    outOfStockCount: 0,
    totalStockValue: 0,
    totalStockQuantity: 0
  });
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [modalType, setModalType] = useState('add'); // add, remove, adjust, damage
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [selectedProductForDetails, setSelectedProductForDetails] = useState(null);
  const [productMovements, setProductMovements] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [formData, setFormData] = useState({
    productId: '',
    quantity: '',
    newQuantity: '',
    unitCost: '',
    reason: '',
    reference: ''
  });
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState('ALL');
  const [stockFilter, setStockFilter] = useState('ALL'); // ALL, LOW, OUT

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [movementsRes, productsRes, statsRes] = await Promise.all([
        api.get('/stock/movements'),
        api.get('/products'),
        api.get('/stock/statistics')
      ]);

      setMovements(movementsRes.data);
      setProducts(productsRes.data);
      setStatistics(statsRes.data);
    } catch (error) {
      console.error('Error fetching data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenModal = (type, product = null) => {
    // Force close modal first to ensure state updates properly
    setShowModal(false);

    // Use setTimeout to ensure modal closes before reopening with new data
    setTimeout(() => {
      setModalType(type);
      setSelectedProduct(product);
      setFormData({
        productId: product?.id || '',
        quantity: '',
        newQuantity: product?.stockQuantity || '',
        unitCost: '',
        reason: '',
        reference: ''
      });
      setShowModal(true);
    }, 0);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {
      let endpoint = '';
      let payload = {};

      switch (modalType) {
        case 'add':
          endpoint = '/stock/add';
          payload = {
            productId: formData.productId,
            quantity: parseInt(formData.quantity),
            unitCost: formData.unitCost ? parseFloat(formData.unitCost) : null,
            reason: formData.reason,
            reference: formData.reference
          };
          break;
        case 'remove':
          endpoint = '/stock/remove';
          payload = {
            productId: formData.productId,
            quantity: parseInt(formData.quantity),
            reason: formData.reason,
            reference: formData.reference
          };
          break;
        case 'adjust':
          endpoint = '/stock/adjust';
          payload = {
            productId: formData.productId,
            newQuantity: parseInt(formData.newQuantity),
            reason: formData.reason
          };
          break;
        case 'damage':
          endpoint = '/stock/damage';
          payload = {
            productId: formData.productId,
            quantity: parseInt(formData.quantity),
            reason: formData.reason
          };
          break;
        default:
          throw new Error('Invalid modal type');
      }

      await api.post(endpoint, payload);
      setShowModal(false);
      fetchData();
      alert('Opération effectuée avec succès!');
    } catch (error) {
      console.error('Error:', error);
      alert(error.response?.data || 'Erreur lors de l\'opération');
    }
  };

  const getMovementTypeInfo = (type) => {
    const types = {
      STOCK_IN: { icon: TrendingUp, class: 'text-green-600 bg-green-100', text: 'Entrée' },
      STOCK_OUT: { icon: TrendingDown, class: 'text-red-600 bg-red-100', text: 'Sortie' },
      ADJUSTMENT: { icon: Edit2, class: 'text-blue-600 bg-blue-100', text: 'Ajustement' },
      RETURN: { icon: RefreshCw, class: 'text-purple-600 bg-purple-100', text: 'Retour' },
      DAMAGE: { icon: AlertTriangle, class: 'text-orange-600 bg-orange-100', text: 'Dommage' },
      TRANSFER: { icon: Package, class: 'text-indigo-600 bg-indigo-100', text: 'Transfert' }
    };
    return types[type] || types.STOCK_IN;
  };

  const fetchProductMovements = async (productId) => {
    try {
      const response = await api.get(`/stock/movements/product/${productId}`);
      setProductMovements(response.data);
    } catch (error) {
      console.error('Error fetching product movements:', error);
      setProductMovements([]);
    }
  };

  const handleViewDetails = (product) => {
    setSelectedProductForDetails(product);
    fetchProductMovements(product.id);
    setActiveTab('details');
  };

  const filteredMovements = movements.filter(movement => {
    const matchesSearch = movement.product?.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          movement.reference?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesFilter = filterType === 'ALL' || movement.type === filterType;
    return matchesSearch && matchesFilter;
  });

  const filteredProducts = products.filter(product => {
    const matchesSearch = product.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          product.reference?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          product.barcode?.toLowerCase().includes(searchTerm.toLowerCase());

    let matchesStockFilter = true;
    if (stockFilter === 'LOW') {
      matchesStockFilter = product.stockQuantity > 0 && product.stockQuantity <= product.minStockAlert;
    } else if (stockFilter === 'OUT') {
      matchesStockFilter = product.stockQuantity === 0;
    }

    return matchesSearch && matchesStockFilter;
  });

  // Pagination
  const totalPages = Math.ceil(filteredMovements.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const paginatedMovements = filteredMovements.slice(startIndex, endIndex);

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const handleItemsPerPageChange = (newItemsPerPage) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1);
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString('fr-FR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Gestion de Stock</h1>
          <p className="text-gray-600 mt-1">Consultez et gérez votre inventaire</p>
        </div>
        <div className="flex gap-3">
          <Button
            variant="secondary"
            onClick={fetchData}
            icon={RefreshCw}
          >
            Actualiser
          </Button>
          <Button
            variant="primary"
            onClick={() => handleOpenModal('add')}
            icon={Plus}
          >
            Ajouter du stock
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <div className="card p-0">
        <div className="flex border-b border-gray-200">
          <button
            onClick={() => setActiveTab('stock')}
            className={`flex items-center gap-2 px-6 py-4 font-medium transition-colors ${
              activeTab === 'stock'
                ? 'border-b-2 border-primary-600 text-primary-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            <Box className="w-5 h-5" />
            Stock Actuel
          </button>
          <button
            onClick={() => setActiveTab('movements')}
            className={`flex items-center gap-2 px-6 py-4 font-medium transition-colors ${
              activeTab === 'movements'
                ? 'border-b-2 border-primary-600 text-primary-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            <List className="w-5 h-5" />
            Mouvements
          </button>
          {selectedProductForDetails && (
            <button
              onClick={() => setActiveTab('details')}
              className={`flex items-center gap-2 px-6 py-4 font-medium transition-colors ${
                activeTab === 'details'
                  ? 'border-b-2 border-primary-600 text-primary-600'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              <BarChart3 className="w-5 h-5" />
              Détail - {selectedProductForDetails.name}
            </button>
          )}
        </div>
      </div>

      {/* Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <div className="card bg-gradient-to-br from-blue-50 to-cyan-50 border-blue-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-blue-600 font-medium">Stock total</p>
              <p className="text-3xl font-bold text-blue-700">{statistics.totalStockQuantity || 0}</p>
              <p className="text-xs text-blue-600 mt-1">{statistics.totalProducts || 0} produits</p>
            </div>
            <Package className="w-12 h-12 text-blue-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-green-50 to-emerald-50 border-green-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-green-600 font-medium">Valeur du stock</p>
              <p className="text-3xl font-bold text-green-700">
                {(statistics.totalStockValue || 0).toFixed(2)}€
              </p>
            </div>
            <DollarSign className="w-12 h-12 text-green-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-orange-50 to-red-50 border-orange-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-orange-600 font-medium">Stock faible</p>
              <p className="text-3xl font-bold text-orange-700">{statistics.lowStockCount || 0}</p>
              <p className="text-xs text-orange-600 mt-1">À réapprovisionner</p>
            </div>
            <AlertTriangle className="w-12 h-12 text-orange-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-red-50 to-pink-50 border-red-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-red-600 font-medium">Rupture de stock</p>
              <p className="text-3xl font-bold text-red-700">{statistics.outOfStockCount || 0}</p>
              <p className="text-xs text-red-600 mt-1">Produits épuisés</p>
            </div>
            <AlertTriangle className="w-12 h-12 text-red-600 opacity-50" />
          </div>
        </div>
      </div>

      {/* Alerts for low/out of stock */}
      {(statistics.lowStockCount > 0 || statistics.outOfStockCount > 0) && (
        <div className="card bg-orange-50 border-orange-200">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-orange-600 mt-0.5" />
            <div className="flex-1">
              <h3 className="font-semibold text-orange-900">Alertes de stock</h3>
              {statistics.outOfStockCount > 0 && (
                <p className="text-sm text-orange-700 mt-1">
                  {statistics.outOfStockCount} produit(s) en rupture de stock
                </p>
              )}
              {statistics.lowStockCount > 0 && (
                <p className="text-sm text-orange-700 mt-1">
                  {statistics.lowStockCount} produit(s) avec un stock faible
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Stock Actuel Tab */}
      {activeTab === 'stock' && (
        <>
          {/* Filters */}
          <div className="card">
            <div className="flex flex-col md:flex-row gap-4">
              <div className="flex-1 relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
                <input
                  type="text"
                  placeholder="Rechercher un produit..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="input pl-10 w-full"
                />
              </div>
              <div className="flex items-center gap-2">
                <Filter className="w-5 h-5 text-gray-400" />
                <select
                  value={stockFilter}
                  onChange={(e) => setStockFilter(e.target.value)}
                  className="input"
                >
                  <option value="ALL">Tous les produits</option>
                  <option value="LOW">Stock faible</option>
                  <option value="OUT">Rupture de stock</option>
                </select>
              </div>
            </div>
          </div>

          {/* Products Stock Table */}
          <div className="card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Produit</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Catégorie</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Stock Actuel</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Seuil Min</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Statut</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Valeur</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {filteredProducts.map((product) => {
                    const stockValue = (product.stockQuantity || 0) * (product.purchasePrice || 0);
                    const isLowStock = product.stockQuantity > 0 && product.stockQuantity <= product.minStockAlert;
                    const isOutOfStock = product.stockQuantity === 0;

                    return (
                      <motion.tr
                        key={product.id}
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        className="hover:bg-gray-50"
                      >
                        <td className="px-6 py-4">
                          <div>
                            <p className="font-medium text-gray-900">{product.name}</p>
                            <p className="text-xs text-gray-500">{product.reference || product.barcode}</p>
                          </div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                          {product.category?.name || '-'}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className="text-lg font-bold text-gray-900">{product.stockQuantity || 0}</span>
                          <span className="text-sm text-gray-500 ml-1">{product.unit}</span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                          {product.minStockAlert || 0}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          {isOutOfStock ? (
                            <span className="px-3 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800">
                              Rupture
                            </span>
                          ) : isLowStock ? (
                            <span className="px-3 py-1 rounded-full text-xs font-medium bg-orange-100 text-orange-800">
                              Stock faible
                            </span>
                          ) : (
                            <span className="px-3 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">
                              Disponible
                            </span>
                          )}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-gray-900">
                          {stockValue.toFixed(2)} €
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleViewDetails(product);
                            }}
                            className="inline-flex items-center gap-2 px-4 py-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors font-medium"
                            title="Voir les détails"
                          >
                            <Eye className="w-4 h-4" />
                            Détails
                          </button>
                        </td>
                      </motion.tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {/* Movements Tab */}
      {activeTab === 'movements' && (
        <>
          {/* Quick Actions */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={() => handleOpenModal('add')}
          className="card hover:shadow-lg transition-shadow text-left"
        >
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-green-100 flex items-center justify-center">
              <Plus className="w-5 h-5 text-green-600" />
            </div>
            <div>
              <p className="font-semibold text-gray-900">Ajouter du stock</p>
              <p className="text-xs text-gray-600">Entrée de marchandises</p>
            </div>
          </div>
        </motion.button>

        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={() => handleOpenModal('remove')}
          className="card hover:shadow-lg transition-shadow text-left"
        >
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-red-100 flex items-center justify-center">
              <Minus className="w-5 h-5 text-red-600" />
            </div>
            <div>
              <p className="font-semibold text-gray-900">Retirer du stock</p>
              <p className="text-xs text-gray-600">Sortie de marchandises</p>
            </div>
          </div>
        </motion.button>

        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={() => handleOpenModal('adjust')}
          className="card hover:shadow-lg transition-shadow text-left"
        >
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-blue-100 flex items-center justify-center">
              <Edit2 className="w-5 h-5 text-blue-600" />
            </div>
            <div>
              <p className="font-semibold text-gray-900">Ajuster le stock</p>
              <p className="text-xs text-gray-600">Correction d'inventaire</p>
            </div>
          </div>
        </motion.button>

        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={() => handleOpenModal('damage')}
          className="card hover:shadow-lg transition-shadow text-left"
        >
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-orange-100 flex items-center justify-center">
              <AlertTriangle className="w-5 h-5 text-orange-600" />
            </div>
            <div>
              <p className="font-semibold text-gray-900">Déclarer un dommage</p>
              <p className="text-xs text-gray-600">Produit endommagé</p>
            </div>
          </div>
        </motion.button>
      </div>

      {/* Filters and Search */}
      <div className="card">
        <div className="flex flex-col md:flex-row gap-4">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
            <input
              type="text"
              placeholder="Rechercher par produit ou référence..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="input pl-10 w-full"
            />
          </div>
          <div className="flex items-center gap-2">
            <Filter className="w-5 h-5 text-gray-400" />
            <select
              value={filterType}
              onChange={(e) => setFilterType(e.target.value)}
              className="input"
            >
              <option value="ALL">Tous les mouvements</option>
              <option value="STOCK_IN">Entrées</option>
              <option value="STOCK_OUT">Sorties</option>
              <option value="ADJUSTMENT">Ajustements</option>
              <option value="DAMAGE">Dommages</option>
              <option value="RETURN">Retours</option>
              <option value="TRANSFER">Transferts</option>
            </select>
          </div>
        </div>
      </div>

      {/* Movements Table */}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Produit</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Quantité</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Stock avant</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Stock après</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Référence</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Raison</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {paginatedMovements.map((movement) => {
                const typeInfo = getMovementTypeInfo(movement.type);
                const Icon = typeInfo.icon;
                return (
                  <motion.tr
                    key={movement.id}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="hover:bg-gray-50"
                  >
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className={`inline-flex items-center gap-2 px-3 py-1 rounded-full ${typeInfo.class}`}>
                        <Icon className="w-4 h-4" />
                        <span className="text-sm font-medium">{typeInfo.text}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div>
                        <p className="font-medium text-gray-900">{movement.product?.name}</p>
                        <p className="text-xs text-gray-500">{movement.product?.reference}</p>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="font-semibold text-gray-900">{movement.quantity}</span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                      {movement.previousStock}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-gray-900">
                      {movement.newStock}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                      {movement.reference || '-'}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                      {formatDate(movement.createdAt)}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-600">
                      <div className="max-w-xs truncate" title={movement.reason}>
                        {movement.reason || '-'}
                      </div>
                    </td>
                  </motion.tr>
                );
              })}
            </tbody>
          </table>

          {/* Pagination */}
          {filteredMovements.length > 0 && (
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalItems={filteredMovements.length}
              itemsPerPage={itemsPerPage}
              onPageChange={handlePageChange}
              onItemsPerPageChange={handleItemsPerPageChange}
            />
          )}
        </div>
      </div>

      {/* Modal */}
      <AnimatePresence>
        {showModal && (
          <Modal
            isOpen={showModal}
            onClose={() => setShowModal(false)}
            title={
              modalType === 'add' ? 'Ajouter du stock' :
              modalType === 'remove' ? 'Retirer du stock' :
              modalType === 'adjust' ? 'Ajuster le stock' :
              'Déclarer un dommage'
            }
          >
            <form onSubmit={handleSubmit} className="space-y-4">
              {selectedProduct ? (
                <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm text-gray-600 font-medium">Produit sélectionné</p>
                      <p className="text-lg font-bold text-gray-900 mt-1">{selectedProduct.name}</p>
                      <p className="text-sm text-gray-600">Stock actuel: <span className="font-semibold text-primary-600">{selectedProduct.stockQuantity}</span> {selectedProduct.unit}</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedProduct(null);
                        setFormData({ ...formData, productId: '' });
                      }}
                      className="text-gray-400 hover:text-gray-600"
                    >
                      <X className="w-5 h-5" />
                    </button>
                  </div>
                </div>
              ) : (
                <FormSelect
                  label="Produit"
                  value={formData.productId}
                  onChange={(e) => {
                    const productId = e.target.value;
                    const product = products.find(p => p.id === parseInt(productId));
                    setFormData({
                      ...formData,
                      productId,
                      newQuantity: product?.stockQuantity || 0
                    });
                  }}
                  options={[
                    { value: '', label: 'Sélectionner un produit' },
                    ...products.map(p => ({
                      value: p.id,
                      label: `${p.name} (Stock: ${p.stockQuantity})`
                    }))
                  ]}
                  required
                />
              )}

              {modalType === 'adjust' ? (
                <FormInput
                  label="Nouvelle quantité"
                  type="number"
                  value={formData.newQuantity}
                  onChange={(e) => setFormData({ ...formData, newQuantity: e.target.value })}
                  placeholder="Nouvelle quantité en stock"
                  required
                />
              ) : (
                <FormInput
                  label="Quantité"
                  type="number"
                  value={formData.quantity}
                  onChange={(e) => setFormData({ ...formData, quantity: e.target.value })}
                  placeholder="Quantité"
                  required
                />
              )}

              {modalType === 'add' && (
                <>
                  <FormInput
                    label="Coût unitaire"
                    type="number"
                    step="0.01"
                    value={formData.unitCost}
                    onChange={(e) => setFormData({ ...formData, unitCost: e.target.value })}
                    placeholder="Prix d'achat unitaire"
                  />
                  <FormInput
                    label="Référence"
                    value={formData.reference}
                    onChange={(e) => setFormData({ ...formData, reference: e.target.value })}
                    placeholder="Bon de commande, facture, etc."
                  />
                </>
              )}

              {(modalType === 'remove' || modalType === 'damage') && (
                <FormInput
                  label="Référence"
                  value={formData.reference}
                  onChange={(e) => setFormData({ ...formData, reference: e.target.value })}
                  placeholder="N° de sortie, etc."
                />
              )}

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Raison
                </label>
                <textarea
                  value={formData.reason}
                  onChange={(e) => setFormData({ ...formData, reason: e.target.value })}
                  rows="3"
                  className="input w-full"
                  placeholder="Motif de l'opération..."
                />
              </div>

              <div className="flex gap-3 pt-4">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setShowModal(false)}
                  className="flex-1"
                >
                  Annuler
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  className="flex-1"
                >
                  Confirmer
                </Button>
              </div>
            </form>
          </Modal>
        )}
      </AnimatePresence>
        </>
      )}

      {/* Product Details Tab */}
      {activeTab === 'details' && selectedProductForDetails && (
        <>
          <div className="card">
            <div className="flex items-center justify-between mb-6">
              <div className="flex items-center gap-4">
                <div className="w-16 h-16 bg-gradient-to-br from-primary-500 to-primary-700 rounded-xl flex items-center justify-center">
                  <Package className="w-8 h-8 text-white" />
                </div>
                <div>
                  <h2 className="text-2xl font-bold text-gray-900">{selectedProductForDetails.name}</h2>
                  <p className="text-sm text-gray-600">{selectedProductForDetails.reference || selectedProductForDetails.barcode}</p>
                </div>
              </div>
              <Button
                variant="secondary"
                onClick={() => {
                  setActiveTab('stock');
                  setSelectedProductForDetails(null);
                }}
              >
                Retour au stock
              </Button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                <p className="text-sm text-blue-600 font-medium">Stock Actuel</p>
                <p className="text-3xl font-bold text-blue-700 mt-2">
                  {selectedProductForDetails.stockQuantity || 0}
                </p>
                <p className="text-xs text-blue-600 mt-1">{selectedProductForDetails.unit}</p>
              </div>

              <div className="p-4 bg-orange-50 rounded-lg border border-orange-200">
                <p className="text-sm text-orange-600 font-medium">Seuil Minimum</p>
                <p className="text-3xl font-bold text-orange-700 mt-2">
                  {selectedProductForDetails.minStockAlert || 0}
                </p>
              </div>

              <div className="p-4 bg-green-50 rounded-lg border border-green-200">
                <p className="text-sm text-green-600 font-medium">Prix d'achat</p>
                <p className="text-2xl font-bold text-green-700 mt-2">
                  {(selectedProductForDetails.purchasePrice || 0).toFixed(2)} €
                </p>
              </div>

              <div className="p-4 bg-purple-50 rounded-lg border border-purple-200">
                <p className="text-sm text-purple-600 font-medium">Valeur du stock</p>
                <p className="text-2xl font-bold text-purple-700 mt-2">
                  {((selectedProductForDetails.stockQuantity || 0) * (selectedProductForDetails.purchasePrice || 0)).toFixed(2)} €
                </p>
              </div>
            </div>

          </div>

          {/* Product Movements History */}
          <div className="card overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-200">
              <h3 className="text-lg font-bold text-gray-900">Historique des mouvements</h3>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Quantité</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Stock avant</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Stock après</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Référence</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Raison</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {productMovements.length === 0 ? (
                    <tr>
                      <td colSpan="7" className="px-6 py-12 text-center">
                        <Package className="w-12 h-12 text-gray-300 mx-auto mb-3" />
                        <p className="text-gray-500 font-medium">Aucun mouvement enregistré</p>
                        <p className="text-gray-400 text-sm mt-1">Les mouvements de stock apparaîtront ici</p>
                      </td>
                    </tr>
                  ) : (
                    productMovements.map((movement) => {
                      const typeInfo = getMovementTypeInfo(movement.type);
                      const Icon = typeInfo.icon;
                      return (
                        <motion.tr
                          key={movement.id}
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          className="hover:bg-gray-50"
                        >
                          <td className="px-6 py-4 whitespace-nowrap">
                            <div className={`inline-flex items-center gap-2 px-3 py-1 rounded-full ${typeInfo.class}`}>
                              <Icon className="w-4 h-4" />
                              <span className="text-sm font-medium">{typeInfo.text}</span>
                            </div>
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap">
                            <span className="font-semibold text-gray-900">{movement.quantity}</span>
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                            {movement.previousStock}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-gray-900">
                            {movement.newStock}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                            {movement.reference || '-'}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                            {formatDate(movement.createdAt)}
                          </td>
                          <td className="px-6 py-4 text-sm text-gray-600">
                            <div className="max-w-xs truncate" title={movement.reason}>
                              {movement.reason || '-'}
                            </div>
                          </td>
                        </motion.tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export default Stock;
