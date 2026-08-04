import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'react-hot-toast';
import { Plus, Search, Edit, Trash2, Package, AlertTriangle, RefreshCw, FolderTree, Upload, Download, TrendingUp, TrendingDown, Euro, Grid3x3, List, Image as ImageIcon, X, Eye, Barcode, Tag, Calendar, Hash } from 'lucide-react';
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
import FormSection from '../components/FormSection';
import StatCard from '../components/StatCard';
import { rankSuggestions } from '../utils/searchSuggestions';
import { formatCurrency, formatPercent } from '../utils/format';
import i18n from '../i18n';

const EMPTY_FORM = {
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
  active: true,
};

const FORM_KEYS = Object.keys(EMPTY_FORM);

/** Longueurs maximales reprises des contraintes `@Size` de `ProductRequest`. */
const MAX_LENGTHS = { name: 200, barcode: 50 };

/** Ordre visuel des champs : décide lequel reçoit le focus quand plusieurs sont en erreur. */
const FIELD_ORDER = [
  'name', 'categoryId', 'unit', 'purchasePrice', 'sellingPrice',
  'stockQuantity', 'minStockAlert', 'barcode',
];

const isBlank = (value) => value === '' || value === null || value === undefined;

/**
 * Valide le formulaire en une passe et renvoie les messages par champ.
 * Les règles sont celles de `ProductRequest` : nom obligatoire, prix obligatoires et positifs
 * ou nuls, stock et seuil d'alerte positifs ou nuls.
 */
const validateProduct = (data, t) => {
  const errors = {};

  if (!(data.name || '').trim()) errors.name = t('products.errorNameRequired');
  else if ((data.name || '').trim().length > MAX_LENGTHS.name) {
    errors.name = t('products.errorMaxLength', { max: MAX_LENGTHS.name });
  }

  [['purchasePrice', 'errorPurchasePriceRequired'], ['sellingPrice', 'errorSellingPriceRequired']]
    .forEach(([field, requiredKey]) => {
      if (isBlank(data[field])) errors[field] = t(`products.${requiredKey}`);
      else if (Number.isNaN(Number(data[field])) || Number(data[field]) < 0) {
        errors[field] = t('products.errorPriceNegative');
      }
    });

  [['stockQuantity', 'errorStockRequired'], ['minStockAlert', 'errorMinStockRequired']]
    .forEach(([field, requiredKey]) => {
      if (isBlank(data[field])) errors[field] = t(`products.${requiredKey}`);
      else if (!Number.isInteger(Number(data[field])) || Number(data[field]) < 0) {
        errors[field] = t('products.errorQuantityInvalid');
      }
    });

  if ((data.barcode || '').trim().length > MAX_LENGTHS.barcode) {
    errors.barcode = t('products.errorMaxLength', { max: MAX_LENGTHS.barcode });
  }

  return errors;
};

/**
 * Marge brute du produit. Vendre à perte reste possible (déstockage, promotion) : la marge
 * est signalée, jamais bloquante — d'où un simple indicateur et non une erreur de validation.
 */
const computeMargin = (purchasePrice, sellingPrice) => {
  const buy = Number(purchasePrice);
  const sell = Number(sellingPrice);
  if (isBlank(purchasePrice) || isBlank(sellingPrice) || Number.isNaN(buy) || Number.isNaN(sell)) {
    return null;
  }
  return { amount: sell - buy, rate: sell > 0 ? (sell - buy) / sell : 0 };
};

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
  const [formData, setFormData] = useState(EMPTY_FORM);
  // Valeurs à l'ouverture : comparées à la saisie pour savoir si le formulaire a bougé
  // (bouton d'enregistrement inutile à vide, garde-fou à la fermeture).
  const [initialForm, setInitialForm] = useState(EMPTY_FORM);
  const [touched, setTouched] = useState({});
  const [submitAttempted, setSubmitAttempted] = useState(false);
  // Erreurs renvoyées par l'API (`fieldErrors` du GlobalExceptionHandler), tenues à part des
  // erreurs locales : elles ne se recalculent pas à la frappe et sont levées champ par champ.
  const [serverErrors, setServerErrors] = useState({});
  const [showDiscardConfirm, setShowDiscardConfirm] = useState(false);
  // Enregistrement distinct du chargement de la liste : `loading` pilote aussi le squelette
  // du tableau, qui n'a aucune raison de clignoter pendant une sauvegarde.
  const [saving, setSaving] = useState(false);

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
    // Le verdict du serveur portait sur l'ancienne valeur : il n'a plus de sens dès qu'elle change.
    setServerErrors(prev => (prev[name] === undefined ? prev : { ...prev, [name]: undefined }));
  };

  // Une erreur ne s'affiche qu'une fois le champ quitté, ou dès la première tentative
  // d'enregistrement : la signaler à la première lettre tapée serait juste mais pénible.
  const handleBlur = (e) => {
    const { name } = e.target;
    setTouched(prev => (prev[name] ? prev : { ...prev, [name]: true }));
  };

  const formErrors = validateProduct(formData, t);

  const visibleErrors = {};
  Object.entries(formErrors).forEach(([field, message]) => {
    if (submitAttempted || touched[field]) visibleErrors[field] = message;
  });
  Object.entries(serverErrors).forEach(([field, message]) => {
    if (message) visibleErrors[field] = message;
  });

  const fieldLabels = {
    name: t('products.nameLabel'),
    categoryId: t('products.categoryLabel'),
    unit: t('products.unitLabel'),
    purchasePrice: t('products.purchasePriceLabel'),
    sellingPrice: t('products.sellingPriceLabel'),
    stockQuantity: t('products.stockQuantityLabel'),
    minStockAlert: t('products.minStockAlertLabel'),
    barcode: t('products.barcodeLabel'),
  };

  const invalidFields = FIELD_ORDER.filter(field => visibleErrors[field]);
  const isDirty = FORM_KEYS.some(key => formData[key] !== initialForm[key]);
  const canSubmit = !saving && (!editingProduct || isDirty);
  const margin = computeMargin(formData.purchasePrice, formData.sellingPrice);

  const focusField = (field) => {
    document.getElementById(field)?.focus();
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setSubmitAttempted(true);

    const remaining = FIELD_ORDER.filter(field => formErrors[field]);
    if (remaining.length > 0) {
      focusField(remaining[0]);
      return;
    }
    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    setSaving(true);

    try {
      // Corps construit champ par champ plutôt qu'en étalant `formData` : en édition, celui-ci
      // portait aussi `id`, `code`, `createdAt`, `category`… inconnus de `ProductRequest`.
      const productData = {
        name: formData.name.trim(),
        description: (formData.description || '').trim() || null,
        categoryId: formData.categoryId ? parseInt(formData.categoryId) : null,
        purchasePrice: Number(formData.purchasePrice),
        sellingPrice: Number(formData.sellingPrice),
        stockQuantity: parseInt(formData.stockQuantity, 10),
        minStockAlert: parseInt(formData.minStockAlert, 10),
        unit: formData.unit,
        barcode: (formData.barcode || '').trim() || null,
        imageUrl: (formData.imageUrl || '').trim() || null,
        active: formData.active,
      };

      if (editingProduct) {
        await productService.updateProduct(editingProduct.id, productData);
        toast.success(t('products.updatedSuccess'));
      } else {
        await productService.createProduct(productData);
        toast.success(t('products.createdSuccess'));
      }

      await fetchProducts();
      closeForm();
    } catch (error) {
      console.error('Error saving product:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string'
        ? raw
        : (raw?.message || raw?.error || t('products.saveError'));

      // Le refus du serveur est ramené sur le champ concerné plutôt que sur un simple toast :
      // l'utilisateur voit quoi corriger sans relire tout le formulaire.
      const fieldErrors = typeof raw === 'object' && raw?.fieldErrors ? { ...raw.fieldErrors } : {};
      const flagged = FIELD_ORDER.filter(field => fieldErrors[field]);
      if (flagged.length > 0) {
        setServerErrors(fieldErrors);
        setSubmitAttempted(true);
        setTimeout(() => focusField(flagged[0]), 0);
      }

      toast.error(t('common.errorPrefixed', { message }));
    } finally {
      setSaving(false);
    }
  };

  /** Ouvre le formulaire sur des valeurs données, en repartant d'un état de validation vierge. */
  const openForm = (product) => {
    // On ne reprend que les champs du formulaire : l'objet reçu de l'API porte aussi `id`,
    // `code`, `createdAt`… que le DTO de requête n'attend pas.
    const values = product
      ? {
          name: product.name || '',
          description: product.description || '',
          categoryId: product.category?.id ? String(product.category.id) : '',
          purchasePrice: product.purchasePrice ?? '',
          sellingPrice: product.sellingPrice ?? '',
          stockQuantity: product.stockQuantity ?? '',
          minStockAlert: product.minStockAlert ?? '',
          unit: product.unit || 'PIECE',
          barcode: product.barcode || '',
          imageUrl: product.imageUrl || '',
          active: product.active !== false,
        }
      : EMPTY_FORM;

    setEditingProduct(product || null);
    setFormData(values);
    setInitialForm(values);
    setTouched({});
    setSubmitAttempted(false);
    setServerErrors({});
    setImagePreview(product?.imageUrl || null);
    setShowModal(true);
  };

  const handleEdit = (product) => openForm(product);

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

  const closeForm = () => {
    setShowModal(false);
    setShowDiscardConfirm(false);
    setEditingProduct(null);
    setImagePreview(null);
    setFormData(EMPTY_FORM);
    setInitialForm(EMPTY_FORM);
    setTouched({});
    setSubmitAttempted(false);
    setServerErrors({});
  };

  /**
   * Fermeture demandée par l'utilisateur (bouton Annuler, croix, clic sur le fond).
   * Le fond de la modale se ferme au moindre clic à côté : perdre une fiche produit
   * entièrement saisie à cette occasion est un incident réel.
   */
  const requestCloseForm = () => {
    if (isDirty) {
      setShowDiscardConfirm(true);
      return;
    }
    closeForm();
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

  // Le tri passe par les en-têtes de colonne (`Table`), pas par un bouton dans chaque cellule :
  // la valeur triée est parfois dérivée (nom de catégorie, prix en nombre), d'où l'accesseur.
  const sortedProducts = [...filteredProducts].sort((a, b) => {
    if (!sortConfig.key) return 0;
    const direction = sortConfig.direction === 'asc' ? 1 : -1;

    const valueOf = (product) => {
      switch (sortConfig.key) {
        case 'name': return (product.name || '').toLowerCase();
        case 'category': return (product.category?.name || '').toLowerCase();
        case 'sellingPrice': return Number(product.sellingPrice) || 0;
        case 'stockQuantity': return Number(product.stockQuantity) || 0;
        case 'active': return product.active ? 1 : 0;
        default: return (product[sortConfig.key] ?? '');
      }
    };

    const left = valueOf(a);
    const right = valueOf(b);
    if (left < right) return -direction;
    if (left > right) return direction;
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

  /* Colonnes du catalogue, ordonnées de l'identification à l'état commercial.
   *
   * Hauteur de ligne : la vignette de 32 px fixe le gabarit. Le nom tient sur une ligne et la
   * description sur deux interlignes serrés (2 × 15 px = 30 px), donc sous la vignette : une
   * rangée fait la même hauteur qu'elle porte ou non une description, quelle que soit la
   * longueur des textes. C'est ce qui rend les colonnes chiffrées lisibles en balayage vertical.
   *
   * Troncature par `line-clamp` et non par `truncate` : `truncate` impose `nowrap`, dont la
   * largeur minimale est celle du texte entier — un nom à rallonge élargissait alors le tableau
   * au lieu d'être coupé. Le texte complet reste accessible en infobulle et dans la fiche.
   *
   * Largeurs : les proportions ne sont posées qu'à partir de `xl`, là où la description est
   * affichée et se dispute la place avec le nom. En dessous, la répartition automatique du
   * tableau donne la largeur au nom, qui est la colonne la plus dense en texte.
   */
  const columns = [
    {
      key: 'code',
      label: t('products.code'),
      sortable: true,
      // Sous 640 px, le code quitte sa colonne pour se replier sous le nom (voir ci-dessous) :
      // à cette largeur, cinq colonnes ne tiennent qu'au prix d'un nom réduit à trois mots.
      className: 'hidden sm:table-cell w-px',
      render: (product) => (
        <span className="font-mono text-xs text-gray-500 dark:text-gray-400">{product.code}</span>
      )
    },
    {
      key: 'name',
      label: t('common.product'),
      sortable: true,
      nowrap: false,
      className: 'xl:w-[26%]',
      render: (product) => (
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 shrink-0 overflow-hidden rounded-md bg-gray-100 dark:bg-gray-700 flex items-center justify-center">
            {product.imageUrl ? (
              <img src={product.imageUrl} alt="" className="w-full h-full object-cover" />
            ) : (
              <Package className="w-4 h-4 text-gray-400" aria-hidden="true" />
            )}
          </div>
          <div className="min-w-0">
            <span
              className="line-clamp-1 font-medium text-gray-900 dark:text-gray-100"
              title={product.name}
            >
              {product.name}
            </span>
            <span className="block font-mono text-[11px] leading-tight text-gray-400 sm:hidden">
              {product.code}
            </span>
          </div>
        </div>
      )
    },
    {
      key: 'description',
      label: t('common.description'),
      nowrap: false,
      className: 'hidden xl:table-cell xl:w-[28%]',
      render: (product) => (
        product.description ? (
          <span
            className="line-clamp-2 text-xs leading-tight text-gray-500 dark:text-gray-400"
            title={product.description}
          >
            {product.description}
          </span>
        ) : (
          <span className="text-xs text-gray-300 dark:text-gray-600">—</span>
        )
      )
    },
    {
      key: 'category',
      label: t('products.category'),
      sortable: true,
      className: 'hidden lg:table-cell',
      render: (product) => (
        product.category?.name
          ? <span className="text-gray-600 dark:text-gray-400">{product.category.name}</span>
          : <span className="text-gray-300 dark:text-gray-600">—</span>
      )
    },
    {
      key: 'sellingPrice',
      label: t('products.sellingPrice'),
      sortable: true,
      className: 'text-right',
      render: (product) => (
        <span className="font-medium tabular-nums text-gray-900 dark:text-gray-100">
          {formatCurrency(product.sellingPrice)}
        </span>
      )
    },
    {
      key: 'stockQuantity',
      label: t('products.stock'),
      sortable: true,
      className: 'text-right',
      render: (product) => (
        <span className={`inline-flex items-center justify-end gap-1 font-medium tabular-nums ${
          product.stockQuantity === 0
            ? 'text-red-600 dark:text-red-400'
            : product.stockQuantity < product.minStockAlert
            ? 'text-amber-600 dark:text-amber-400'
            : 'text-green-600 dark:text-green-400'
        }`}>
          {product.stockQuantity < product.minStockAlert && (
            <AlertTriangle className="w-3.5 h-3.5" aria-hidden="true" />
          )}
          {product.stockQuantity}
        </span>
      )
    },
    {
      key: 'active',
      label: t('products.columnStatus'),
      sortable: true,
      className: 'hidden sm:table-cell',
      render: (product) => (
        <span className={product.active ? 'badge-success' : 'badge-danger'}>
          {product.active ? 'Actif' : 'Inactif'}
        </span>
      )
    }
  ];

  return (
    <div className="space-y-4">
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
                onClick={() => openForm(null)}
              >
                {t('products.addProduct')}
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Indicateurs — tuiles partagées (`StatCard`) plutôt qu'une variante maison : même
          gabarit que les autres pages, et une bande d'en-tête plus courte au-dessus du
          catalogue, qui est le contenu qu'on vient consulter. */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard title={t('products.totalCount')} value={totalProducts} icon={Package} tone="info" loading={loading} />
        <StatCard title={t('products.outOfStockLabel')} value={outOfStockCount} icon={TrendingDown} tone="danger" loading={loading} />
        <StatCard title={t('products.lowStockLabel')} value={lowStockCount} icon={AlertTriangle} tone="warning" loading={loading} />
        <StatCard title={t('products.stockValueLabel')} value={formatCurrency(stockValue)} icon={Euro} tone="success" loading={loading} />
      </div>

      {/* Search & View Toggle */}
      <div className="card p-4">
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
        // `p-0` : le tableau porte déjà ses marges de cellule, celles de la carte s'y ajoutaient.
        <div className="card overflow-hidden p-0">
          {/* `compact` : le catalogue se parcourt par dizaines de lignes, contrairement aux
              tableaux de pièces (commandes, factures) que l'on lit une par une. La ligne
              entière ouvre la fiche, d'où le `stopPropagation` sur chaque action. */}
          <Table
            columns={columns}
            data={displayedProducts}
            loading={loading}
            density="compact"
            sortKey={sortConfig.key}
            sortDirection={sortConfig.direction}
            onSort={handleSort}
            onRowClick={handleViewDetails}
            actions={(product) => (
              <>
                <button
                  onClick={(e) => { e.stopPropagation(); handleViewDetails(product); }}
                  className="text-gray-600 hover:text-gray-900 p-1.5 hover:bg-gray-100 rounded-lg transition-colors"
                  title={t('common.viewDetails')}
                >
                  <Eye className="w-4 h-4" />
                </button>
                {/* Le caissier est en lecture seule sur les produits : seules les actions
                    « Modifier » et « Supprimer » sont réservées à l'ADMIN. */}
                {isAdmin && (
                  <>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleEdit(product); }}
                      className="text-primary-600 hover:text-primary-900 p-1.5 hover:bg-primary-50 rounded-lg transition-colors"
                      title={t('common.edit')}
                    >
                      <Edit className="w-4 h-4" />
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDelete(product.id); }}
                      className="text-red-600 hover:text-red-900 p-1.5 hover:bg-red-50 rounded-lg transition-colors"
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
        // Une colonne de plus au-delà de 1536 px et des écarts resserrés : la vignette carrée
        // ne laissait voir qu'une rangée et demie sur un écran de bureau courant.
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-4">
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
                // Décalage plafonné : à 50 ms par carte, une page de cent produits mettait cinq
                // secondes à finir de s'afficher. Au-delà de la huitième, tout entre ensemble.
                transition={{ delay: Math.min(index, 8) * 0.05 }}
                onClick={() => handleViewDetails(product)}
                className="card p-4 hover:shadow-lg transition-shadow cursor-pointer group"
              >
                {/* Vignette en 4/3 plutôt que carrée : elle occupait deux fois la hauteur du
                    texte utile, pour une photo de catalogue rarement décisive. */}
                <div className="relative aspect-[4/3] bg-gradient-to-br from-gray-100 to-gray-200 rounded-lg mb-3 overflow-hidden">
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
                <div className="space-y-1.5">
                  <div className="min-w-0">
                    <h3 className="line-clamp-1 text-sm font-semibold text-gray-900 dark:text-gray-100" title={product.name}>
                      {product.name}
                    </h3>
                    <p className="font-mono text-xs text-gray-500 dark:text-gray-400">{product.code}</p>
                  </div>

                  {/* Bloc toujours rendu, à hauteur de deux lignes : sans lui, les cartes sans
                      description remontaient leur prix et leur stock d'un cran, et rien ne
                      s'alignait plus d'une carte à l'autre. Texte complet en infobulle. */}
                  <p
                    className="line-clamp-2 min-h-[1.875rem] text-xs leading-tight text-gray-500 dark:text-gray-400"
                    title={product.description || undefined}
                  >
                    {product.description || ''}
                  </p>

                  {product.category && (
                    <span className="badge badge-info">
                      {product.category.name}
                    </span>
                  )}

                  <div className="pt-2 border-t border-gray-200 dark:border-gray-700">
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs text-gray-600 dark:text-gray-400">{t('products.sellingPrice')}</span>
                      <span className="font-bold text-primary-600 dark:text-primary-400 tabular-nums">
                        {formatCurrency(product.sellingPrice)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-gray-600 dark:text-gray-400">{t('products.stock')}</span>
                      <span className={`text-sm font-semibold tabular-nums ${
                        product.stockQuantity === 0
                          ? 'text-red-600 dark:text-red-400'
                          : product.stockQuantity < product.minStockAlert
                          ? 'text-amber-600 dark:text-amber-400'
                          : 'text-green-600 dark:text-green-400'
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
        onClose={requestCloseForm}
        title={editingProduct ? t('products.editTitle') : t('products.newTitle')}
        size="lg"
      >
        {/* `noValidate` : la validation est celle du formulaire, pas celle du navigateur, dont
            les bulles natives s'affichent hors de la charte et dans la langue du navigateur. */}
        <form onSubmit={handleSubmit} noValidate>
          {/* Récapitulatif des champs à corriger : sur un formulaire de cette hauteur, le champ
              fautif peut se trouver hors écran au moment de l'enregistrement. */}
          {submitAttempted && invalidFields.length > 0 && (
            <div
              role="alert"
              className="mb-6 flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-500/30 dark:bg-red-500/10"
            >
              <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-red-600 dark:text-red-400" aria-hidden="true" />
              <div className="min-w-0 text-sm">
                <p className="font-semibold text-red-800 dark:text-red-300">
                  {t('products.formErrorTitle', { count: invalidFields.length })}
                </p>
                <ul className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-red-700 dark:text-red-300/90">
                  {invalidFields.map(field => (
                    <li key={field}>
                      <button
                        type="button"
                        onClick={() => focusField(field)}
                        className="underline underline-offset-2 hover:no-underline"
                      >
                        {fieldLabels[field]}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          )}

          {editingProduct && (
            <div className="mb-6 flex items-center gap-3 rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/40">
              <Package className="w-5 h-5 shrink-0 text-gray-400" aria-hidden="true" />
              <div className="min-w-0">
                <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                  {t('products.productCodeLabel')}<span className="font-mono">{editingProduct.code}</span>
                </p>
                <p className="text-xs text-gray-500 dark:text-gray-400">{t('products.codeImmutable')}</p>
              </div>
            </div>
          )}

          {/* Ordre de lecture : ce qu'est le produit, ce qu'il coûte et rapporte, ce qu'il en
              reste, comment on l'identifie en caisse, puis son illustration et son statut. */}
          <div className="divide-y divide-gray-200 dark:divide-gray-700">
            <FormSection
              icon={Package}
              title={t('products.sectionIdentity')}
              description={t('products.sectionIdentityHint')}
            >
              <FormInput
                label={t('products.nameLabel')}
                name="name"
                value={formData.name}
                onChange={handleInputChange}
                onBlur={handleBlur}
                placeholder={t('products.namePlaceholder')}
                error={visibleErrors.name}
                maxLength={MAX_LENGTHS.name}
                required
                icon={Package}
              />
              <FormInput
                label={t('common.description')}
                name="description"
                type="textarea"
                rows={3}
                value={formData.description}
                onChange={handleInputChange}
                placeholder={t('products.descriptionPlaceholder')}
              />
              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
                <FormSelect
                  label={t('products.categoryLabel')}
                  name="categoryId"
                  value={formData.categoryId}
                  onChange={handleInputChange}
                  error={visibleErrors.categoryId}
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
              </div>
            </FormSection>

            <FormSection
              icon={Euro}
              title={t('products.sectionPricing')}
              description={t('products.sectionPricingHint')}
            >
              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
                <FormInput
                  label={t('products.purchasePriceLabel')}
                  name="purchasePrice"
                  type="number"
                  step="0.01"
                  min="0"
                  value={formData.purchasePrice}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  placeholder="0.00"
                  error={visibleErrors.purchasePrice}
                  required
                  icon={Euro}
                />
                <FormInput
                  label={t('products.sellingPriceLabel')}
                  name="sellingPrice"
                  type="number"
                  step="0.01"
                  min="0"
                  value={formData.sellingPrice}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  placeholder="0.00"
                  error={visibleErrors.sellingPrice}
                  required
                  icon={Euro}
                />
              </div>
              {/* Marge calculée à la saisie : c'est la vérification qu'on fait de tête en
                  remplissant les deux prix. Vendre à perte reste autorisé (déstockage), on
                  le signale sans l'interdire. */}
              {margin && (
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-700 dark:bg-gray-900/40">
                  <span className="text-sm text-gray-600 dark:text-gray-400">{t('products.grossMargin')}</span>
                  <span className="flex items-baseline gap-2 tabular-nums">
                    <span className="text-base font-bold text-gray-900 dark:text-gray-100">
                      {formatCurrency(margin.amount)}
                    </span>
                    <span className="text-sm text-gray-500 dark:text-gray-400">
                      {formatPercent(margin.rate)}
                    </span>
                  </span>
                  {margin.amount < 0 && (
                    <p className="w-full text-xs text-amber-600 dark:text-amber-400">
                      {t('products.marginNegativeHint')}
                    </p>
                  )}
                </div>
              )}
            </FormSection>

            <FormSection
              icon={AlertTriangle}
              title={t('products.sectionStock')}
              description={t('products.sectionStockHint')}
            >
              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
                <FormInput
                  label={t('products.stockQuantityLabel')}
                  name="stockQuantity"
                  type="number"
                  min="0"
                  step="1"
                  value={formData.stockQuantity}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  placeholder={t('products.quantityPlaceholder')}
                  error={visibleErrors.stockQuantity}
                  required
                  icon={Package}
                />
                <FormInput
                  label={t('products.minStockAlertLabel')}
                  name="minStockAlert"
                  type="number"
                  min="0"
                  step="1"
                  value={formData.minStockAlert}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  placeholder={t('products.minStockPlaceholder')}
                  error={visibleErrors.minStockAlert}
                  hint={t('products.minStockHint')}
                  required
                  icon={AlertTriangle}
                />
              </div>
            </FormSection>

            <FormSection
              icon={Barcode}
              title={t('products.barcodeLabel')}
              description={t('products.sectionBarcodeHint')}
            >
              <FormInput
                label={t('products.barcodeLabel')}
                name="barcode"
                value={formData.barcode}
                onChange={handleInputChange}
                onBlur={handleBlur}
                placeholder="1234567890123"
                error={visibleErrors.barcode}
                maxLength={MAX_LENGTHS.barcode}
                icon={Barcode}
              />
            </FormSection>

            <FormSection
              icon={ImageIcon}
              title={t('products.productImageLabel')}
              description={t('products.sectionImageHint')}
            >
              <div className="flex flex-wrap items-start gap-4">
                <div className="w-32 h-32 shrink-0 border-2 border-dashed border-gray-300 rounded-lg overflow-hidden bg-gray-50 flex items-center justify-center dark:border-gray-600 dark:bg-gray-900/40">
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
                        title={t('common.remove')}
                        aria-label={t('common.remove')}
                        className="absolute top-1 right-1 bg-red-500 text-white p-1 rounded-full opacity-0 group-hover:opacity-100 focus-visible:opacity-100 transition-opacity"
                      >
                        <X className="w-4 h-4" aria-hidden="true" />
                      </button>
                    </div>
                  ) : (
                    <ImageIcon className="w-12 h-12 text-gray-400" aria-hidden="true" />
                  )}
                </div>

                <div className="flex-1 min-w-[14rem] space-y-3">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleImageUpload}
                    className="hidden"
                    id="image-upload"
                  />
                  <label htmlFor="image-upload" className="quick-action cursor-pointer">
                    <Upload className="w-4 h-4" aria-hidden="true" />
                    {t('products.chooseImageButton')}
                  </label>
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    {t('products.imageFormatsHelp')}
                  </p>
                  <FormInput
                    label={t('products.imageUrlLabel')}
                    name="imageUrl"
                    value={formData.imageUrl}
                    onChange={handleInputChange}
                    placeholder={t('products.imageUrlPlaceholder')}
                  />
                </div>
              </div>
            </FormSection>

            <FormSection
              icon={Tag}
              title={t('products.sectionStatus')}
              description={t('products.sectionStatusHint')}
            >
              {/* Interrupteur plutôt qu'une case à cocher : l'effet du réglage est écrit à côté,
                  un produit inactif ne pouvant plus être ajouté à une commande. */}
              <div className="flex items-center justify-between gap-4 rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/40">
                <div className="min-w-0">
                  <label htmlFor="active" className="cursor-pointer font-medium text-gray-900 dark:text-gray-100">
                    {t('products.activeLabel')}
                  </label>
                  <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
                    {formData.active ? t('products.activeStateHint') : t('products.inactiveStateHint')}
                  </p>
                </div>
                <label className="relative inline-flex flex-shrink-0 cursor-pointer items-center">
                  <input
                    type="checkbox"
                    id="active"
                    name="active"
                    checked={formData.active}
                    onChange={handleInputChange}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-gray-200 dark:bg-gray-600 peer-focus-visible:ring-2 peer-focus-visible:ring-primary-500 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"></div>
                </label>
              </div>
            </FormSection>
          </div>

          {/* Barre d'actions collée au bas de la modale : sur un écran court, le formulaire
              défile mais l'enregistrement reste sous la main. */}
          <div className="sticky bottom-0 -mx-6 -mb-6 mt-6 flex flex-col-reverse gap-3 border-t border-gray-200 bg-white/95 px-6 py-4 backdrop-blur sm:flex-row sm:items-center sm:justify-between dark:border-gray-700 dark:bg-gray-800/95">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              {editingProduct && !isDirty ? t('products.noChanges') : t('clients.requiredHint')}
            </p>
            <div className="flex items-center justify-end gap-3">
              <Button variant="secondary" onClick={requestCloseForm} type="button">
                {t('common.cancel')}
              </Button>
              <Button variant="primary" type="submit" loading={saving} disabled={!canSubmit}>
                {editingProduct ? t('common.saveChanges') : t('common.create')}
              </Button>
            </div>
          </div>
        </form>
      </Modal>

      {/* Confirmations */}
      <ConfirmModal
        isOpen={showDiscardConfirm}
        onClose={() => setShowDiscardConfirm(false)}
        onConfirm={closeForm}
        title={t('clients.discardTitle')}
        message={t('clients.discardMessage')}
        type="warning"
        confirmLabel={t('clients.discardConfirm')}
        cancelLabel={t('clients.discardCancel')}
      />

      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmSubmit}
        title={editingProduct ? t('products.confirmEdit') : t('products.confirmCreate')}
        message={editingProduct
          ? t('products.confirmEditMessage', { name: formData.name })
          : t('products.confirmCreateMessage', { name: formData.name })}
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
