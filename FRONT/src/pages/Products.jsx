import { useState, useEffect, useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'react-hot-toast';
import { Plus, Edit, Trash2, Package, AlertTriangle, RefreshCw, FolderTree, Upload, Download, TrendingUp, TrendingDown, Euro, Grid3x3, List, Image as ImageIcon, X, Eye, Barcode, Tag, Calendar, Hash } from 'lucide-react';
import { motion } from 'framer-motion';
import { useAuth } from '../context/useAuth';
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
import { formatCurrency, formatPercent, productShortName } from '../utils/format';
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

/** Longueurs max, les mêmes que les `@Size` de `ProductRequest`. */
const MAX_LENGTHS = { name: 200, barcode: 50 };

/**
 * Colonnes triables, avec le chemin de l'entité utilisé pour le `sort` de Spring Data.
 *
 * Seule `category` a un nom différent : le tableau affiche le nom de la catégorie, alors que
 * l'entité a une relation. Les autres ont le même nom des deux côtés, elles sont là pour
 * avoir la liste des tris autorisés (un `sort` sur un champ qui n'existe pas fait une 500).
 */
const SORT_FIELDS = {
  name: 'name',
  category: 'category.name',
  sellingPrice: 'sellingPrice',
  stockQuantity: 'stockQuantity',
  active: 'active',
  code: 'code',
};

/** Ordre des champs à l'écran : sert à savoir lequel prend le focus s'il y a plusieurs erreurs. */
const FIELD_ORDER = [
  'name', 'categoryId', 'unit', 'purchasePrice', 'sellingPrice',
  'stockQuantity', 'minStockAlert', 'barcode',
];

const isBlank = (value) => value === '' || value === null || value === undefined;

/**
 * Valide tout le formulaire et renvoie les erreurs par champ.
 * Mêmes règles que `ProductRequest` : nom obligatoire, prix obligatoires et >= 0,
 * stock et seuil d'alerte >= 0.
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
 * Marge brute du produit. On a le droit de vendre à perte (déstockage, promo), donc la marge
 * est juste affichée comme indication, ce n'est pas une erreur de validation.
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
  // Côté backend, create/update/delete/import/export sont déjà réservés aux ADMIN
  // (voir ProductController @PreAuthorize("hasRole('ADMIN')")). On cache ces boutons
  // au CAISSIER pour éviter les 403 et ne pas lui montrer des actions qu'il ne peut
  // pas faire.
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
    // Mode d'affichage lu dans le localStorage, 'list' par défaut
    return localStorage.getItem('productsViewMode') || 'list';
  });
  const [imagePreview, setImagePreview] = useState(null);
  const fileInputRef = useRef(null);
  const [currentPage, setCurrentPage] = useState(1);
  // Par défaut on affiche le plus de produits possible par page (100, le max du sélecteur).
  const [itemsPerPage, setItemsPerPage] = useState(100);
  const [formData, setFormData] = useState(EMPTY_FORM);
  // Valeurs au moment de l'ouverture, pour savoir si le formulaire a été modifié
  // (bouton enregistrer désactivé sinon, confirmation à la fermeture).
  const [initialForm, setInitialForm] = useState(EMPTY_FORM);
  const [touched, setTouched] = useState({});
  const [submitAttempted, setSubmitAttempted] = useState(false);
  // Erreurs renvoyées par l'API (`fieldErrors` du GlobalExceptionHandler), gardées à part des
  // erreurs locales : elles ne sont pas recalculées à la frappe et partent champ par champ.
  const [serverErrors, setServerErrors] = useState({});
  const [showDiscardConfirm, setShowDiscardConfirm] = useState(false);
  // État d'enregistrement séparé du chargement : `loading` gère aussi le squelette du
  // tableau, qui n'a pas à clignoter pendant une sauvegarde.
  const [saving, setSaving] = useState(false);
  // Nombre total de résultats, renvoyé par le serveur (on ne peut plus le déduire de
  // `products`, qui ne contient que la page affichée).
  const [pageMeta, setPageMeta] = useState({ totalElements: 0, totalPages: 1 });
  // Compteurs en haut de page. Ils portent sur TOUT le catalogue et viennent d'un appel à
  // part : calculés sur la page, on afficherait « 3 ruptures » pour la page ouverte seulement.
  const [summary, setSummary] = useState({ total: 0, outOfStock: 0, lowStock: 0, stockValue: 0 });

  // Debounce sur la saisie, sinon chaque caractère lancerait une requête. 300 ms c'est à peu
  // près la limite avant que l'utilisateur sente un délai.
  const [debouncedSearch, setDebouncedSearch] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchTerm.trim()), 300);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  // Paramètres envoyés au serveur. `page` commence à 1 dans l'interface mais à 0 dans
  // Spring Data.
  const queryParams = useMemo(() => {
    const params = { page: currentPage - 1, size: itemsPerPage };
    if (debouncedSearch) params.search = debouncedSearch;
    if (sortConfig.key) params.sort = `${SORT_FIELDS[sortConfig.key] ?? sortConfig.key},${sortConfig.direction}`;
    return params;
  }, [currentPage, itemsPerPage, debouncedSearch, sortConfig]);

  useEffect(() => {
    fetchCategories();
    fetchSummary();
  }, []);

  useEffect(() => {
    fetchProducts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryParams]);

  // Si la recherche ou le tri change, on revient à la page 1 : rester en page 7 alors qu'il
  // n'y en a plus que 2 afficherait un tableau vide.
  useEffect(() => {
    setCurrentPage(1);
  }, [debouncedSearch, sortConfig, itemsPerPage]);

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
      const { data } = await productService.searchProducts(queryParams);
      setProducts(data.content || []);
      setPageMeta({
        totalElements: data.totalElements ?? 0,
        totalPages: Math.max(1, data.totalPages ?? 1),
      });
    } catch (error) {
      console.error('Error fetching products:', error);
      toast.error(t('products.loadError'));
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  /** Compteurs en haut de page : à recharger après chaque modif (une création change le total). */
  const fetchSummary = async () => {
    try {
      const { data } = await productService.getCatalogSummary();
      setSummary(data);
    } catch (error) {
      console.error('Error fetching product summary:', error);
    }
  };

  /** Recharge la page en cours ET les compteurs. À appeler après création, modif ou suppression. */
  const refresh = async () => {
    await Promise.all([fetchProducts(), fetchSummary()]);
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
    // L'erreur du serveur concernait l'ancienne valeur, on l'enlève dès que la valeur change.
    setServerErrors(prev => (prev[name] === undefined ? prev : { ...prev, [name]: undefined }));
  };

  // L'erreur s'affiche quand on quitte le champ, ou au premier essai d'enregistrement :
  // l'afficher dès la première lettre serait pénible.
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
      // On construit le body champ par champ au lieu de faire `...formData` : en modification,
      // il contenait aussi `id`, `code`, `createdAt`, `category`… que `ProductRequest` ne connaît pas.
      const productData = {
        name: formData.name.trim(),
        description: (formData.description || '').trim() || null,
        categoryId: formData.categoryId ? parseInt(formData.categoryId) : null,
        purchasePrice: Number(formData.purchasePrice),
        sellingPrice: Number(formData.sellingPrice),
        minStockAlert: parseInt(formData.minStockAlert, 10),
        unit: formData.unit,
        barcode: (formData.barcode || '').trim() || null,
        imageUrl: (formData.imageUrl || '').trim() || null,
        active: formData.active,
      };

      // Le stock n'est envoyé qu'à la création (stock de départ). En modification, le formulaire
      // contient la valeur lue à l'ouverture : la renvoyer remettrait le stock d'avant les ventes
      // faites entre-temps. Ensuite le stock ne change que par les ventes, les retours et les
      // ajustements de la page Stock (le backend ignore d'ailleurs ce champ sur un PUT).
      if (!editingProduct) {
        productData.stockQuantity = parseInt(formData.stockQuantity, 10);
      }

      if (editingProduct) {
        await productService.updateProduct(editingProduct.id, productData);
        toast.success(t('products.updatedSuccess'));
      } else {
        await productService.createProduct(productData);
        toast.success(t('products.createdSuccess'));
      }

      await refresh();
      closeForm();
    } catch (error) {
      console.error('Error saving product:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string'
        ? raw
        : (raw?.message || raw?.error || t('products.saveError'));

      // L'erreur du serveur est affichée sur le champ concerné plutôt que dans un toast,
      // comme ça on voit directement quoi corriger.
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

  /** Ouvre le formulaire avec des valeurs données et des erreurs remises à zéro. */
  const openForm = (product) => {
    // On ne garde que les champs du formulaire : l'objet de l'API contient aussi `id`,
    // `code`, `createdAt`… dont le DTO de requête n'a pas besoin.
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
        await refresh();
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
   * Fermeture par l'utilisateur (bouton Annuler, croix, clic à côté).
   * Un clic à côté suffit à fermer le modal, et perdre une fiche produit complète comme ça,
   * c'est vraiment énervant.
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
      // Vérifie le type de fichier
      if (!file.type.startsWith('image/')) {
        toast.error(t('products.selectValidImage'));
        return;
      }

      // Vérifie la taille du fichier (max 5 Mo)
      if (file.size > 5 * 1024 * 1024) {
        toast.error(t('products.imageSizeLimit'));
        return;
      }

      // Crée l'aperçu de l'image
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

  /**
   * Export du catalogue, généré par le serveur.
   *
   * Avant, le fichier était construit ici en collant les valeurs avec des virgules, à partir de
   * la liste complète chargée dans le navigateur. Comme on ne charge plus toute la liste, on
   * n'aurait exporté que la page affichée (un fichier incomplet).
   *
   * `GET /products/export` existait déjà et fait mieux : il a tout le catalogue, et passe par
   * CsvExportService qui échappe les valeurs et ajoute le BOM pour Excel (ce que la version
   * d'ici ne faisait pas).
   */
  const handleExport = async () => {
    try {
      const response = await productService.exportProducts();
      const url = window.URL.createObjectURL(response.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${t('products.exportFilename')}_${new Date().toISOString().split('T')[0]}.csv`;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Error exporting products:', error);
      toast.error(t('products.exportError'));
    }
  };

  // Suggestions d'autocomplétion triées par pertinence (le nom passe avant le code, le
  // code-barres et la catégorie).
  //
  // Elles viennent de la page reçue, puisqu'on ne charge plus tout le catalogue. Ce n'est pas
  // gênant : la page est déjà le résultat de la recherche, donc les suggestions portent sur
  // les mêmes lignes qu'avant. Le serveur cherche sur les mêmes colonnes, et la frappe
  // suivante met à jour les suggestions.
  const productSuggestions = rankSuggestions(
    products,
    searchTerm,
    (p) => [p.name, p.code, p.barcode, p.category?.name],
    8
  );

  // Le tri et la pagination sont faits en base : `products` contient déjà la bonne page dans
  // le bon ordre. Trier ou découper ici ne trierait que les lignes reçues, ce qui donnerait un
  // faux tri (le piège classique quand on passe à la pagination serveur).
  const displayedProducts = products;
  const totalPages = pageMeta.totalPages;

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const handleItemsPerPageChange = (newItemsPerPage) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1);
  };

  // Compteurs sur tout le catalogue, calculés en base (voir /api/products/summary). Avant ils
  // étaient calculés sur la liste complète chargée dans le navigateur ; maintenant ils ne
  // porteraient que sur la page affichée.
  const totalProducts = summary.total;
  const outOfStockCount = summary.outOfStock;
  const lowStockCount = summary.lowStock;
  const stockValue = Number(summary.stockValue) || 0;

  /* Colonnes du catalogue, limitées à l'essentiel : le produit, la catégorie, le prix, le
   * stock et l'état. Le reste est dans la fiche détaillée.
   *
   * Ce qu'on a enlevé :
   *   - la description : texte libre de longueur variable, qui prenait un tiers de la largeur
   *     et doublait la hauteur des lignes ;
   *   - le code produit : il sert à chercher, pas à lire la liste. On peut toujours le
   *     chercher, et il est affiché dans les suggestions, la fiche et l'export CSV.
   *
   * Le nom est sur une ligne, coupé avec `truncate`, avec une largeur max (`max-w`). Sans
   * cette largeur max, `truncate` (qui met `nowrap`) faisait s'élargir le tableau avec les
   * noms très longs au lieu de les couper. Le nom complet est dans l'infobulle et la fiche.
   *
   * La largeur max est assez petite (10 → 15 rem selon l'écran, ~30 à ~45 caractères) : on
   * vise les noms normaux, pas le plus long du catalogue. Un nom peut faire jusqu'à 200
   * caractères (`MAX_LENGTHS.name`) et poussait le prix et le stock hors de l'écran. Les
   * premiers mots suffisent pour reconnaître l'article.
   *
   * Avant de couper, `productShortName` enlève les parenthèses du nom (c'est plutôt du détail
   * technique). Attention : deux variantes qui ne diffèrent que par là (« ... (33 cl) » /
   * « ... (50 cl) ») s'affichent pareil, seules l'infobulle et la fiche les distinguent.
   */
  const columns = [
    {
      key: 'name',
      label: t('common.product'),
      sortable: true,
      render: (product) => (
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 shrink-0 overflow-hidden rounded-md bg-gray-100 dark:bg-gray-700 flex items-center justify-center">
            {product.imageUrl ? (
              <img src={product.imageUrl} alt="" className="w-full h-full object-cover" />
            ) : (
              <Package className="w-4 h-4 text-gray-400" aria-hidden="true" />
            )}
          </div>
          <span
            className="block min-w-0 max-w-[10rem] truncate font-medium text-gray-900 sm:max-w-[12rem] lg:max-w-[14rem] xl:max-w-[15rem] dark:text-gray-100"
            title={product.name}
          >
            {productShortName(product.name)}
          </span>
        </div>
      )
    },
    {
      // La catégorie s'affiche dès `md` : maintenant que la colonne code n'est plus là, elle rentre sur tablette.
      key: 'category',
      label: t('products.category'),
      sortable: true,
      className: 'hidden md:table-cell',
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
        {/* `flex-wrap` comme sur les autres en-têtes : les quatre boutons faisaient 540 px
            dans un en-tête de 334 px, et la page scrollait horizontalement. */}
        <div className="flex flex-wrap items-center gap-3">
          <Button
            variant="outline"
            icon={RefreshCw}
            onClick={fetchProducts}
            loading={loading}
          >
            {t('common.refresh')}
          </Button>
          {isAdmin && (
            <>
              <Button
                variant="secondary"
                icon={Upload}
                onClick={() => toast(t('common.comingSoon'), { icon: 'ℹ️' })}
              >
                {t('common.import')}
              </Button>
              <Button
                variant="secondary"
                icon={Download}
                onClick={handleExport}
              >
                {t('common.export')}
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

      {/* Stats : on utilise `StatCard` comme sur les autres pages plutôt qu'une version à
          part. La bande du haut est aussi moins haute, pour laisser la place au catalogue. */}
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
                    {t('products.code')} : {p.code}{p.barcode ? ` · ${p.barcode}` : ''}
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
        // `p-0` : le tableau a déjà ses marges dans les cellules, celles de la carte s'ajoutaient.
        <div className="card overflow-hidden p-0">
          {/* `compact` : on parcourt le catalogue par dizaines de lignes, pas comme les
              commandes ou factures qu'on lit une par une. Cliquer sur la ligne ouvre la fiche,
              d'où le `stopPropagation` sur chaque bouton. */}
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
                {/* Le caissier ne peut que consulter les produits : « Modifier » et
                    « Supprimer » sont réservés à l'ADMIN. */}
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

          {/* Pagination : le total vient du serveur, on n'affiche qu'une partie. */}
          {pageMeta.totalElements > 0 && (
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalItems={pageMeta.totalElements}
              itemsPerPage={itemsPerPage}
              onPageChange={handlePageChange}
              onItemsPerPageChange={handleItemsPerPageChange}
            />
          )}
        </div>
      ) : (
        // Une colonne de plus au-dessus de 1536 px et moins d'espace entre les cartes : avec
        // les images carrées on ne voyait qu'une rangée et demie sur un écran de bureau.
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-4">
          {displayedProducts.length === 0 ? (
            <div className="col-span-full text-center py-12 text-gray-500">
              {t('products.noProducts')}
            </div>
          ) : (
            displayedProducts.map((product, index) => (
              <motion.div
                key={product.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                // Délai limité : à 50 ms par carte, une page de cent produits mettait cinq
                // secondes à s'afficher. Après la huitième carte, tout apparaît en même temps.
                transition={{ delay: Math.min(index, 8) * 0.05 }}
                onClick={() => handleViewDetails(product)}
                className="card p-4 hover:shadow-lg transition-shadow cursor-pointer group"
              >
                {/* Image en 4/3 au lieu de carrée : elle prenait deux fois la hauteur du texte,
                    alors que la photo n'est pas l'info la plus importante. */}
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
                        {t('products.outOfStockShort')}
                      </span>
                    </div>
                  )}
                  {product.stockQuantity > 0 && product.stockQuantity < product.minStockAlert && (
                    <div className="absolute top-2 left-2">
                      <span className="badge-warning">
                        <AlertTriangle className="w-3 h-3" />
                        {t('products.lowStockShort')}
                      </span>
                    </div>
                  )}
                  {/* Boutons d'action affichés au survol (stopPropagation pour ne pas rouvrir le détail via le clic sur la carte) */}
                  <div className="absolute inset-0 bg-black bg-opacity-50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2">
                    <button
                      onClick={(e) => { e.stopPropagation(); handleViewDetails(product); }}
                      className="bg-white text-gray-700 p-3 rounded-lg hover:bg-gray-100 transition-colors"
                      title={t('common.viewDetails')}
                    >
                      <Eye className="w-5 h-5" />
                    </button>
                    {/* Caissier en lecture seule : modifier et supprimer réservés à l'ADMIN. */}
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
                  {/* Nom sur une ligne, coupé : la carte a une largeur fixe. Le nom complet est
                      dans l'infobulle et la fiche. */}
                  <h3 className="truncate text-sm font-semibold text-gray-900 dark:text-gray-100" title={product.name}>
                    {productShortName(product.name)}
                  </h3>

                  {/* Pas de description ni de code ici non plus. Par contre la ligne de la
                      catégorie est toujours là : sans elle, les cartes sans catégorie avaient le
                      prix et le stock plus haut que les autres, et plus rien n'était aligné. */}
                  <div className="min-h-[1.375rem]">
                    {product.category && (
                      <span className="badge badge-info">
                        {product.category.name}
                      </span>
                    )}
                  </div>

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

          {/* Pagination : le total vient du serveur, on n'affiche qu'une partie. */}
          {pageMeta.totalElements > 0 && (
            <div className="col-span-full mt-6">
              <div className="card">
                <Pagination
                  currentPage={currentPage}
                  totalPages={totalPages}
                  totalItems={pageMeta.totalElements}
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
        {/* `noValidate` : on utilise notre propre validation, pas les bulles du navigateur
            (pas au style de l'appli et dans la langue du navigateur). */}
        <form onSubmit={handleSubmit} noValidate>
          {/* Liste des champs à corriger : le formulaire est long, le champ en erreur peut
              être hors de l'écran au moment d'enregistrer. */}
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

          {/* Ordre : description du produit, prix et marge, stock, identification en caisse,
              puis image et statut. */}
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
              {/* Marge calculée pendant la saisie : c'est ce qu'on vérifie de tête en
                  remplissant les deux prix. On peut vendre à perte (déstockage), donc on
                  affiche juste un avertissement. */}
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
                {/* En modification, le champ est juste informatif : le stock dépend des
                    mouvements (ventes, retours, ajustements) et se corrige depuis la page Stock. */}
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
                  hint={editingProduct ? t('products.stockManagedHint') : undefined}
                  required={!editingProduct}
                  disabled={!!editingProduct}
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
              {/* Switch plutôt que checkbox, avec l'effet écrit à côté : un produit inactif
                  ne peut plus être ajouté à une commande. */}
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

          {/* Boutons collés en bas du modal : sur un petit écran le formulaire scrolle mais
              le bouton enregistrer reste visible. */}
          <div className="sticky -bottom-6 -mx-6 -mb-6 mt-6 flex flex-col-reverse gap-3 border-t border-gray-200 bg-white/95 px-6 py-4 backdrop-blur sm:flex-row sm:items-center sm:justify-between dark:border-gray-700 dark:bg-gray-800/95">
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
            // Caissier en lecture seule : sans onEdit, le bouton « Modifier » n'est pas affiché.
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
  // Format de date de la langue choisie (voir `export.locale`).
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
      {/* Header : image + nom */}
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
          <Euro className="w-4 h-4" /> {t('products.sectionPricing')}
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
          <Package className="w-4 h-4" /> {t('products.sectionStock')}
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
          <span>{t('products.createdAtLabel')}&nbsp;: {formatDateTime(product.createdAt)}</span>
        </div>
        <div className="flex items-center gap-2">
          <Calendar className="w-4 h-4 text-gray-400" />
          <span>{t('products.updatedAtLabel')}&nbsp;: {formatDateTime(product.updatedAt)}</span>
        </div>
      </div>

      <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200">
        <Button variant="secondary" type="button" onClick={onClose}>
          {t('common.close')}
        </Button>
        {/* « Modifier » s'affiche seulement si onEdit est fourni (ADMIN). Le caissier ne
            voit que « Fermer ». */}
        {onEdit && (
          <Button variant="primary" type="button" icon={Edit} onClick={onEdit}>
            {t('common.edit')}
          </Button>
        )}
      </div>
    </div>
  );
};

export default Products;
