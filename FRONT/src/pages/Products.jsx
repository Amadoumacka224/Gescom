import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'react-hot-toast';
import { Plus, Search, Edit, Trash2, Package, AlertTriangle, RefreshCw, FolderTree, Upload, Download, TrendingUp, TrendingDown, Euro, ArrowUpDown, Grid3x3, List, Image as ImageIcon, X, Eye, Barcode, Tag, Calendar, Hash } from 'lucide-react';
import { motion } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import productService from '../services/productService';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Pagination from '../components/Pagination';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import Button from '../components/Button';
import Table from '../components/Table';
import SearchBox from '../components/SearchBox';
import { rankSuggestions } from '../utils/searchSuggestions';
import i18n from '../i18n';

const Products = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  // Le backend restreint déjà create/update/delete/import/export aux ADMIN
  // (cf. ProductController @PreAuthorize("hasRole('ADMIN')")). On masque l'UI
  // pour le CAISSIER afin d'éviter les 403 et de ne pas afficher des actions
  // dont il ne dispose pas.
  const isAdmin = user?.role === 'ADMIN';
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [editingProduct, setEditingProduct] = useState(null);
  const [sortConfig, setSortConfig] = useState({ key: null, direction: 'asc' });
  const [viewMode, setViewMode] = useState(() => {
    // Load view mode from localStorage, default to 'list' if not set
    return localStorage.getItem('productsViewMode') || 'list';
  });
  const [imagePreview, setImagePreview] = useState(null);
  const fileInputRef = useRef(null);
  const [currentPage, setCurrentPage] = useState(1);
  // On affiche le maximum de produits par page par défaut (100, le plus grand pas du sélecteur).
  const [itemsPerPage, setItemsPerPage] = useState(100);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    categoryId: '',
    purchasePrice: '',
    sellingPrice: '',
    stockQuantity: '',
    minStockAlert: '',
    unit: 'PIECE',
    barcode: '',
    imageUrl: '',
    active: true
  });

  useEffect(() => {
    fetchProducts();
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      console.log('Fetching categories from API...');
      const categoriesData = await productService.getCategories();
      console.log('Categories loaded:', categoriesData);
      setCategories(categoriesData || []);
    } catch (error) {
      console.error('Error fetching categories:', error);
      setCategories([]);
    }
  };

  const fetchProducts = async () => {
    setLoading(true);
    try {
      const response = await productService.getAllProducts();
      setProducts(response.data);
    } catch (error) {
      console.error('Error fetching products:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    setLoading(true);

    try {
      // Préparer les données du produit. Le backend (ProductRequest) attend `categoryId` (Long),
      // pas un objet `category` : on envoie donc l'identifiant numérique (ou null si aucune catégorie).
      const productData = {
        ...formData,
        categoryId: formData.categoryId ? parseInt(formData.categoryId) : null
      };
      // On retire l'objet `category` éventuellement hérité du produit en édition (champ inconnu du DTO).
      delete productData.category;

      if (editingProduct) {
        await productService.updateProduct(editingProduct.id, productData);
        toast.success(t('products.updatedSuccess'));
      } else {
        await productService.createProduct(productData);
        toast.success(t('products.createdSuccess'));
      }

      await fetchProducts();
      handleCloseModal();
    } catch (error) {
      console.error('Error saving product:', error);
      console.error('Error response:', error.response);
      const errorMessage = error.response?.data || error.message || 'Erreur lors de l\'enregistrement du produit';
      toast.error(t('common.errorPrefixed', { message: errorMessage }));
    } finally {
      setLoading(false);
    }
  };

  const handleEdit = (product) => {
    setEditingProduct(product);
    setFormData({
      ...product,
      categoryId: product.category?.id || ''
    });
    if (product.imageUrl) {
      setImagePreview(product.imageUrl);
    }
    setShowModal(true);
  };

  const handleViewDetails = (product) => {
    setSelectedProduct(product);
    setShowDetailsModal(true);
  };

  const handleCloseDetails = () => {
    setShowDetailsModal(false);
    setSelectedProduct(null);
  };

  const handleDelete = async (id) => {
    if (window.confirm(t('products.confirmDelete'))) {
      try {
        await productService.deleteProduct(id);
        await fetchProducts();
      } catch (error) {
        console.error('Error deleting product:', error);
        toast.error(t('products.deleteError'));
      }
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingProduct(null);
    setImagePreview(null);
    setFormData({
      name: '',
      description: '',
      categoryId: '',
      purchasePrice: '',
      sellingPrice: '',
      stockQuantity: '',
      minStockAlert: '',
      unit: 'PIECE',
      barcode: '',
      imageUrl: '',
      active: true
    });
  };

  const handleImageUpload = (e) => {
    const file = e.target.files[0];
    if (file) {
      // Vérifier le type de fichier
      if (!file.type.startsWith('image/')) {
        toast.error(t('products.selectValidImage'));
        return;
      }

      // Vérifier la taille du fichier (max 5MB)
      if (file.size > 5 * 1024 * 1024) {
        toast.error(t('products.imageSizeLimit'));
        return;
      }

      // Créer une prévisualisation
      const reader = new FileReader();
      reader.onloadend = () => {
        setImagePreview(reader.result);
        setFormData(prev => ({
          ...prev,
          imageUrl: reader.result
        }));
      };
      reader.readAsDataURL(file);
    }
  };

  const handleRemoveImage = () => {
    setImagePreview(null);
    setFormData(prev => ({
      ...prev,
      imageUrl: ''
    }));
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleViewModeChange = (mode) => {
    setViewMode(mode);
    localStorage.setItem('productsViewMode', mode);
  };


  const handleSort = (key) => {
    let direction = 'asc';
    if (sortConfig.key === key && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key, direction });
  };

  const handleExport = () => {
    const csvContent = [
      ['Code', 'Nom', 'Catégorie', 'Prix Achat', 'Prix Vente', 'Stock', 'Statut'],
      ...products.map(p => [
        p.code,
        p.name,
        p.category || '',
        p.purchasePrice,
        p.sellingPrice,
        p.stockQuantity,
        p.active ? 'Actif' : 'Inactif'
      ])
    ].map(row => row.join(',')).join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `produits_${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
  };

  const filteredProducts = products.filter((product) =>
    `${product.name} ${product.code} ${product.barcode || ''} ${product.category?.name || ''}`
      .toLowerCase()
      .includes(searchTerm.toLowerCase())
  );

  // Suggestions d'autocomplétion classées par pertinence (nom prioritaire sur
  // code / code-barres / catégorie), distinctes du filtrage du tableau.
  const productSuggestions = rankSuggestions(
    products,
    searchTerm,
    (p) => [p.name, p.code, p.barcode, p.category?.name],
    8
  );

  const sortedProducts = [...filteredProducts].sort((a, b) => {
    if (!sortConfig.key) return 0;

    const aValue = a[sortConfig.key];
    const bValue = b[sortConfig.key];

    if (aValue < bValue) return sortConfig.direction === 'asc' ? -1 : 1;
    if (aValue > bValue) return sortConfig.direction === 'asc' ? 1 : -1;
    return 0;
  });

  // Pagination
  const totalPages = Math.ceil(sortedProducts.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const paginatedProducts = sortedProducts.slice(startIndex, endIndex);

  // On affiche le maximum de produits : la liste complète, triée et paginée (jusqu'à 100 par page),
  // que l'on soit en navigation normale ou en recherche.
  const displayedProducts = paginatedProducts;

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const handleItemsPerPageChange = (newItemsPerPage) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1);
  };

  // Calculate statistics
  const totalProducts = products.length;
  const outOfStockCount = products.filter(p => p.stockQuantity === 0).length;
  const lowStockCount = products.filter(p => p.stockQuantity > 0 && p.stockQuantity < p.minStockAlert).length;
  const stockValue = products.reduce((sum, p) => sum + (p.stockQuantity * p.purchasePrice), 0);

  const columns = [
    {
      key: 'code',
      label: t('products.code'),
      render: (product) => (
        <div className="flex items-center gap-2">
          <span className="font-medium text-gray-900">{product.code}</span>
          <button
            onClick={() => handleSort('code')}
            className="text-gray-400 hover:text-gray-600"
          >
            <ArrowUpDown className="w-3 h-3" />
          </button>
        </div>
      )
    },
    {
      key: 'name',
      label: t('common.product'),
      render: (product) => (
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-secondary-500 rounded-lg flex items-center justify-center">
            <Package className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="font-medium text-gray-900">{product.name}</div>
            <div className="text-sm text-gray-500">{product.description || '-'}</div>
          </div>
        </div>
      )
    },
    {
      key: 'category',
      label: t('products.category'),
      render: (product) => (
        <span className="text-sm text-gray-600">{product.category?.name || '-'}</span>
      )
    },
    {
      key: 'sellingPrice',
      label: t('products.sellingPrice'),
      render: (product) => (
        <div className="flex items-center gap-2">
          <span className="subsection-title">{product.sellingPrice}€</span>
          <button
            onClick={() => handleSort('sellingPrice')}
            className="text-gray-400 hover:text-gray-600"
          >
            <ArrowUpDown className="w-3 h-3" />
          </button>
        </div>
      )
    },
    {
      key: 'stockQuantity',
      label: t('products.stock'),
      render: (product) => (
        <div className="flex items-center gap-2">
          <span className={`font-medium ${
            product.stockQuantity === 0
              ? 'text-red-600'
              : product.stockQuantity < product.minStockAlert
              ? 'text-amber-600'
              : 'text-green-600'
          }`}>
            {product.stockQuantity}
          </span>
          {product.stockQuantity < product.minStockAlert && (
            <AlertTriangle className="w-4 h-4 text-amber-600" />
          )}
          <button
            onClick={() => handleSort('stockQuantity')}
            className="text-gray-400 hover:text-gray-600"
          >
            <ArrowUpDown className="w-3 h-3" />
          </button>
        </div>
      )
    },
    {
      key: 'active',
      label: t('products.columnStatus'),
      render: (product) => (
        <span className={`badge ${product.active ? 'badge-success' : 'badge-danger'}`}>
          {product.active ? 'Actif' : 'Inactif'}
        </span>
      )
    }
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <Package aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('products.title')}</h1>
            <p className="page-subtitle">{t('products.subtitle')}</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            icon={RefreshCw}
            onClick={fetchProducts}
            loading={loading}
          >
            Actualiser
          </Button>
          {isAdmin && (
            <>
              <Button
                variant="secondary"
                icon={Upload}
                onClick={() => toast(t('common.comingSoon'), { icon: 'ℹ️' })}
              >
                Importer
              </Button>
              <Button
                variant="secondary"
                icon={Download}
                onClick={handleExport}
              >
                Exporter
              </Button>
              <Button
                variant="primary"
                icon={Plus}
                onClick={() => setShowModal(true)}
              >
                {t('products.addProduct')}
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <div className="stat-tile-info">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">{t('products.totalCount')}</p>
              <p className="text-3xl font-bold text-current">{totalProducts}</p>
            </div>
            <Package className="w-12 h-12 text-current opacity-60" />
          </div>
        </div>

        <div className="stat-tile-danger">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">{t('products.outOfStockLabel')}</p>
              <p className="text-3xl font-bold text-current">{outOfStockCount}</p>
            </div>
            <TrendingDown className="w-12 h-12 text-current opacity-60" />
          </div>
        </div>

        <div className="stat-tile-warning">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">{t('products.lowStockLabel')}</p>
              <p className="text-3xl font-bold text-current">{lowStockCount}</p>
            </div>
            <AlertTriangle className="w-12 h-12 text-current opacity-60" />
          </div>
        </div>

        <div className="stat-tile-success">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">{t('products.stockValueLabel')}</p>
              <p className="text-3xl font-bold text-current">{stockValue.toFixed(2)}€</p>
            </div>
            <Euro className="w-12 h-12 text-current opacity-60" />
          </div>
        </div>
      </div>

      {/* Search & View Toggle */}
      <div className="card">
        <div className="flex items-center gap-4">
          <SearchBox
            className="flex-1"
            value={searchTerm}
            onChange={setSearchTerm}
            placeholder={t('products.searchPlaceholder')}
            suggestions={productSuggestions}
            getKey={(p) => p.id}
            onSelectSuggestion={(p) => setSearchTerm(p.name)}
            renderSuggestion={(p) => (
              <span className="flex items-center justify-between gap-2">
                <span className="flex flex-col min-w-0">
                  <span className="font-medium truncate">{p.name}</span>
                  <span className="text-xs text-gray-400 truncate">
                    Code : {p.code}{p.barcode ? ` · ${p.barcode}` : ''}
                  </span>
                </span>
                <span className="text-xs text-gray-500 shrink-0">
                  {Number(p.sellingPrice).toFixed(2)} € · {p.stockQuantity} {p.unit}
                </span>
              </span>
            )}
          />
          <div className="flex items-center gap-2 border border-gray-300 rounded-lg p-1">
            <button
              onClick={() => handleViewModeChange('list')}
              className={`p-2 rounded transition-colors ${
                viewMode === 'list'
                  ? 'bg-primary-600 text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
              title={t('products.listViewTitle')}
            >
              <List className="w-5 h-5" />
            </button>
            <button
              onClick={() => handleViewModeChange('grid')}
              className={`p-2 rounded transition-colors ${
                viewMode === 'grid'
                  ? 'bg-primary-600 text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
              title={t('products.gridViewTitle')}
            >
              <Grid3x3 className="w-5 h-5" />
            </button>
          </div>
        </div>
      </div>

      {/* Products Display - List or Grid */}
      {viewMode === 'list' ? (
        <div className="card overflow-hidden">
          <Table
            columns={columns}
            data={displayedProducts}
            actions={(product) => (
              <>
                <button
                  onClick={() => handleViewDetails(product)}
                  className="text-gray-600 hover:text-gray-900 p-2 hover:bg-gray-100 rounded-lg transition-colors"
                  title={t('common.viewDetails')}
                >
                  <Eye className="w-4 h-4" />
                </button>
                {/* Le caissier est en lecture seule sur les produits : seules les actions
                    « Modifier » et « Supprimer » sont réservées à l'ADMIN. */}
                {isAdmin && (
                  <>
                    <button
                      onClick={() => handleEdit(product)}
                      className="text-primary-600 hover:text-primary-900 p-2 hover:bg-primary-50 rounded-lg transition-colors"
                      title={t('common.edit')}
                    >
                      <Edit className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => handleDelete(product.id)}
                      className="text-red-600 hover:text-red-900 p-2 hover:bg-red-50 rounded-lg transition-colors"
                      title={t('common.delete')}
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </>
                )}
              </>
            )}
          />

          {/* Pagination : affichée dès qu'il y a des produits (la liste complète est montrée). */}
          {sortedProducts.length > 0 && (
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalItems={sortedProducts.length}
              itemsPerPage={itemsPerPage}
              onPageChange={handlePageChange}
              onItemsPerPageChange={handleItemsPerPageChange}
            />
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {displayedProducts.length === 0 ? (
            <div className="col-span-full text-center py-12 text-gray-500">
              Aucun produit disponible
            </div>
          ) : (
            displayedProducts.map((product, index) => (
              <motion.div
                key={product.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
                onClick={() => handleViewDetails(product)}
                className="card hover:shadow-lg transition-shadow cursor-pointer group"
              >
                {/* Product Image */}
                <div className="relative h-48 bg-gradient-to-br from-gray-100 to-gray-200 rounded-lg mb-4 overflow-hidden">
                  {product.imageUrl ? (
                    <img
                      src={product.imageUrl}
                      alt={product.name}
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center">
                      <Package className="w-16 h-16 text-gray-400" />
                    </div>
                  )}
                  {/* Status Badge */}
                  <div className="absolute top-2 right-2">
                    <span className={`badge ${product.active ? 'badge-success' : 'badge-danger'}`}>
                      {product.active ? 'Actif' : 'Inactif'}
                    </span>
                  </div>
                  {/* Stock Alert Badge */}
                  {product.stockQuantity === 0 && (
                    <div className="absolute top-2 left-2">
                      <span className="badge badge-danger flex items-center gap-1">
                        <AlertTriangle className="w-3 h-3" />
                        Rupture
                      </span>
                    </div>
                  )}
                  {product.stockQuantity > 0 && product.stockQuantity < product.minStockAlert && (
                    <div className="absolute top-2 left-2">
                      <span className="badge-warning">
                        <AlertTriangle className="w-3 h-3" />
                        Stock faible
                      </span>
                    </div>
                  )}
                  {/* Action Buttons - Show on hover (stopPropagation pour ne pas ré-ouvrir les détails depuis le clic sur la carte) */}
                  <div className="absolute inset-0 bg-black bg-opacity-50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2">
                    <button
                      onClick={(e) => { e.stopPropagation(); handleViewDetails(product); }}
                      className="bg-white text-gray-700 p-3 rounded-lg hover:bg-gray-100 transition-colors"
                      title={t('common.viewDetails')}
                    >
                      <Eye className="w-5 h-5" />
                    </button>
                    {/* Caissier en lecture seule : édition et suppression réservées à l'ADMIN. */}
                    {isAdmin && (
                      <>
                        <button
                          onClick={(e) => { e.stopPropagation(); handleEdit(product); }}
                          className="bg-white text-primary-600 p-3 rounded-lg hover:bg-primary-50 transition-colors"
                          title={t('common.edit')}
                        >
                          <Edit className="w-5 h-5" />
                        </button>
                        <button
                          onClick={(e) => { e.stopPropagation(); handleDelete(product.id); }}
                          className="bg-white text-red-600 p-3 rounded-lg hover:bg-red-50 transition-colors"
                          title={t('common.delete')}
                        >
                          <Trash2 className="w-5 h-5" />
                        </button>
                      </>
                    )}
                  </div>
                </div>

                {/* Product Info */}
                <div className="space-y-2">
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <h3 className="font-semibold text-gray-900 line-clamp-1">{product.name}</h3>
                      <p className="text-sm text-gray-500">{product.code}</p>
                    </div>
                  </div>

                  {product.description && (
                    <p className="text-sm text-gray-600 line-clamp-2">{product.description}</p>
                  )}

                  {product.category && (
                    <span className="inline-block badge badge-info text-xs">
                      {product.category.name}
                    </span>
                  )}

                  <div className="pt-2 border-t border-gray-200">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm text-gray-600">{t('products.sellingPrice')}</span>
                      <span className="text-lg font-bold text-primary-600">{product.sellingPrice}€</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-gray-600">{t('products.stock')}</span>
                      <span className={`font-semibold ${
                        product.stockQuantity === 0
                          ? 'text-red-600'
                          : product.stockQuantity < product.minStockAlert
                          ? 'text-amber-600'
                          : 'text-green-600'
                      }`}>
                        {product.stockQuantity} {product.unit?.toLowerCase() || ''}
                      </span>
                    </div>
                  </div>
                </div>
              </motion.div>
            ))
          )}

          {/* Pagination : affichée dès qu'il y a des produits (la liste complète est montrée). */}
          {sortedProducts.length > 0 && (
            <div className="col-span-full mt-6">
              <div className="card">
                <Pagination
                  currentPage={currentPage}
                  totalPages={totalPages}
                  totalItems={sortedProducts.length}
                  itemsPerPage={itemsPerPage}
                  onPageChange={handlePageChange}
                  onItemsPerPageChange={handleItemsPerPageChange}
                />
              </div>
            </div>
          )}
        </div>
      )}

      {/* Product Modal Form */}
      <Modal
        isOpen={showModal}
        onClose={handleCloseModal}
        title={editingProduct ? 'Modifier le produit' : 'Nouveau produit'}
        size="lg"
      >
        <form onSubmit={handleSubmit} className="space-y-6">
          {editingProduct && (
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 flex items-center gap-3">
              <Package className="w-5 h-5 text-blue-600" />
              <div>
                <p className="text-sm font-medium text-blue-900">Code produit: {editingProduct.code}</p>
                <p className="text-xs text-blue-700">{t('products.codeImmutable')}</p>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <FormInput
              label={t('products.nameLabel')}
              name="name"
              value={formData.name}
              onChange={handleInputChange}
              placeholder={t('products.namePlaceholder')}
              required
              icon={Package}
            />

            <div className="md:col-span-2">
              <FormInput
                label={t('common.description')}
                name="description"
                type="textarea"
                value={formData.description}
                onChange={handleInputChange}
                placeholder={t('products.descriptionPlaceholder')}
              />
            </div>

            <FormSelect
              label={t('products.categoryLabel')}
              name="categoryId"
              value={formData.categoryId}
              onChange={handleInputChange}
              options={categories.map(cat => ({ value: cat.id, label: cat.name }))}
              placeholder={t('products.selectCategory')}
            />

            <FormSelect
              label={t('products.unitLabel')}
              name="unit"
              value={formData.unit}
              onChange={handleInputChange}
              required
              options={[
                { value: 'PIECE', label: t('products.units.PIECE') },
                { value: 'KILOGRAM', label: t('products.units.KILOGRAM') },
                { value: 'LITER', label: t('products.units.LITER') },
                { value: 'METER', label: t('products.units.METER') },
                { value: 'BOX', label: t('products.units.BOX') }
              ]}
            />

            <FormInput
              label={t('products.purchasePriceLabel')}
              name="purchasePrice"
              type="number"
              step="0.01"
              value={formData.purchasePrice}
              onChange={handleInputChange}
              placeholder="0.00"
              required
              icon={Euro}
            />

            <FormInput
              label={t('products.sellingPriceLabel')}
              name="sellingPrice"
              type="number"
              step="0.01"
              value={formData.sellingPrice}
              onChange={handleInputChange}
              placeholder="0.00"
              required
              icon={Euro}
            />

            <FormInput
              label={t('products.stockQuantityLabel')}
              name="stockQuantity"
              type="number"
              value={formData.stockQuantity}
              onChange={handleInputChange}
              placeholder="0"
              required
              icon={Package}
            />

            <FormInput
              label={t('products.minStockAlertLabel')}
              name="minStockAlert"
              type="number"
              value={formData.minStockAlert}
              onChange={handleInputChange}
              placeholder="10"
              required
              icon={AlertTriangle}
            />

            <FormInput
              label={t('products.barcodeLabel')}
              name="barcode"
              value={formData.barcode}
              onChange={handleInputChange}
              placeholder="1234567890123"
            />
          </div>

          {/* Image Upload Section */}
          <div className="space-y-3">
            <label className="block text-sm font-medium text-gray-700">
              Image du produit
            </label>
            <div className="flex items-start gap-4">
              {/* Image Preview */}
              <div className="flex-shrink-0">
                <div className="w-32 h-32 border-2 border-dashed border-gray-300 rounded-lg overflow-hidden bg-gray-50 flex items-center justify-center">
                  {imagePreview || formData.imageUrl ? (
                    <div className="relative w-full h-full group">
                      <img
                        src={imagePreview || formData.imageUrl}
                        alt={t('products.imagePreviewAlt')}
                        className="w-full h-full object-cover"
                      />
                      <button
                        type="button"
                        onClick={handleRemoveImage}
                        className="absolute top-1 right-1 bg-red-500 text-white p-1 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  ) : (
                    <ImageIcon className="w-12 h-12 text-gray-400" />
                  )}
                </div>
              </div>

              {/* Upload Controls */}
              <div className="flex-1 space-y-3">
                <div>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleImageUpload}
                    className="hidden"
                    id="image-upload"
                  />
                  <label
                    htmlFor="image-upload"
                    className="inline-flex items-center gap-2 px-4 py-2 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 cursor-pointer transition-colors"
                  >
                    <Upload className="w-4 h-4" />
                    Choisir une image
                  </label>
                </div>
                <p className="text-xs text-gray-500">
                  Formats acceptés: JPG, PNG, GIF. Taille max: 5MB
                </p>
                <div className="pt-2">
                  <FormInput
                    label={t('products.imageUrlLabel')}
                    name="imageUrl"
                    value={formData.imageUrl}
                    onChange={handleInputChange}
                    placeholder="https://..."
                  />
                </div>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="active"
              name="active"
              checked={formData.active}
              onChange={handleInputChange}
              className="w-4 h-4 text-primary-600 rounded focus:ring-primary-500"
            />
            <label htmlFor="active" className="text-sm font-medium text-gray-700">
              Produit actif
            </label>
          </div>

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
              loading={loading}
            >
              {editingProduct ? 'Modifier' : 'Créer'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Confirmation Modal */}
      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmSubmit}
        title={editingProduct ? "Confirmer la modification" : "Confirmer la création"}
        message={editingProduct
          ? `Voulez-vous vraiment modifier le produit "${formData.name}" ?`
          : `Voulez-vous vraiment créer le produit "${formData.name}" ?`
        }
        type="info"
      />

      {/* Product Details Modal */}
      {selectedProduct && (
        <Modal
          isOpen={showDetailsModal}
          onClose={handleCloseDetails}
          title={t('products.productDetails')}
          size="lg"
        >
          <ProductDetails
            product={selectedProduct}
            // Caissier en lecture seule : pas de onEdit → le bouton « Modifier » du détail disparaît.
            onEdit={isAdmin ? () => {
              handleCloseDetails();
              handleEdit(selectedProduct);
            } : undefined}
            onClose={handleCloseDetails}
          />
        </Modal>
      )}

    </div>
  );
};

const formatDateTime = (value) => {
  // Convention de date de la langue active (cf. `export.locale`).
  if (!value) return '—';
  try {
    return new Date(value).toLocaleString(i18n.t('export.locale'), {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  } catch {
    return String(value);
  }
};

const ProductDetails = ({ product, onEdit, onClose }) => {
  const { t } = useTranslation();
  const purchase = Number(product.purchasePrice) || 0;
  const selling = Number(product.sellingPrice) || 0;
  const stock = Number(product.stockQuantity) || 0;
  const minAlert = Number(product.minStockAlert) || 0;
  const marginAbs = selling - purchase;
  const marginPct = purchase > 0 ? (marginAbs / purchase) * 100 : null;
  const stockValue = stock * purchase;

  let stockStatus;
  if (stock === 0) {
    stockStatus = { label: t('stock.statOut'), class: 'badge-danger', icon: TrendingDown };
  } else if (stock < minAlert) {
    stockStatus = { label: t('stock.status.low'), class: 'badge-warning', icon: AlertTriangle };
  } else {
    stockStatus = { label: t('products.stockNormal'), class: 'badge-success', icon: TrendingUp };
  }
  const StockIcon = stockStatus.icon;

  return (
    <div className="space-y-6">
      {/* Header — image + identité */}
      <div className="flex items-start gap-4 pb-4 border-b border-gray-200">
        <div className="flex-shrink-0 w-32 h-32 bg-gradient-to-br from-gray-100 to-gray-200 rounded-lg overflow-hidden flex items-center justify-center">
          {product.imageUrl ? (
            <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover" />
          ) : (
            <Package className="w-12 h-12 text-gray-400" />
          )}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className={`badge ${product.active ? 'badge-success' : 'badge-danger'}`}>
              {product.active ? 'Actif' : 'Inactif'}
            </span>
            {product.category?.name && (
              <span className="badge badge-info flex items-center gap-1">
                <FolderTree className="w-3 h-3" />
                {product.category.name}
              </span>
            )}
          </div>
          <h2 className="modal-title break-words">{product.name}</h2>
          <p className="text-sm text-gray-500 flex items-center gap-1 mt-1">
            <Hash className="w-3 h-3" />
            {product.code}
          </p>
          {product.barcode && (
            <p className="text-sm text-gray-500 flex items-center gap-1 mt-1">
              <Barcode className="w-3 h-3" />
              {product.barcode}
            </p>
          )}
        </div>
      </div>

      {/* Description */}
      {product.description && (
        <div>
          <h3 className="subsection-title mb-2">{t('common.description')}</h3>
          <p className="text-sm text-gray-600 whitespace-pre-wrap bg-gray-50 p-3 rounded-lg">
            {product.description}
          </p>
        </div>
      )}

      {/* Prix & marge */}
      <div>
        <h3 className="subsection-title mb-2 flex items-center gap-2">
          <Euro className="w-4 h-4" /> Prix
        </h3>
        <div className="grid grid-cols-3 gap-3">
          <div className="bg-gray-50 rounded-lg p-3">
            <p className="text-xs text-gray-500">{t('products.purchasePrice')}</p>
            <p className="text-lg font-semibold text-gray-900">{purchase.toFixed(2)}€</p>
          </div>
          <div className="bg-primary-50 rounded-lg p-3">
            <p className="text-xs text-primary-600">{t('products.sellingPrice')}</p>
            <p className="text-lg font-semibold text-primary-700">{selling.toFixed(2)}€</p>
          </div>
          <div className={`rounded-lg p-3 ${marginAbs >= 0 ? 'bg-green-50' : 'bg-red-50'}`}>
            <p className={`text-xs ${marginAbs >= 0 ? 'text-green-600' : 'text-red-600'}`}>{t('products.grossMargin')}</p>
            <p className={`text-lg font-semibold ${marginAbs >= 0 ? 'text-green-700' : 'text-red-700'}`}>
              {marginAbs.toFixed(2)}€
              {marginPct !== null && (
                <span className="text-sm font-normal ml-1">({marginPct.toFixed(1)}%)</span>
              )}
            </p>
          </div>
        </div>
      </div>

      {/* Stock */}
      <div>
        <h3 className="subsection-title mb-2 flex items-center gap-2">
          <Package className="w-4 h-4" /> Stock
        </h3>
        <div className="grid grid-cols-3 gap-3 mb-3">
          <div className="bg-gray-50 rounded-lg p-3">
            <p className="text-xs text-gray-500">{t('products.currentQuantity')}</p>
            <p className="text-lg font-semibold text-gray-900">
              {stock} <span className="text-sm font-normal text-gray-500">{product.unit || ''}</span>
            </p>
          </div>
          <div className="bg-gray-50 rounded-lg p-3">
            <p className="text-xs text-gray-500">{t('products.alertThreshold')}</p>
            <p className="text-lg font-semibold text-gray-900">{minAlert}</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-3">
            <p className="text-xs text-gray-500">{t('stock.statValue')}</p>
            <p className="text-lg font-semibold text-gray-900">{stockValue.toFixed(2)}€</p>
          </div>
        </div>
        <span className={stockStatus.class}>
          <StockIcon className="w-3 h-3" />
          {stockStatus.label}
        </span>
      </div>

      {/* Méta */}
      <div className="grid grid-cols-2 gap-3 pt-4 border-t border-gray-200 text-sm text-gray-600">
        <div className="flex items-center gap-2">
          <Calendar className="w-4 h-4 text-gray-400" />
          <span>Créé&nbsp;: {formatDateTime(product.createdAt)}</span>
        </div>
        <div className="flex items-center gap-2">
          <Calendar className="w-4 h-4 text-gray-400" />
          <span>Modifié&nbsp;: {formatDateTime(product.updatedAt)}</span>
        </div>
      </div>

      <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200">
        <Button variant="secondary" type="button" onClick={onClose}>
          Fermer
        </Button>
        {/* « Modifier » n'est rendu que si l'appelant fournit onEdit (ADMIN) : le caissier,
            en lecture seule, ne voit que « Fermer ». */}
        {onEdit && (
          <Button variant="primary" type="button" icon={Edit} onClick={onEdit}>
            Modifier
          </Button>
        )}
      </div>
    </div>
  );
};

export default Products;
