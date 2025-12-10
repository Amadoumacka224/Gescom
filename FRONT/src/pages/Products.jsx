import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Search, Edit, Trash2, Package, AlertTriangle, RefreshCw, FolderTree, Upload, Download, TrendingUp, TrendingDown, DollarSign, ArrowUpDown, Grid3x3, List, Image as ImageIcon, X } from 'lucide-react';
import { motion } from 'framer-motion';
import productService from '../services/productService';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Pagination from '../components/Pagination';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import Button from '../components/Button';
import Table from '../components/Table';

const Products = () => {
  const { t } = useTranslation();
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [sortConfig, setSortConfig] = useState({ key: null, direction: 'asc' });
  const [viewMode, setViewMode] = useState(() => {
    // Load view mode from localStorage, default to 'list' if not set
    return localStorage.getItem('productsViewMode') || 'list';
  });
  const [imagePreview, setImagePreview] = useState(null);
  const fileInputRef = useRef(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
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
      // Préparer les données du produit
      const productData = {
        ...formData,
        category: formData.categoryId ? { id: parseInt(formData.categoryId) } : null
      };

      // Supprimer categoryId car le backend attend category
      delete productData.categoryId;

      console.log('Sending product data:', productData);

      if (editingProduct) {
        await productService.updateProduct(editingProduct.id, productData);
        alert('✅ Produit modifié avec succès!');
      } else {
        await productService.createProduct(productData);
        alert('✅ Produit créé avec succès!');
      }

      await fetchProducts();
      handleCloseModal();
    } catch (error) {
      console.error('Error saving product:', error);
      console.error('Error response:', error.response);
      const errorMessage = error.response?.data || error.message || 'Erreur lors de l\'enregistrement du produit';
      alert('❌ Erreur: ' + errorMessage);
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

  const handleDelete = async (id) => {
    if (window.confirm('Êtes-vous sûr de vouloir supprimer ce produit ?')) {
      try {
        await productService.deleteProduct(id);
        await fetchProducts();
      } catch (error) {
        console.error('Error deleting product:', error);
        alert('Erreur lors de la suppression du produit');
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
        alert('Veuillez sélectionner une image valide');
        return;
      }

      // Vérifier la taille du fichier (max 5MB)
      if (file.size > 5 * 1024 * 1024) {
        alert('L\'image ne doit pas dépasser 5MB');
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
    `${product.name} ${product.code} ${product.category?.name || ''}`
      .toLowerCase()
      .includes(searchTerm.toLowerCase())
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
      label: 'Code',
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
      label: 'Produit',
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
      label: 'Catégorie',
      render: (product) => (
        <span className="text-sm text-gray-600">{product.category?.name || '-'}</span>
      )
    },
    {
      key: 'sellingPrice',
      label: 'Prix Vente',
      render: (product) => (
        <div className="flex items-center gap-2">
          <span className="font-semibold text-gray-900">{product.sellingPrice}€</span>
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
      label: 'Stock',
      render: (product) => (
        <div className="flex items-center gap-2">
          <span className={`font-medium ${
            product.stockQuantity === 0
              ? 'text-red-600'
              : product.stockQuantity < product.minStockAlert
              ? 'text-orange-600'
              : 'text-green-600'
          }`}>
            {product.stockQuantity}
          </span>
          {product.stockQuantity < product.minStockAlert && (
            <AlertTriangle className="w-4 h-4 text-orange-600" />
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
      label: 'Statut',
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
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">{t('products.title')}</h1>
          <p className="text-gray-600 mt-1">Gérez votre catalogue de produits</p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            icon={RefreshCw}
            onClick={fetchProducts}
          >
            Actualiser
          </Button>
          <Button
            variant="secondary"
            icon={Upload}
            onClick={() => alert('Import en cours de développement')}
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
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <div className="card bg-gradient-to-br from-blue-50 to-cyan-50 border-blue-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-blue-600 font-medium">Total Produits</p>
              <p className="text-3xl font-bold text-blue-700">{totalProducts}</p>
            </div>
            <Package className="w-12 h-12 text-blue-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-red-50 to-orange-50 border-red-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-red-600 font-medium">Rupture de Stock</p>
              <p className="text-3xl font-bold text-red-700">{outOfStockCount}</p>
            </div>
            <TrendingDown className="w-12 h-12 text-red-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-orange-50 to-yellow-50 border-orange-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-orange-600 font-medium">Stock Faible</p>
              <p className="text-3xl font-bold text-orange-700">{lowStockCount}</p>
            </div>
            <AlertTriangle className="w-12 h-12 text-orange-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-green-50 to-emerald-50 border-green-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-green-600 font-medium">Valeur du Stock</p>
              <p className="text-3xl font-bold text-green-700">{stockValue.toFixed(2)}€</p>
            </div>
            <DollarSign className="w-12 h-12 text-green-600 opacity-50" />
          </div>
        </div>
      </div>

      {/* Search & View Toggle */}
      <div className="card">
        <div className="flex items-center gap-4">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              placeholder={t('common.search') + ' produits par code, nom ou catégorie...'}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="input-field pl-10"
            />
          </div>
          <div className="flex items-center gap-2 border border-gray-300 rounded-lg p-1">
            <button
              onClick={() => handleViewModeChange('list')}
              className={`p-2 rounded transition-colors ${
                viewMode === 'list'
                  ? 'bg-primary-600 text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
              title="Vue liste"
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
              title="Vue grille"
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
            data={paginatedProducts}
            actions={(product) => (
              <>
                <button
                  onClick={() => handleEdit(product)}
                  className="text-primary-600 hover:text-primary-900 p-2 hover:bg-primary-50 rounded-lg transition-colors"
                  title="Modifier"
                >
                  <Edit className="w-4 h-4" />
                </button>
                <button
                  onClick={() => handleDelete(product.id)}
                  className="text-red-600 hover:text-red-900 p-2 hover:bg-red-50 rounded-lg transition-colors"
                  title="Supprimer"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </>
            )}
          />

          {/* Pagination */}
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
          {sortedProducts.length === 0 ? (
            <div className="col-span-full text-center py-12 text-gray-500">
              Aucun produit disponible
            </div>
          ) : (
            paginatedProducts.map((product, index) => (
              <motion.div
                key={product.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
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
                      <span className="badge bg-orange-100 text-orange-700 border-orange-200 flex items-center gap-1">
                        <AlertTriangle className="w-3 h-3" />
                        Stock faible
                      </span>
                    </div>
                  )}
                  {/* Action Buttons - Show on hover */}
                  <div className="absolute inset-0 bg-black bg-opacity-50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2">
                    <button
                      onClick={() => handleEdit(product)}
                      className="bg-white text-primary-600 p-3 rounded-lg hover:bg-primary-50 transition-colors"
                      title="Modifier"
                    >
                      <Edit className="w-5 h-5" />
                    </button>
                    <button
                      onClick={() => handleDelete(product.id)}
                      className="bg-white text-red-600 p-3 rounded-lg hover:bg-red-50 transition-colors"
                      title="Supprimer"
                    >
                      <Trash2 className="w-5 h-5" />
                    </button>
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
                      <span className="text-sm text-gray-600">Prix de vente</span>
                      <span className="text-lg font-bold text-primary-600">{product.sellingPrice}€</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-gray-600">Stock</span>
                      <span className={`font-semibold ${
                        product.stockQuantity === 0
                          ? 'text-red-600'
                          : product.stockQuantity < product.minStockAlert
                          ? 'text-orange-600'
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

          {/* Pagination */}
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
                <p className="text-xs text-blue-700">Le code produit ne peut pas être modifié</p>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <FormInput
              label="Nom du produit"
              name="name"
              value={formData.name}
              onChange={handleInputChange}
              placeholder="Nom du produit"
              required
              icon={Package}
            />

            <div className="md:col-span-2">
              <FormInput
                label="Description"
                name="description"
                type="textarea"
                value={formData.description}
                onChange={handleInputChange}
                placeholder="Description du produit"
              />
            </div>

            <FormSelect
              label="Catégorie"
              name="categoryId"
              value={formData.categoryId}
              onChange={handleInputChange}
              options={categories.map(cat => ({ value: cat.id, label: cat.name }))}
              placeholder="Sélectionner une catégorie"
            />

            <FormSelect
              label="Unité"
              name="unit"
              value={formData.unit}
              onChange={handleInputChange}
              required
              options={[
                { value: 'PIECE', label: 'Pièce' },
                { value: 'KILOGRAM', label: 'Kilogramme' },
                { value: 'LITER', label: 'Litre' },
                { value: 'METER', label: 'Mètre' },
                { value: 'BOX', label: 'Boîte' }
              ]}
            />

            <FormInput
              label="Prix d'achat"
              name="purchasePrice"
              type="number"
              step="0.01"
              value={formData.purchasePrice}
              onChange={handleInputChange}
              placeholder="0.00"
              required
              icon={DollarSign}
            />

            <FormInput
              label="Prix de vente"
              name="sellingPrice"
              type="number"
              step="0.01"
              value={formData.sellingPrice}
              onChange={handleInputChange}
              placeholder="0.00"
              required
              icon={DollarSign}
            />

            <FormInput
              label="Quantité en stock"
              name="stockQuantity"
              type="number"
              value={formData.stockQuantity}
              onChange={handleInputChange}
              placeholder="0"
              required
              icon={Package}
            />

            <FormInput
              label="Stock minimum (alerte)"
              name="minStockAlert"
              type="number"
              value={formData.minStockAlert}
              onChange={handleInputChange}
              placeholder="10"
              required
              icon={AlertTriangle}
            />

            <FormInput
              label="Code-barres"
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
                        alt="Preview"
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
                    label="Ou entrez l'URL de l'image"
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

    </div>
  );
};

export default Products;
