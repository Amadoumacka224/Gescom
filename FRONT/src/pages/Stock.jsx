import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Warehouse,
  Package,
  Euro,
  AlertTriangle,
  PackageX,
  RefreshCw,
  Plus,
  Minus,
  Edit2,
  RotateCcw,
  TrendingUp,
  TrendingDown,
  Boxes,
  ArrowRightLeft,
  Eye,
  X,
  ShieldAlert,
} from 'lucide-react';
import { toast } from 'react-hot-toast';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';
import Table from '../components/Table';
import Pagination from '../components/Pagination';
import FormInput from '../components/FormInput';
import SearchBox from '../components/SearchBox';
import SearchableSelect from '../components/SearchableSelect';
import SegmentedFilter from '../components/SegmentedFilter';
import StatCard from '../components/StatCard';
import Button from '../components/Button';
import ReturnWizard from '../components/ReturnWizard';
import ReturnDetails from '../components/ReturnDetails';
import { normalizeText, rankSuggestions } from '../utils/searchSuggestions';
import { formatCurrency, formatDate, formatTime } from '../utils/format';
import { STOCK_MOVEMENT_TONE, badgeClass } from '../constants/statusBadges';

/* Types de mouvement, dans l'ordre du flux métier. Les libellés passent par i18n ;
 * la teinte vient de `STOCK_MOVEMENT_TONE`, partagée avec les autres écrans. */
const MOVEMENT_TYPES = [
  { value: 'STOCK_IN', icon: TrendingUp },
  { value: 'STOCK_OUT', icon: TrendingDown },
  { value: 'ADJUSTMENT', icon: Edit2 },
  { value: 'RETURN', icon: RotateCcw },
  { value: 'DAMAGE', icon: AlertTriangle },
  { value: 'TRANSFER', icon: ArrowRightLeft },
];

const MOVEMENT_ICONS = Object.fromEntries(MOVEMENT_TYPES.map((m) => [m.value, m.icon]));

/* Colonnes triables du grand livre et champ trié en base. `delta` n'y figure pas : la
 * variation est calculée à l'affichage (newStock - previousStock) et n'existe pas en base. */
const MOVEMENT_SORT_FIELDS = {
  createdAt: 'createdAt',
  product: 'product.name',
  type: 'type',
  quantity: 'quantity',
};

/* Colonnes triables du registre des retours et champ trié en base. */
const RETURN_SORT_FIELDS = {
  createdAt: 'createdAt',
  returnNumber: 'returnNumber',
  order: 'order.orderNumber',
  totalQuantity: 'totalQuantity',
  refundAmount: 'refundAmount',
};

/* Les opérations de stock saisies à la main, avec leur endpoint et le champ de quantité
 * attendu. `sign` sert au calcul du stock projeté affiché avant validation.
 *
 * Le retour client n'en fait plus partie : il ne se saisit pas produit par produit mais part
 * de la vente d'origine (cf. ReturnWizard), seul moyen de contrôler les quantités rendues et
 * de rattacher le mouvement à son document. */
const OPERATIONS = {
  add: { endpoint: '/stock/add', icon: Plus, sign: 1, withUnitCost: true, withReference: true },
  remove: { endpoint: '/stock/remove', icon: Minus, sign: -1, withReference: true },
  adjust: { endpoint: '/stock/adjust', icon: Edit2, sign: 0 },
  // Pas de référence pour un dommage : `StockDamageRequest` n'expose pas ce champ et
  // `recordDamage` ne le prend pas. Le formulaire l'affichait pourtant, et la valeur
  // saisie était silencieusement perdue — le n° de constat va dans « Raison ».
  damage: { endpoint: '/stock/damage', icon: AlertTriangle, sign: -1 },
};

const EMPTY_FORM = {
  productId: '',
  quantity: '',
  newQuantity: '',
  unitCost: '',
  reason: '',
  reference: '',
};

/* État de stock d'un produit. Les deux seuils sont volontairement disjoints — un produit
 * épuisé est « en rupture », pas « en stock faible » — et la comparaison est stricte, comme
 * la requête `stockQuantity < minStockAlert` du backend. */
const stockStatus = (product) => {
  const quantity = product.stockQuantity ?? 0;
  const threshold = product.minStockAlert ?? 0;
  if (quantity <= 0) return 'out';
  if (quantity < threshold) return 'low';
  return 'ok';
};

const stockValueOf = (product) => (product.stockQuantity ?? 0) * (product.purchasePrice ?? 0);

const Stock = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  // Gestion du stock = réservée ADMIN (la barre latérale la masque déjà au CAISSIER,
  // le backend la refuse). Garde defense-in-depth si l'URL est tapée à la main.
  const isAdmin = user?.role === 'ADMIN';

  const [activeTab, setActiveTab] = useState('stock');
  const [products, setProducts] = useState([]);
  // `movements` ne contient que la page courante du grand livre.
  const [movements, setMovements] = useState([]);
  const [movementsMeta, setMovementsMeta] = useState({ totalElements: 0, totalPages: 1 });
  const [movementsLoading, setMovementsLoading] = useState(true);
  // Même principe pour le registre des retours : append-only, paginé côté serveur.
  const [returns, setReturns] = useState([]);
  const [returnsMeta, setReturnsMeta] = useState({ totalElements: 0, totalPages: 1 });
  const [returnsLoading, setReturnsLoading] = useState(true);
  const [loading, setLoading] = useState(true);

  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [stockFilter, setStockFilter] = useState('ALL');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [productSort, setProductSort] = useState({ key: 'name', direction: 'asc' });
  const [movementSort, setMovementSort] = useState({ key: 'createdAt', direction: 'desc' });
  const [returnSort, setReturnSort] = useState({ key: 'createdAt', direction: 'desc' });
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);

  // Saisie d'un retour client et consultation d'un retour enregistré.
  const [returnWizardOpen, setReturnWizardOpen] = useState(false);
  const [selectedReturn, setSelectedReturn] = useState(null);
  const [returnDetailsLoading, setReturnDetailsLoading] = useState(false);

  const [operation, setOperation] = useState(null);
  const [formData, setFormData] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Seul l'identifiant est conservé : la fiche lit ensuite le produit dans la liste
  // rafraîchie, sinon elle continuerait d'afficher le stock d'avant l'opération.
  const [detailsProductId, setDetailsProductId] = useState(null);
  const [productMovements, setProductMovements] = useState([]);
  const [detailsLoading, setDetailsLoading] = useState(false);

  // Le grand livre des mouvements est paginé côté serveur (registre append-only) : filtre,
  // recherche et tri lui sont délégués, sinon ils ne porteraient que sur la page reçue.
  // La liste des produits, elle, est bornée par le catalogue et reste traitée côté client.
  useEffect(() => {
    if (isAdmin) fetchProducts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin]);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchTerm.trim()), 350);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  useEffect(() => {
    setCurrentPage(1);
  }, [debouncedSearch, stockFilter, typeFilter, activeTab, itemsPerPage]);

  const movementParams = useMemo(() => {
    const params = {};
    if (typeFilter !== 'ALL') params.type = typeFilter;
    if (debouncedSearch) params.search = debouncedSearch;
    return params;
  }, [typeFilter, debouncedSearch]);

  const movementSortParam = `${MOVEMENT_SORT_FIELDS[movementSort.key] ?? 'createdAt'},${movementSort.direction}`;
  const returnSortParam = `${RETURN_SORT_FIELDS[returnSort.key] ?? 'createdAt'},${returnSort.direction}`;

  useEffect(() => {
    if (!isAdmin) return;
    fetchMovements();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin, movementParams, movementSortParam, currentPage, itemsPerPage]);

  useEffect(() => {
    if (!isAdmin) return;
    fetchReturns();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin, debouncedSearch, returnSortParam, currentPage, itemsPerPage]);

  const fetchProducts = async () => {
    try {
      setLoading(true);
      const { data } = await api.get('/products');
      setProducts(data || []);
    } catch (error) {
      console.error('Error fetching stock data:', error);
      toast.error(t('stock.loadError'));
    } finally {
      setLoading(false);
    }
  };

  const fetchMovements = async () => {
    try {
      setMovementsLoading(true);
      const { data } = await api.get('/stock/movements', {
        params: {
          ...movementParams,
          page: currentPage - 1,
          size: itemsPerPage,
          sort: movementSortParam,
        },
      });
      setMovements(data.content || []);
      setMovementsMeta({
        totalElements: data.totalElements ?? 0,
        totalPages: Math.max(1, data.totalPages ?? 1),
      });
    } catch (error) {
      console.error('Error fetching stock movements:', error);
      toast.error(t('stock.loadError'));
    } finally {
      setMovementsLoading(false);
    }
  };

  const fetchReturns = async () => {
    try {
      setReturnsLoading(true);
      const { data } = await api.get('/stock/returns', {
        params: {
          ...(debouncedSearch ? { search: debouncedSearch } : {}),
          page: currentPage - 1,
          size: itemsPerPage,
          sort: returnSortParam,
        },
      });
      setReturns(data.content || []);
      setReturnsMeta({
        totalElements: data.totalElements ?? 0,
        totalPages: Math.max(1, data.totalPages ?? 1),
      });
    } catch (error) {
      console.error('Error fetching stock returns:', error);
      toast.error(t('stock.loadError'));
    } finally {
      setReturnsLoading(false);
    }
  };

  // Après une opération de stock, produit ET grand livre ont changé.
  const fetchData = async () => {
    await Promise.all([fetchProducts(), fetchMovements()]);
  };

  /* Indicateurs calculés depuis la liste des produits plutôt que depuis `/stock/statistics`.
   * L'endpoint compte comme « stock faible » tous les produits sous leur seuil, ruptures
   * comprises : la tuile annonçait donc un total que le filtre correspondant ne retrouvait
   * jamais. Ici tuiles, filtres et pastilles du tableau appliquent la même règle. */
  const stats = useMemo(() => {
    const statuses = products.map(stockStatus);
    return {
      totalProducts: products.length,
      totalQuantity: products.reduce((sum, p) => sum + (p.stockQuantity ?? 0), 0),
      totalValue: products.reduce((sum, p) => sum + stockValueOf(p), 0),
      low: statuses.filter((s) => s === 'low').length,
      out: statuses.filter((s) => s === 'out').length,
      ok: statuses.filter((s) => s === 'ok').length,
    };
  }, [products]);

  // Recherche insensible à la casse ET aux accents, comme le moteur de suggestions.
  const query = normalizeText(searchTerm);
  const matchesQuery = (...fields) =>
    !query || fields.some((field) => field && normalizeText(field).includes(query));

  const filteredProducts = useMemo(() => {
    return products.filter((product) => {
      if (!matchesQuery(product.name, product.code, product.barcode, product.category?.name)) {
        return false;
      }
      if (stockFilter === 'ALL') return true;
      return stockStatus(product) === stockFilter;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [products, query, stockFilter]);

  const sortedProducts = useMemo(() => {
    const { key, direction } = productSort;
    const factor = direction === 'asc' ? 1 : -1;
    const rank = { out: 0, low: 1, ok: 2 };

    const valueOf = (product) => {
      if (key === 'status') return rank[stockStatus(product)];
      if (key === 'value') return stockValueOf(product);
      if (key === 'category') return product.category?.name || '';
      if (key === 'stockQuantity') return product.stockQuantity ?? 0;
      if (key === 'minStockAlert') return product.minStockAlert ?? 0;
      return product[key] ?? '';
    };

    return [...filteredProducts].sort((a, b) => {
      const left = valueOf(a);
      const right = valueOf(b);
      if (typeof left === 'string' || typeof right === 'string') {
        return String(left).localeCompare(String(right), 'fr', { sensitivity: 'base' }) * factor;
      }
      return (left - right) * factor;
    });
  }, [filteredProducts, productSort]);

  const isStockTab = activeTab === 'stock';
  const isMovementsTab = activeTab === 'movements';
  const isReturnsTab = activeTab === 'returns';

  /* Les onglets ne se paginent pas de la même façon : le catalogue est borné et découpé côté
   * client, tandis que le grand livre et le registre des retours arrivent déjà paginés du
   * serveur (filtrés, cherchés et triés par lui). D'où trois sources pour le total et pour
   * les lignes affichées. */
  const panel = isStockTab
    ? {
        loading,
        rows: sortedProducts.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage),
        totalItems: sortedProducts.length,
        totalPages: Math.max(1, Math.ceil(sortedProducts.length / itemsPerPage)),
        sort: productSort,
      }
    : isMovementsTab
      ? {
          loading: movementsLoading,
          rows: movements,
          totalItems: movementsMeta.totalElements,
          totalPages: movementsMeta.totalPages,
          sort: movementSort,
        }
      : {
          loading: returnsLoading,
          rows: returns,
          totalItems: returnsMeta.totalElements,
          totalPages: returnsMeta.totalPages,
          sort: returnSort,
        };

  const productSuggestions = useMemo(
    () => rankSuggestions(products, searchTerm, (p) => [p.name, p.code, p.barcode], 6),
    [products, searchTerm]
  );

  const hasActiveFilters =
    searchTerm.trim() !== '' ||
    (isStockTab && stockFilter !== 'ALL') ||
    (isMovementsTab && typeFilter !== 'ALL');

  const resetFilters = () => {
    setSearchTerm('');
    setStockFilter('ALL');
    setTypeFilter('ALL');
  };

  const handleSort = (key) => {
    const setter = isStockTab ? setProductSort : isMovementsTab ? setMovementSort : setReturnSort;
    setter((prev) => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
  };

  /* ---- Retours clients ---- */

  // Le retour vient de modifier des stocks et d'écrire au grand livre : les trois onglets
  // doivent repartir du serveur, pas seulement celui qu'on regarde.
  const handleReturnCreated = async (created) => {
    setReturnWizardOpen(false);
    toast.success(t('stock.returns.created', { number: created.returnNumber }));
    setActiveTab('returns');
    await Promise.all([fetchProducts(), fetchMovements(), fetchReturns()]);
    if (detailsProductId) await fetchProductMovements(detailsProductId);
  };

  // La liste ne transporte que les entêtes : le détail des articles rendus est chargé
  // à l'ouverture de la fiche.
  const openReturnDetails = async (stockReturn) => {
    setSelectedReturn(stockReturn);
    setReturnDetailsLoading(true);
    try {
      const { data } = await api.get(`/stock/returns/${stockReturn.id}`);
      setSelectedReturn(data);
    } catch (error) {
      console.error('Error fetching stock return:', error);
      toast.error(t('stock.returns.detailsError'));
      setSelectedReturn(null);
    } finally {
      setReturnDetailsLoading(false);
    }
  };

  /* ---- Opérations de stock ---- */

  const openOperation = (type, product = null) => {
    setOperation(type);
    setFormError('');
    setFormData({
      ...EMPTY_FORM,
      productId: product?.id ?? '',
      newQuantity: product?.stockQuantity ?? '',
    });
  };

  const closeOperation = () => {
    setOperation(null);
    setFormData(EMPTY_FORM);
    setFormError('');
  };

  // Produit visé par l'opération, déduit du seul `productId` : une même valeur pilote
  // le sélecteur, l'aperçu du stock projeté et la validation.
  const operationProduct = useMemo(
    () => products.find((p) => String(p.id) === String(formData.productId)) || null,
    [products, formData.productId]
  );

  const config = operation ? OPERATIONS[operation] : null;
  const isAdjust = operation === 'adjust';

  // Stock projeté après validation : le chiffre que l'utilisateur vient vérifier avant
  // de confirmer, et que l'écran ne montrait nulle part.
  const projection = useMemo(() => {
    if (!operationProduct || !config) return null;
    const current = operationProduct.stockQuantity ?? 0;
    if (isAdjust) {
      if (formData.newQuantity === '') return null;
      const target = parseInt(formData.newQuantity, 10);
      if (Number.isNaN(target)) return null;
      return { current, next: target, delta: target - current };
    }
    if (formData.quantity === '') return null;
    const amount = parseInt(formData.quantity, 10);
    if (Number.isNaN(amount)) return null;
    const delta = amount * config.sign;
    return { current, next: current + delta, delta };
  }, [operationProduct, config, isAdjust, formData.quantity, formData.newQuantity]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!config) return;

    if (!formData.productId) {
      setFormError(t('stock.productRequired'));
      return;
    }
    if (!projection) {
      setFormError(t('stock.quantityRequired'));
      return;
    }
    if (isAdjust ? projection.next < 0 : projection.delta === 0) {
      setFormError(t('stock.quantityPositive'));
      return;
    }
    // Une sortie supérieure au stock disponible part sinon pour revenir en
    // InsufficientStockException : autant le dire avant l'appel.
    if (projection.next < 0) {
      setFormError(t('stock.insufficientStock', { available: projection.current }));
      return;
    }
    setFormError('');

    const payload = { productId: formData.productId, reason: formData.reason };
    if (isAdjust) {
      payload.newQuantity = parseInt(formData.newQuantity, 10);
    } else {
      payload.quantity = parseInt(formData.quantity, 10);
      if (config.withReference) payload.reference = formData.reference;
      if (config.withUnitCost && formData.unitCost) {
        payload.unitCost = parseFloat(formData.unitCost);
      }
    }

    try {
      setSubmitting(true);
      await api.post(config.endpoint, payload);
      toast.success(t('stock.operationSuccess'));
      closeOperation();
      await fetchData();
      // La fiche ouverte doit refléter le mouvement qui vient d'être enregistré.
      if (detailsProductId) await fetchProductMovements(detailsProductId);
    } catch (error) {
      console.error('Error submitting stock operation:', error);
      const raw = error.response?.data;
      const message =
        typeof raw === 'string' ? raw : raw?.message || raw?.error || t('stock.operationError');
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  /* ---- Fiche produit ---- */

  const detailsProduct = useMemo(
    () => products.find((p) => p.id === detailsProductId) || null,
    [products, detailsProductId]
  );

  // Fiche produit : on montre l'historique récent, borné à une page — l'onglet Mouvements
  // reste l'endroit où parcourir le grand livre entier.
  const fetchProductMovements = async (productId) => {
    setDetailsLoading(true);
    try {
      const response = await api.get(`/stock/movements/product/${productId}`, {
        params: { page: 0, size: 50, sort: 'createdAt,desc' },
      });
      setProductMovements(response.data.content || []);
    } catch (error) {
      console.error('Error fetching product movements:', error);
      setProductMovements([]);
    } finally {
      setDetailsLoading(false);
    }
  };

  const openDetails = (product) => {
    setDetailsProductId(product.id);
    setProductMovements([]);
    fetchProductMovements(product.id);
  };

  /* ---- Rendu partagé ---- */

  const statusBadge = (product) => {
    const status = stockStatus(product);
    const tone = { out: 'badge-danger', low: 'badge-warning', ok: 'badge-success' }[status];
    return <span className={tone}>{t(`stock.status.${status}`)}</span>;
  };

  const movementBadge = (type) => {
    const Icon = MOVEMENT_ICONS[type] || Package;
    return (
      <span className={badgeClass(STOCK_MOVEMENT_TONE[type])}>
        <Icon className="w-3 h-3" aria-hidden="true" />
        {t(`stock.movementTypes.${type}`, { defaultValue: type })}
      </span>
    );
  };

  const productColumns = [
    {
      key: 'name',
      label: t('stock.columnProduct'),
      sortable: true,
      render: (product) => (
        <div className="min-w-0">
          <div className="font-semibold text-gray-900 dark:text-gray-100 truncate">{product.name}</div>
          <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
            {product.code || product.barcode || '—'}
          </div>
        </div>
      ),
    },
    {
      key: 'category',
      label: t('stock.columnCategory'),
      sortable: true,
      className: 'hidden lg:table-cell',
      render: (product) => (
        <span className="text-gray-600 dark:text-gray-400">{product.category?.name || '—'}</span>
      ),
    },
    {
      key: 'stockQuantity',
      label: t('stock.columnStock'),
      sortable: true,
      render: (product) => (
        <div className="flex items-baseline gap-1">
          <span className="text-base font-semibold tabular-nums text-gray-900 dark:text-gray-100">
            {product.stockQuantity ?? 0}
          </span>
          <span className="text-xs text-gray-500 dark:text-gray-400">{product.unit}</span>
        </div>
      ),
    },
    {
      key: 'minStockAlert',
      label: t('stock.columnThreshold'),
      sortable: true,
      className: 'hidden xl:table-cell',
      render: (product) => (
        <span className="tabular-nums text-gray-600 dark:text-gray-400">
          {product.minStockAlert ?? 0}
        </span>
      ),
    },
    {
      key: 'status',
      label: t('stock.columnStatus'),
      sortable: true,
      render: statusBadge,
    },
    {
      key: 'value',
      label: t('stock.columnValue'),
      sortable: true,
      className: 'hidden md:table-cell',
      render: (product) => (
        <span className="tabular-nums font-semibold text-gray-900 dark:text-gray-100">
          {formatCurrency(stockValueOf(product))}
        </span>
      ),
    },
  ];

  const movementColumns = [
    {
      key: 'createdAt',
      label: t('stock.columnDate'),
      sortable: true,
      render: (movement) => (
        <div className="tabular-nums">
          <div className="text-gray-900 dark:text-gray-100">{formatDate(movement.createdAt)}</div>
          <div className="text-xs text-gray-500 dark:text-gray-400">{formatTime(movement.createdAt)}</div>
        </div>
      ),
    },
    {
      key: 'type',
      label: t('stock.columnType'),
      sortable: true,
      render: (movement) => movementBadge(movement.type),
    },
    {
      key: 'product',
      label: t('stock.columnProduct'),
      sortable: true,
      render: (movement) => (
        <div className="min-w-0">
          <div className="font-medium text-gray-900 dark:text-gray-100 truncate">
            {movement.product?.name || '—'}
          </div>
          <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
            {movement.product?.code || '—'}
          </div>
        </div>
      ),
    },
    {
      key: 'delta',
      label: t('stock.columnMovement'),
      // Non triable : l'écart est calculé à l'affichage et n'existe pas en base, or le tri
      // du grand livre est délégué au serveur. Trier ici n'ordonnerait que la page visible.
      sortable: false,
      render: (movement) => {
        /* L'écart est recalculé depuis previousStock/newStock : `quantity` est stocké en
         * valeur absolue (cf. StockService.adjustStock), il ne porte donc pas le sens du
         * mouvement. Le signe affiché est ainsi toujours celui de l'effet réel. */
        const delta = (movement.newStock ?? 0) - (movement.previousStock ?? 0);
        const tone =
          delta > 0
            ? 'text-green-600 dark:text-green-400'
            : delta < 0
              ? 'text-red-600 dark:text-red-400'
              : 'text-gray-500 dark:text-gray-400';
        return (
          <div>
            <span className={`font-semibold tabular-nums ${tone}`}>
              {delta > 0 ? '+' : ''}
              {delta}
            </span>
            <div className="text-xs text-gray-500 dark:text-gray-400 tabular-nums">
              {movement.previousStock} → {movement.newStock}
            </div>
          </div>
        );
      },
    },
    {
      key: 'reference',
      label: t('stock.columnReference'),
      className: 'hidden lg:table-cell',
      render: (movement) => (
        <span className="text-gray-600 dark:text-gray-400">{movement.reference || '—'}</span>
      ),
    },
    {
      key: 'user',
      label: t('stock.columnBy'),
      className: 'hidden xl:table-cell',
      render: (movement) => (
        <span className="text-gray-600 dark:text-gray-400">
          {movement.user
            ? `${movement.user.firstName || ''} ${movement.user.lastName || ''}`.trim() ||
              movement.user.username
            : '—'}
        </span>
      ),
    },
    {
      key: 'reason',
      label: t('stock.columnReason'),
      className: 'hidden 2xl:table-cell',
      nowrap: false,
      render: (movement) => (
        <span className="block max-w-xs truncate text-gray-600 dark:text-gray-400" title={movement.reason}>
          {movement.reason || '—'}
        </span>
      ),
    },
  ];

  const returnColumns = [
    {
      key: 'createdAt',
      label: t('stock.columnDate'),
      sortable: true,
      render: (item) => (
        <div className="tabular-nums">
          <div className="text-gray-900 dark:text-gray-100">{formatDate(item.createdAt)}</div>
          <div className="text-xs text-gray-500 dark:text-gray-400">{formatTime(item.createdAt)}</div>
        </div>
      ),
    },
    {
      key: 'returnNumber',
      label: t('stock.returns.columnNumber'),
      sortable: true,
      render: (item) => (
        <span className="font-medium text-gray-900 dark:text-gray-100">{item.returnNumber}</span>
      ),
    },
    {
      key: 'order',
      label: t('stock.returns.columnOrder'),
      sortable: true,
      render: (item) => (
        <div className="min-w-0">
          <div className="text-gray-900 dark:text-gray-100 truncate">{item.orderNumber || '—'}</div>
          <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
            {item.invoiceNumber || '—'}
          </div>
        </div>
      ),
    },
    {
      key: 'client',
      label: t('stock.returns.columnClient'),
      className: 'hidden lg:table-cell',
      render: (item) => (
        <span className="text-gray-600 dark:text-gray-400">
          {item.clientName || t('stock.returns.walkInClient')}
        </span>
      ),
    },
    {
      key: 'totalQuantity',
      label: t('stock.returns.columnQuantity'),
      sortable: true,
      render: (item) => (
        <span className="font-semibold tabular-nums text-green-600 dark:text-green-400">
          +{item.totalQuantity ?? 0}
        </span>
      ),
    },
    {
      key: 'refundAmount',
      label: t('stock.returns.columnRefund'),
      sortable: true,
      render: (item) =>
        Number(item.refundAmount) > 0 ? (
          <span className="tabular-nums font-semibold text-gray-900 dark:text-gray-100">
            {formatCurrency(item.refundAmount)}
          </span>
        ) : (
          <span className="text-gray-400">—</span>
        ),
    },
    {
      key: 'user',
      label: t('stock.columnBy'),
      className: 'hidden xl:table-cell',
      render: (item) => (
        <span className="text-gray-600 dark:text-gray-400">
          {item.createdBy
            ? `${item.createdBy.firstName || ''} ${item.createdBy.lastName || ''}`.trim() ||
              item.createdBy.username
            : '—'}
        </span>
      ),
    },
  ];

  const emptyState = hasActiveFilters ? (
    <div className="flex flex-col items-center gap-3">
      <Boxes className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('stock.noResultsTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('stock.noResultsHint')}</p>
      </div>
      <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
        {t('stock.resetFilters')}
      </Button>
    </div>
  ) : (
    <div className="flex flex-col items-center gap-3">
      <Boxes className="empty-state-icon" aria-hidden="true" />
      <p className="font-medium text-gray-700 dark:text-gray-300">
        {isStockTab
          ? t('stock.emptyProducts')
          : isMovementsTab
            ? t('stock.emptyMovements')
            : t('stock.returns.empty')}
      </p>
    </div>
  );

  if (!isAdmin) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] text-center px-6">
        <div className="w-16 h-16 rounded-full bg-red-100 dark:bg-red-500/15 flex items-center justify-center mb-4">
          <ShieldAlert className="w-8 h-8 text-red-600 dark:text-red-400" aria-hidden="true" />
        </div>
        <h1 className="page-title mb-2">{t('stock.deniedTitle')}</h1>
        <p className="text-gray-600 dark:text-gray-400 max-w-md">{t('stock.deniedHint')}</p>
      </div>
    );
  }

  const tabs = [
    { value: 'stock', label: t('stock.tabStock'), icon: Boxes, count: products.length },
    // Totaux des registres, pas le nombre de lignes de la page affichée.
    { value: 'movements', label: t('stock.tabMovements'), icon: ArrowRightLeft, count: movementsMeta.totalElements },
    { value: 'returns', label: t('stock.tabReturns'), icon: RotateCcw, count: returnsMeta.totalElements },
  ];

  return (
    <div className="space-y-6">
      {/* ---- En-tête ---- */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <Warehouse aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('stock.title')}</h1>
            <p className="page-subtitle">{t('stock.subtitle')}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" icon={RefreshCw} onClick={fetchData} loading={loading}>
            {t('common.refresh')}
          </Button>
          <Button variant="primary" icon={Plus} onClick={() => openOperation('add')}>
            {t('stock.opAdd')}
          </Button>
        </div>
      </div>

      {/* ---- Indicateurs ---- */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 sm:gap-6">
        <StatCard
          title={t('stock.statQuantity')}
          value={stats.totalQuantity}
          subtitle={t('stock.statQuantityHint', { products: stats.totalProducts })}
          icon={Package}
          tone="info"
          loading={loading}
        />
        <StatCard
          title={t('stock.statValue')}
          value={formatCurrency(stats.totalValue)}
          subtitle={t('stock.statValueHint')}
          icon={Euro}
          tone="success"
          loading={loading}
        />
        <StatCard
          title={t('stock.statLow')}
          value={stats.low}
          subtitle={t('stock.statLowHint')}
          icon={AlertTriangle}
          tone="warning"
          loading={loading}
        />
        <StatCard
          title={t('stock.statOut')}
          value={stats.out}
          subtitle={t('stock.statOutHint')}
          icon={PackageX}
          tone="danger"
          loading={loading}
        />
      </div>

      {/* ---- Opérations ----
       * Les quatre opérations secondaires vivaient dans l'onglet « Mouvements », tout comme
       * la modale elle-même : le bouton « Ajouter du stock » de l'en-tête n'ouvrait donc
       * rien tant qu'on était sur l'onglet « Stock actuel ». Elles sont désormais hors des
       * onglets, donc accessibles en permanence. */}
      <div className="card">
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm font-medium text-gray-500 dark:text-gray-400 mr-1">
            {t('stock.operationsLabel')}
          </span>
          <Button variant="secondary" size="sm" icon={Minus} onClick={() => openOperation('remove')}>
            {t('stock.opRemove')}
          </Button>
          <Button variant="secondary" size="sm" icon={Edit2} onClick={() => openOperation('adjust')}>
            {t('stock.opAdjust')}
          </Button>
          <Button
            variant="secondary"
            size="sm"
            icon={AlertTriangle}
            onClick={() => openOperation('damage')}
          >
            {t('stock.opDamage')}
          </Button>
          {/* Le retour client ouvre son propre parcours : il commence par retrouver la vente,
              pas par choisir un produit. */}
          <Button variant="outline" size="sm" icon={RotateCcw} onClick={() => setReturnWizardOpen(true)}>
            {t('stock.opReturn')}
          </Button>
        </div>
      </div>

      {/* ---- Alerte actionnable ----
       * L'ancien bandeau répétait mot pour mot les deux tuiles qui le précédaient. Il sert
       * maintenant de raccourci : un clic bascule sur l'onglet stock avec le filtre posé. */}
      {!loading && (stats.out > 0 || stats.low > 0) && (
        <div className="card border-amber-200 bg-amber-50 dark:bg-amber-500/10 dark:border-amber-500/30">
          <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
            <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0" aria-hidden="true" />
            <p className="text-sm font-medium text-amber-900 dark:text-amber-200 flex-1 min-w-[12rem]">
              {stats.out > 0 && stats.low > 0
                ? t('stock.alertBoth', { out: stats.out, low: stats.low })
                : stats.out > 0
                  ? t('stock.alertOut', { out: stats.out })
                  : t('stock.alertLow', { low: stats.low })}
            </p>
            <div className="flex flex-wrap gap-2">
              {stats.out > 0 && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    setActiveTab('stock');
                    setStockFilter('out');
                  }}
                >
                  {t('stock.alertViewOut')}
                </Button>
              )}
              {stats.low > 0 && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    setActiveTab('stock');
                    setStockFilter('low');
                  }}
                >
                  {t('stock.alertViewLow')}
                </Button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ---- Onglets et contenu ----
       * Barre d'onglets et panneau dans une même carte : la barre flottait auparavant dans
       * sa propre carte, séparée de son contenu par les indicateurs et le bandeau d'alerte. */}
      <div className="card overflow-hidden p-0">
        <div role="tablist" aria-label={t('stock.title')} className="flex border-b border-gray-200 dark:border-gray-700 overflow-x-auto">
          {tabs.map((tab) => {
            const selected = activeTab === tab.value;
            return (
              <button
                key={tab.value}
                role="tab"
                aria-selected={selected}
                aria-controls={`panel-${tab.value}`}
                onClick={() => setActiveTab(tab.value)}
                className={`flex items-center gap-2 px-6 py-4 text-sm font-medium whitespace-nowrap border-b-2 -mb-px transition-colors ${
                  selected
                    ? 'border-primary-600 text-primary-700 dark:text-primary-300'
                    : 'border-transparent text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100'
                }`}
              >
                <tab.icon className="w-4 h-4" aria-hidden="true" />
                {tab.label}
                <span
                  className={`tabular-nums text-xs ${
                    selected ? 'text-primary-600 dark:text-primary-400' : 'text-gray-400 dark:text-gray-500'
                  }`}
                >
                  {tab.count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Barre de recherche et filtres du panneau actif. Les suggestions de produits
            servent aux deux onglets : choisir un produit revient à filtrer ses mouvements,
            la recherche portant sur son nom de part et d'autre. */}
        <div className="flex flex-col lg:flex-row lg:items-center gap-4 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <SearchBox
            className="flex-1"
            value={searchTerm}
            onChange={setSearchTerm}
            placeholder={
              isStockTab
                ? t('stock.searchProducts')
                : isMovementsTab
                  ? t('stock.searchMovements')
                  : t('stock.returns.search')
            }
            suggestions={isReturnsTab ? [] : productSuggestions}
            getKey={(p) => p.id}
            onSelectSuggestion={(p) => setSearchTerm(p.name)}
            renderSuggestion={(p) => (
              <span className="flex items-center justify-between gap-2">
                <span className="flex flex-col min-w-0">
                  <span className="font-medium truncate">{p.name}</span>
                  <span className="text-xs text-gray-400 truncate">{p.code || p.barcode || '—'}</span>
                </span>
                <span className="text-xs text-gray-500 shrink-0">
                  {p.stockQuantity ?? 0} {p.unit}
                </span>
              </span>
            )}
          />

          <div className="flex flex-wrap items-center gap-3">
            {isStockTab ? (
              <SegmentedFilter
                label={t('stock.columnStatus')}
                value={stockFilter}
                onChange={setStockFilter}
                options={[
                  { value: 'ALL', label: t('stock.filterAll'), count: stats.totalProducts },
                  { value: 'ok', label: t('stock.status.ok'), count: stats.ok },
                  { value: 'low', label: t('stock.status.low'), count: stats.low },
                  { value: 'out', label: t('stock.status.out'), count: stats.out },
                ]}
              />
            ) : isMovementsTab ? (
              <div className="flex items-center gap-2">
                <label htmlFor="movement-type" className="text-sm text-gray-500 dark:text-gray-400">
                  {t('stock.columnType')}
                </label>
                <select
                  id="movement-type"
                  value={typeFilter}
                  onChange={(e) => setTypeFilter(e.target.value)}
                  className="input-field w-auto py-2"
                >
                  <option value="ALL">{t('stock.filterAllMovements')}</option>
                  {MOVEMENT_TYPES.map((type) => (
                    <option key={type.value} value={type.value}>
                      {t(`stock.movementTypes.${type.value}`)}
                    </option>
                  ))}
                </select>
              </div>
            ) : null
            /* Rien pour le registre des retours : pas de facette à filtrer — la recherche porte
               déjà sur le n° de retour, la vente, la facture et le client — et l'ouverture de
               l'assistant est le bouton « Retour client » de la barre d'opérations, qui reste
               à l'écran quel que soit l'onglet. */}
            {hasActiveFilters && (
              <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
                {t('stock.resetFilters')}
              </Button>
            )}
          </div>
        </div>

        <div id={`panel-${activeTab}`} role="tabpanel">
          <Table
            columns={isStockTab ? productColumns : isMovementsTab ? movementColumns : returnColumns}
            data={panel.rows}
            loading={panel.loading}
            emptyState={emptyState}
            sortKey={panel.sort.key}
            sortDirection={panel.sort.direction}
            onSort={handleSort}
            actions={
              isReturnsTab
                ? (item) => (
                    <button
                      onClick={() => openReturnDetails(item)}
                      className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                      title={t('stock.returns.viewDetails')}
                      aria-label={`${t('stock.returns.viewDetails')} — ${item.returnNumber}`}
                    >
                      <Eye className="w-4 h-4" aria-hidden="true" />
                    </button>
                  )
                : isStockTab
                ? (product) => (
                    <>
                      <button
                        onClick={() => openDetails(product)}
                        className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                        title={t('stock.viewDetails')}
                        aria-label={`${t('stock.viewDetails')} — ${product.name}`}
                      >
                        <Eye className="w-4 h-4" aria-hidden="true" />
                      </button>
                      {/* Opérer depuis la ligne du produit concerné : `openOperation`
                          acceptait déjà un produit, rien ne le lui passait. */}
                      <button
                        onClick={() => openOperation('add', product)}
                        className="text-green-600 hover:text-green-800 dark:hover:text-green-300 p-2 hover:bg-green-50 dark:hover:bg-green-500/10 rounded-lg transition-colors"
                        title={t('stock.opAdd')}
                        aria-label={`${t('stock.opAdd')} — ${product.name}`}
                      >
                        <Plus className="w-4 h-4" aria-hidden="true" />
                      </button>
                      <button
                        onClick={() => openOperation('remove', product)}
                        className="text-red-600 hover:text-red-800 dark:hover:text-red-300 p-2 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors"
                        title={t('stock.opRemove')}
                        aria-label={`${t('stock.opRemove')} — ${product.name}`}
                      >
                        <Minus className="w-4 h-4" aria-hidden="true" />
                      </button>
                      <button
                        onClick={() => openOperation('adjust', product)}
                        className="text-primary-600 hover:text-primary-900 dark:hover:text-primary-300 p-2 hover:bg-primary-50 dark:hover:bg-primary-500/10 rounded-lg transition-colors"
                        title={t('stock.opAdjust')}
                        aria-label={`${t('stock.opAdjust')} — ${product.name}`}
                      >
                        <Edit2 className="w-4 h-4" aria-hidden="true" />
                      </button>
                    </>
                  )
                : null
            }
          />

          {!panel.loading && panel.totalItems > 0 && (
            <Pagination
              currentPage={Math.min(currentPage, panel.totalPages)}
              totalPages={panel.totalPages}
              totalItems={panel.totalItems}
              itemsPerPage={itemsPerPage}
              onPageChange={setCurrentPage}
              onItemsPerPageChange={setItemsPerPage}
            />
          )}
        </div>
      </div>

      {/* ---- Modale d'opération ----
       * Enveloppée dans un contexte d'empilement supérieur : elle peut être ouverte
       * depuis la fiche produit, et les deux modales partagent le même `z-50`. Sans
       * cela, la fiche — déclarée après dans le DOM — recouvrirait le formulaire. */}
      <div className="relative z-[60]">
      <Modal
        isOpen={Boolean(operation)}
        onClose={closeOperation}
        title={operation ? t(`stock.opTitle.${operation}`) : ''}
      >
        {operation && (
          <form onSubmit={handleSubmit} className="space-y-4">
            <p className="text-sm text-gray-500 dark:text-gray-400 -mt-1">
              {t(`stock.opHint.${operation}`)}
            </p>

            {operationProduct ? (
              <div className="flex items-start justify-between gap-3 p-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40">
                <div className="min-w-0">
                  <p className="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                    {t('stock.selectedProduct')}
                  </p>
                  <p className="font-semibold text-gray-900 dark:text-gray-100 mt-1 truncate">
                    {operationProduct.name}
                  </p>
                  <p className="text-sm text-gray-600 dark:text-gray-400">
                    {t('stock.currentStock')}{' '}
                    <span className="font-semibold text-gray-900 dark:text-gray-100 tabular-nums">
                      {operationProduct.stockQuantity ?? 0}
                    </span>{' '}
                    {operationProduct.unit}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setFormData({ ...formData, productId: '', newQuantity: '' })}
                  className="p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 rounded"
                  aria-label={t('stock.changeProduct')}
                  title={t('stock.changeProduct')}
                >
                  <X className="w-5 h-5" aria-hidden="true" />
                </button>
              </div>
            ) : (
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {t('stock.productLabel')} <span className="text-red-500">*</span>
                </label>
                <SearchableSelect
                  options={products}
                  value={formData.productId}
                  onChange={(value) => {
                    const product = products.find((p) => String(p.id) === String(value));
                    setFormData({
                      ...formData,
                      productId: value,
                      newQuantity: product?.stockQuantity ?? '',
                    });
                  }}
                  getOptionValue={(p) => p.id}
                  getOptionLabel={(p) => p.name}
                  getOptionSearch={(p) => `${p.code || ''} ${p.barcode || ''}`}
                  placeholder={t('stock.productPlaceholder')}
                  noResultsText={t('stock.noProductFound')}
                  minChars={1}
                  required
                  inputClassName="input-field pl-10 pr-9"
                  renderOption={(p) => (
                    <span className="flex flex-col">
                      <span className="font-medium truncate">{p.name}</span>
                      <span className="text-xs text-gray-500">
                        {[
                          p.code && `Réf. ${p.code}`,
                          p.barcode,
                          `${t('stock.currentStock')} ${p.stockQuantity ?? 0} ${p.unit || ''}`.trim(),
                        ]
                          .filter(Boolean)
                          .join(' · ')}
                      </span>
                    </span>
                  )}
                />
              </div>
            )}

            {isAdjust ? (
              <FormInput
                label={t('stock.newQuantityLabel')}
                name="newQuantity"
                type="number"
                min="0"
                value={formData.newQuantity}
                onChange={(e) => setFormData({ ...formData, newQuantity: e.target.value })}
                placeholder={t('stock.newQuantityPlaceholder')}
                required
              />
            ) : (
              <FormInput
                label={t('stock.quantityLabel')}
                name="quantity"
                type="number"
                min="1"
                value={formData.quantity}
                onChange={(e) => setFormData({ ...formData, quantity: e.target.value })}
                placeholder={t('stock.quantityPlaceholder')}
                required
              />
            )}

            {/* Aperçu du résultat : ce que deviendra le stock si l'opération est validée. */}
            {projection && (
              <div className="flex items-center justify-between gap-3 px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700">
                <span className="text-sm text-gray-500 dark:text-gray-400">
                  {t('stock.projectionLabel')}
                </span>
                <span className="flex items-center gap-2 tabular-nums">
                  <span className="text-gray-500 dark:text-gray-400">{projection.current}</span>
                  <span className="text-gray-400">→</span>
                  <span
                    className={`text-lg font-bold ${
                      projection.next < 0
                        ? 'text-red-600 dark:text-red-400'
                        : 'text-gray-900 dark:text-gray-100'
                    }`}
                  >
                    {projection.next}
                  </span>
                  <span
                    className={
                      projection.delta > 0
                        ? 'badge-success'
                        : projection.delta < 0
                          ? 'badge-danger'
                          : 'badge-neutral'
                    }
                  >
                    {projection.delta > 0 ? '+' : ''}
                    {projection.delta}
                  </span>
                </span>
              </div>
            )}

            {config?.withUnitCost && (
              <FormInput
                label={t('stock.unitCostLabel')}
                name="unitCost"
                type="number"
                step="0.01"
                min="0"
                value={formData.unitCost}
                onChange={(e) => setFormData({ ...formData, unitCost: e.target.value })}
                placeholder={t('stock.unitCostPlaceholder')}
              />
            )}

            {config?.withReference && (
              <FormInput
                label={t('stock.referenceLabel')}
                name="reference"
                value={formData.reference}
                onChange={(e) => setFormData({ ...formData, reference: e.target.value })}
                placeholder={t(`stock.referencePlaceholder.${operation}`)}
                maxLength={100}
              />
            )}

            <FormInput
              label={t('stock.reasonLabel')}
              name="reason"
              type="textarea"
              rows={3}
              value={formData.reason}
              onChange={(e) => setFormData({ ...formData, reason: e.target.value })}
              placeholder={t(`stock.reasonPlaceholder.${operation}`)}
              maxLength={500}
            />

            {formError && (
              <p className="text-sm text-red-600 dark:text-red-400" role="alert">
                {formError}
              </p>
            )}

            <div className="flex justify-end gap-3 pt-2">
              <Button type="button" variant="secondary" onClick={closeOperation}>
                {t('common.cancel')}
              </Button>
              <Button type="submit" variant="primary" loading={submitting}>
                {t('stock.confirmOperation')}
              </Button>
            </div>
          </form>
        )}
      </Modal>
      </div>

      {/* ---- Fiche produit ---- */}
      <Modal
        isOpen={Boolean(detailsProduct)}
        onClose={() => setDetailsProductId(null)}
        size="lg"
        title={detailsProduct?.name || ''}
      >
        {detailsProduct && (
          <div className="space-y-6">
            <p className="text-sm text-gray-500 dark:text-gray-400 -mt-1">
              {detailsProduct.code || detailsProduct.barcode || '—'}
              {detailsProduct.category?.name ? ` · ${detailsProduct.category.name}` : ''}
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <StatCard
                title={t('stock.columnStock')}
                value={`${detailsProduct.stockQuantity ?? 0} ${detailsProduct.unit || ''}`.trim()}
                subtitle={t(`stock.status.${stockStatus(detailsProduct)}`)}
                icon={Package}
                tone={{ out: 'danger', low: 'warning', ok: 'success' }[stockStatus(detailsProduct)]}
              />
              <StatCard
                title={t('stock.columnThreshold')}
                value={detailsProduct.minStockAlert ?? 0}
                subtitle={t('stock.thresholdHint')}
                icon={AlertTriangle}
                tone="neutral"
              />
              <StatCard
                title={t('stock.purchasePrice')}
                value={formatCurrency(detailsProduct.purchasePrice ?? 0)}
                subtitle={t('stock.perUnit', { unit: detailsProduct.unit || '' })}
                icon={Euro}
                tone="info"
              />
              <StatCard
                title={t('stock.columnValue')}
                value={formatCurrency(stockValueOf(detailsProduct))}
                subtitle={t('stock.statValueHint')}
                icon={Boxes}
                tone="accent"
              />
            </div>

            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" size="sm" icon={Plus} onClick={() => openOperation('add', detailsProduct)}>
                {t('stock.opAdd')}
              </Button>
              <Button variant="secondary" size="sm" icon={Minus} onClick={() => openOperation('remove', detailsProduct)}>
                {t('stock.opRemove')}
              </Button>
              <Button variant="secondary" size="sm" icon={Edit2} onClick={() => openOperation('adjust', detailsProduct)}>
                {t('stock.opAdjust')}
              </Button>
            </div>

            <div>
              <h3 className="section-title mb-3">{t('stock.movementHistory')}</h3>
              <div className="rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
                <Table
                  columns={movementColumns.filter((column) => column.key !== 'product')}
                  data={productMovements}
                  loading={detailsLoading}
                  skeletonRows={3}
                  emptyState={
                    <div className="flex flex-col items-center gap-2">
                      <Package className="empty-state-icon" aria-hidden="true" />
                      <p className="font-medium text-gray-700 dark:text-gray-300">
                        {t('stock.noMovementsTitle')}
                      </p>
                      <p className="text-sm text-gray-500 dark:text-gray-400">
                        {t('stock.noMovementsHint')}
                      </p>
                    </div>
                  }
                />
              </div>
            </div>
          </div>
        )}
      </Modal>

      {/* ---- Retour client ----
       * Au-dessus de la fiche produit pour la même raison que la modale d'opération : les deux
       * partagent `z-50` et c'est l'ordre du DOM qui trancherait autrement. */}
      <div className="relative z-[60]">
        <Modal
          isOpen={returnWizardOpen}
          onClose={() => setReturnWizardOpen(false)}
          size="xl"
          title={t('stock.returns.wizardTitle')}
        >
          <ReturnWizard
            isOpen={returnWizardOpen}
            products={products}
            onSuccess={handleReturnCreated}
            onClose={() => setReturnWizardOpen(false)}
          />
        </Modal>
      </div>

      {/* ---- Fiche d'un retour enregistré ---- */}
      <Modal
        isOpen={Boolean(selectedReturn)}
        onClose={() => setSelectedReturn(null)}
        size="lg"
        title={selectedReturn?.returnNumber || ''}
      >
        <ReturnDetails stockReturn={selectedReturn} loading={returnDetailsLoading} />
      </Modal>
    </div>
  );
};

export default Stock;
