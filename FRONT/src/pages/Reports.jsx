import { useState, useEffect, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  TrendingUp,
  Euro,
  ShoppingCart,
  Package,
  FileText,
  FileSpreadsheet,
  RefreshCw,
  User,
  Repeat,
  CalendarClock,
  ShoppingBag,
  Mail,
  Phone,
  MapPin,
  Building2,
  ArrowUp,
  ArrowDown,
  ArrowUpDown,
} from 'lucide-react';
import { motion } from 'framer-motion';
import { toast } from 'react-hot-toast';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import api from '../services/api';
import AdvancedFilters from '../components/AdvancedFilters';
import SearchableSelect from '../components/SearchableSelect';
import SearchBox from '../components/SearchBox';
import SegmentedFilter from '../components/SegmentedFilter';
import StatCard from '../components/StatCard';
import Pagination from '../components/Pagination';
import OrderStatusBadge from '../components/OrderStatusBadge';
import { exportToCsv, exportToPdf } from '../utils/exportData';
import { rankSuggestions } from '../utils/searchSuggestions';
import {
  formatCurrency,
  formatAmount,
  formatCompactCurrency,
  formatDate,
  safeRatio,
  todayISO,
} from '../utils/format';

/**
 * Rapports des ventes.
 *
 * Trois principes tiennent cet écran, chacun corrigeant un défaut de la version précédente :
 *
 *   1. Un seul jeu de données. Les indicateurs, le graphique, l'analyse client, le tableau et
 *      les exports partent tous de `filteredOrders`. L'export lisait auparavant d'autres
 *      champs que le tableau (`totalAmount` au lieu de `finalAmount`, `orderDate` — jamais
 *      renvoyé par l'API — au lieu de `createdAt`) : le fichier livré ne correspondait donc
 *      ni à l'écran ni à la réalité, avec des dates « Invalid Date » et des montants hors TVA.
 *
 *   2. Le même périmètre financier que le reste de l'application. Une commande annulée n'est
 *      pas un chiffre d'affaires : le backend l'exclut dans `/dashboard/overview` comme dans
 *      `buildDayMetrics`, ce rapport l'incluait. Les annulées restent affichées et comptées à
 *      part, jamais sommées dans le CA.
 *
 *   3. Des bornes de date inclusives. Le filtre comparait la date de commande à
 *      `new Date('2026-07-28')`, soit minuit UTC : toutes les commandes du dernier jour de la
 *      période disparaissaient du rapport, silencieusement.
 */

const ROWS_PER_PAGE = 25;

const EMPTY_FILTERS = {
  search: '',
  userId: '',
  clientId: '',
  startDate: '',
  endDate: '',
  status: '',
};

/** Date d'une commande. `orderDate` n'existe pas dans `OrderResponse` — repli défensif. */
const orderDateOf = (order) => new Date(order.createdAt || order.orderDate || Date.now());

/** Montant net de la commande (remise et TVA appliquées), celui qui figure sur la facture. */
const orderAmountOf = (order) => Number(order.finalAmount ?? order.totalAmount ?? 0);

const orderItemCount = (order) =>
  (order.items || order.orderItems || []).reduce((sum, item) => sum + (item.quantity || 0), 0);

/**
 * Nom du client. L'expression d'origine — `client?.name || client?.firstName ? \`${firstName}
 * ${lastName}\` : 'anonyme'` — évalue `||` avant `?:` : dès qu'un `name` existait, elle
 * affichait la concaténation prénom/nom, soit une chaîne vide pour un client professionnel
 * n'ayant qu'une raison sociale.
 */
const clientNameOf = (order, fallback) => {
  const client = order.client;
  if (!client) return fallback;
  return client.name
    || `${client.firstName || ''} ${client.lastName || ''}`.trim()
    || client.company
    || fallback;
};

/** Libellé court d'un client — partagé par le sélecteur, sa pastille et l'analyse client. */
const clientLabelOf = (client) =>
  client?.name || `${client?.firstName || ''} ${client?.lastName || ''}`.trim() || `#${client?.id}`;

const cashierNameOf = (order, fallback) => {
  const author = order.createdBy || order.user;
  if (!author) return fallback;
  return `${author.firstName || ''} ${author.lastName || ''}`.trim() || author.username || fallback;
};

/** Début de la semaine ISO (lundi) contenant `date`. */
const startOfIsoWeek = (date) => {
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const shift = (start.getDay() + 6) % 7;
  start.setDate(start.getDate() - shift);
  return start;
};

/**
 * Clé locale d'une date, `YYYY-MM-DD`. Volontairement pas `toISOString()` : celui-ci convertit
 * en UTC, si bien qu'un minuit local à l'est de Greenwich retombe sur la veille et range la
 * commande dans le mauvais seau.
 */
const localDayKey = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

/**
 * Bornage temporel d'une date selon la granularité choisie.
 *
 * `next` sert à parcourir la période sans trou — un intervalle sans vente doit apparaître à
 * zéro, sinon la courbe relie deux points distants et suggère une activité continue. `prev`
 * sert à remonter depuis la fin de la période quand celle-ci dépasse `maxBuckets` : c'est la
 * période RÉCENTE qu'on conserve, alors qu'une troncature depuis le début aurait affiché les
 * premiers jours de l'historique en prétendant montrer les derniers.
 */
const GRANULARITY = {
  day: {
    startOf: (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()),
    next: (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1),
    prev: (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate() - 1),
    key: localDayKey,
    label: (d) => d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }),
    maxBuckets: 62,
  },
  week: {
    startOf: startOfIsoWeek,
    next: (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + 7),
    prev: (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate() - 7),
    key: localDayKey,
    label: (d) => `S. ${d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })}`,
    maxBuckets: 53,
  },
  month: {
    startOf: (d) => new Date(d.getFullYear(), d.getMonth(), 1),
    next: (d) => new Date(d.getFullYear(), d.getMonth() + 1, 1),
    prev: (d) => new Date(d.getFullYear(), d.getMonth() - 1, 1),
    key: (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`,
    label: (d) => d.toLocaleDateString('fr-FR', { month: 'short', year: '2-digit' }),
    maxBuckets: 36,
  },
};

const TrendTooltip = ({ active, payload, t }) => {
  if (!active || !payload || payload.length === 0) return null;
  const point = payload[0].payload;
  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow px-3 py-2 text-sm">
      <p className="font-semibold text-gray-800 dark:text-gray-100">{point.label}</p>
      <p className="text-primary-600 dark:text-primary-400 font-medium">{formatCurrency(point.total)}</p>
      <p className="text-gray-500 dark:text-gray-400 text-xs">
        {t('reports.ordersCount', { count: point.count })}
      </p>
    </div>
  );
};

/** En-tête de colonne triable : le tri se pilote au clavier et s'annonce via `aria-sort`. */
const SortableHeader = ({ label, columnKey, sort, onSort, align = 'left' }) => {
  const isActive = sort.key === columnKey;
  const direction = isActive ? sort.direction : null;
  const Icon = !isActive ? ArrowUpDown : direction === 'asc' ? ArrowUp : ArrowDown;

  return (
    <th
      scope="col"
      className={align === 'right' ? 'table-th-right' : 'table-th'}
      aria-sort={!isActive ? 'none' : direction === 'asc' ? 'ascending' : 'descending'}
    >
      <button
        type="button"
        onClick={() => onSort(columnKey)}
        className={`inline-flex items-center gap-1 uppercase tracking-wider hover:text-gray-700 dark:hover:text-gray-200 transition-colors rounded ${
          align === 'right' ? 'flex-row-reverse' : ''
        } ${isActive ? 'text-gray-900 dark:text-gray-100' : ''}`}
      >
        {label}
        <Icon className="w-3 h-3" aria-hidden="true" />
      </button>
    </th>
  );
};

const Reports = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState([]);
  const [clients, setClients] = useState([]);
  const [orders, setOrders] = useState([]);
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const [granularity, setGranularity] = useState('day');
  const [sort, setSort] = useState({ key: 'date', direction: 'desc' });
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(ROWS_PER_PAGE);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      // En parallèle : les trois appels sont indépendants, les enchaîner triplait l'attente.
      const [ordersRes, usersRes, clientsRes] = await Promise.all([
        api.get('/orders'),
        api.get('/users'),
        api.get('/clients'),
      ]);
      setOrders(ordersRes.data);
      setUsers(usersRes.data.filter((u) => u.role === 'CAISSIER'));
      setClients(clientsRes.data);
    } catch (error) {
      console.error('Error loading reports data:', error);
      toast.error(t('reports.loadError'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  const setFilter = (name, value) => setFilters((prev) => ({ ...prev, [name]: value }));

  const resetFilters = () => setFilters(EMPTY_FILTERS);

  // Toute modification de filtre ramène à la première page : rester page 7 d'un résultat qui
  // n'en compte plus que 2 affichait un tableau vide sans expliquer pourquoi.
  useEffect(() => {
    setCurrentPage(1);
  }, [filters, itemsPerPage]);

  /**
   * Périodes usuelles. Chacune porte ses deux bornes : le bandeau peut donc à la fois les
   * appliquer et déduire laquelle est en vigueur, sans stocker un troisième état qui aurait
   * affiché « Ce mois » alors que les dates ont été retouchées à la main depuis.
   */
  const periodPresets = useMemo(() => {
    const now = new Date();
    const today = todayISO();
    return [
      { value: 'all', label: t('reports.allPeriods'), startDate: '', endDate: '' },
      { value: 'today', label: t('reports.presetToday'), startDate: today, endDate: today },
      {
        value: 'week',
        label: t('reports.presetWeek'),
        startDate: localDayKey(startOfIsoWeek(now)),
        endDate: today,
      },
      {
        value: 'month',
        label: t('reports.presetMonth'),
        startDate: localDayKey(new Date(now.getFullYear(), now.getMonth(), 1)),
        endDate: today,
      },
      {
        value: 'year',
        label: t('reports.presetYear'),
        startDate: localDayKey(new Date(now.getFullYear(), 0, 1)),
        endDate: today,
      },
    ];
  }, [t]);

  const activePreset = periodPresets.find(
    (preset) => preset.startDate === filters.startDate && preset.endDate === filters.endDate
  )?.value ?? 'custom';

  const applyPreset = (value) => {
    const preset = periodPresets.find((p) => p.value === value);
    // « Personnalisé » n'est qu'un état affiché, jamais une action : les bornes viennent alors
    // des deux champs de date du panneau.
    if (!preset) return;
    setFilters((prev) => ({ ...prev, startDate: preset.startDate, endDate: preset.endDate }));
  };

  const filteredOrders = useMemo(() => {
    // Bornes construites en heure LOCALE et fin de journée incluse : `new Date('2026-07-28')`
    // vaut minuit UTC, ce qui excluait toute la dernière journée de la période demandée.
    const startBound = filters.startDate ? new Date(`${filters.startDate}T00:00:00`) : null;
    const endBound = filters.endDate ? new Date(`${filters.endDate}T23:59:59.999`) : null;
    const needle = filters.search.trim().toLowerCase();
    const userId = filters.userId ? parseInt(filters.userId, 10) : null;
    const clientId = filters.clientId ? parseInt(filters.clientId, 10) : null;

    return orders.filter((order) => {
      if (userId && (order.createdBy?.id ?? order.user?.id) !== userId) return false;
      if (clientId && order.client?.id !== clientId) return false;
      if (filters.status && order.status !== filters.status) return false;

      const date = orderDateOf(order);
      if (startBound && date < startBound) return false;
      if (endBound && date > endBound) return false;

      if (needle) {
        const haystack = [
          order.orderNumber,
          clientNameOf(order, ''),
          cashierNameOf(order, ''),
        ].join(' ').toLowerCase();
        if (!haystack.includes(needle)) return false;
      }

      return true;
    });
  }, [orders, filters]);

  /**
   * Indicateurs de la sélection. Les annulées sont sorties du financier — comme partout
   * ailleurs dans l'application — mais restent comptées pour être signalées explicitement.
   */
  const stats = useMemo(() => {
    const honored = filteredOrders.filter((o) => o.status !== 'CANCELED');
    const totalSales = honored.reduce((sum, o) => sum + orderAmountOf(o), 0);
    const totalQuantity = honored.reduce((sum, o) => sum + orderItemCount(o), 0);

    return {
      totalSales,
      totalOrders: honored.length,
      canceledOrders: filteredOrders.length - honored.length,
      totalQuantity,
      averageOrderValue: honored.length > 0 ? totalSales / honored.length : 0,
      honored,
    };
  }, [filteredOrders]);

  /** Évolution du chiffre d'affaires sur la période, au pas choisi. */
  const trend = useMemo(() => {
    const config = GRANULARITY[granularity];
    const purchases = [...stats.honored].sort((a, b) => orderDateOf(a) - orderDateOf(b));
    if (purchases.length === 0) return [];

    const buckets = new Map();
    purchases.forEach((order) => {
      const bucketStart = config.startOf(orderDateOf(order));
      const key = config.key(bucketStart);
      const entry = buckets.get(key) || { total: 0, count: 0 };
      entry.total += orderAmountOf(order);
      entry.count += 1;
      buckets.set(key, entry);
    });

    const first = config.startOf(orderDateOf(purchases[0]));
    const last = config.startOf(orderDateOf(purchases[purchases.length - 1]));

    // Point de départ : au plus `maxBuckets` intervalles avant la fin. Au-delà, l'axe devient
    // illisible — et c'est la période récente qu'on garde. On remonte donc depuis `last` au
    // lieu d'avancer depuis `first`, ce qui borne aussi le nombre d'itérations sur un
    // historique de plusieurs années au pas journalier.
    let start = last;
    for (let i = 1; i < config.maxBuckets && start > first; i += 1) {
      start = config.prev(start);
    }
    if (start < first) start = first;

    const series = [];
    let cursor = start;
    while (cursor <= last) {
      const key = config.key(cursor);
      const entry = buckets.get(key) || { total: 0, count: 0 };
      series.push({ key, label: config.label(cursor), total: entry.total, count: entry.count });
      cursor = config.next(cursor);
    }

    return series;
  }, [stats.honored, granularity]);

  const selectedClient = filters.clientId
    ? clients.find((c) => c.id === parseInt(filters.clientId, 10))
    : null;

  /**
   * Analyse des achats d'un client, calculée sur les commandes DÉJÀ filtrées : ce qui est
   * résumé ici correspond exactement à ce qu'affiche le tableau plus bas.
   */
  const clientAnalytics = useMemo(() => {
    if (!filters.clientId) return null;

    const purchases = [...stats.honored].sort((a, b) => orderDateOf(a) - orderDateOf(b));
    const orderCount = purchases.length;
    const totalSpent = purchases.reduce((sum, o) => sum + orderAmountOf(o), 0);

    const firstDate = orderCount > 0 ? orderDateOf(purchases[0]) : null;
    const lastDate = orderCount > 0 ? orderDateOf(purchases[orderCount - 1]) : null;

    // Fréquence : délai moyen entre deux achats (au moins deux achats requis).
    let avgDaysBetween = null;
    if (orderCount >= 2) {
      const spanDays = (lastDate - firstDate) / (1000 * 60 * 60 * 24);
      avgDaysBetween = spanDays / (orderCount - 1);
    }

    // Produits les plus achetés : agrégation par produit (quantité + montant).
    const byProduct = new Map();
    purchases.forEach((order) => {
      (order.items || order.orderItems || []).forEach((item) => {
        const name = item.product?.name || item.productName || t('reports.unknownProduct');
        const key = item.product?.id ?? name;
        const quantity = item.quantity || 0;
        const amount = Number(item.totalPrice ?? (item.unitPrice || 0) * quantity);
        const entry = byProduct.get(key) || { name, quantity: 0, amount: 0 };
        entry.quantity += quantity;
        entry.amount += amount;
        byProduct.set(key, entry);
      });
    });
    const topProducts = [...byProduct.values()].sort((a, b) => b.quantity - a.quantity).slice(0, 6);

    return {
      orderCount,
      totalSpent,
      averageBasket: orderCount > 0 ? totalSpent / orderCount : 0,
      canceledCount: stats.canceledOrders,
      firstDate,
      lastDate,
      avgDaysBetween,
      topProducts,
      maxProductQty: topProducts.reduce((max, p) => Math.max(max, p.quantity), 0),
    };
  }, [filters.clientId, stats, t]);

  const sortedOrders = useMemo(() => {
    const factor = sort.direction === 'asc' ? 1 : -1;
    const accessors = {
      date: (o) => orderDateOf(o).getTime(),
      orderNumber: (o) => o.orderNumber || '',
      cashier: (o) => cashierNameOf(o, ''),
      client: (o) => clientNameOf(o, ''),
      items: (o) => orderItemCount(o),
      status: (o) => o.status || '',
      amount: (o) => orderAmountOf(o),
    };
    const accessor = accessors[sort.key] || accessors.date;

    return [...filteredOrders].sort((a, b) => {
      const left = accessor(a);
      const right = accessor(b);
      if (typeof left === 'string') return factor * left.localeCompare(right, 'fr');
      return factor * (left - right);
    });
  }, [filteredOrders, sort]);

  const totalPages = Math.max(1, Math.ceil(sortedOrders.length / itemsPerPage));
  const pageOrders = useMemo(() => {
    const start = (currentPage - 1) * itemsPerPage;
    return sortedOrders.slice(start, start + itemsPerPage);
  }, [sortedOrders, currentPage, itemsPerPage]);

  const handleSort = (key) => {
    setSort((prev) =>
      prev.key === key
        ? { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' }
        // Un montant ou une date se lisent d'abord du plus grand au plus petit ; un libellé,
        // dans l'ordre alphabétique.
        : { key, direction: ['amount', 'date', 'items'].includes(key) ? 'desc' : 'asc' }
    );
  };

  const activeFilterCount = Object.entries(filters)
    .filter(([, value]) => value !== '').length;

  const periodLabel = filters.startDate || filters.endDate
    ? `${filters.startDate ? formatDate(filters.startDate) : '…'} → ${filters.endDate ? formatDate(filters.endDate) : '…'}`
    : t('reports.allPeriods');

  /** Suggestions de la recherche plein texte, sur les mêmes champs que le filtrage. */
  const orderSuggestions = useMemo(
    () => rankSuggestions(
      orders,
      filters.search,
      (order) => [order.orderNumber, clientNameOf(order, ''), cashierNameOf(order, '')],
      8
    ),
    [orders, filters.search]
  );

  /**
   * Critères du panneau replié, décrits en données plutôt qu'en JSX : le composant partagé en
   * tire d'un coup les champs, le compteur du bouton et les pastilles de rappel, qui ne peuvent
   * donc plus diverger de ce qui filtre réellement le rapport.
   */
  const filterFields = useMemo(() => [
    {
      key: 'userId',
      label: t('reports.cashier'),
      type: 'select',
      options: [
        { value: '', label: t('reports.allCashiers') },
        ...users.map((user) => ({
          value: String(user.id),
          label: `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.username,
        })),
      ],
    },
    {
      key: 'clientId',
      label: t('reports.client'),
      type: 'custom',
      chipValue: (value) => {
        const client = clients.find((c) => String(c.id) === String(value));
        return client ? clientLabelOf(client) : value;
      },
      // Combobox et non `<select>` : le fichier clients se compte en centaines de lignes, une
      // liste déroulante native s'y parcourt à l'aveugle.
      render: ({ value, onChange }) => (
        <SearchableSelect
          options={clients}
          value={value}
          onChange={onChange}
          getOptionValue={(client) => client.id}
          getOptionLabel={(client) =>
            `${clientLabelOf(client)}${client.company ? ` • ${client.company}` : ''}`}
          getOptionSearch={(client) => `${client.company || ''} ${client.email || ''}`}
          placeholder={t('reports.clientPlaceholder')}
          noResultsText={t('reports.noClientFound')}
          minChars={0}
          inputClassName="w-full pl-9 pr-8 py-2 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:ring-4 focus:ring-primary-500/15 outline-none transition-all"
          renderOption={(client) => (
            <span className="flex flex-col">
              <span className="font-medium">
                {clientLabelOf(client)}
                {client.company && <span className="ml-2 text-xs text-primary-600">{client.company}</span>}
              </span>
              <span className="text-xs text-gray-500">
                {[client.email, client.phone].filter(Boolean).join(' · ') || t('reports.noContact')}
              </span>
            </span>
          )}
        />
      ),
    },
    {
      key: 'status',
      label: t('reports.status'),
      type: 'select',
      options: [
        { value: '', label: t('reports.allStatuses') },
        ...['PENDING', 'CONFIRMED', 'INVOICED', 'DELIVERED', 'CANCELED'].map((status) => ({
          value: status,
          label: t(`status.order.${status}`),
        })),
      ],
    },
    // Bornes croisées : une période inversée ne renvoie aucun résultat, autant l'empêcher à la
    // saisie plutôt que d'afficher un tableau vide inexplicable.
    {
      key: 'startDate',
      label: t('reports.startDate'),
      type: 'date',
      max: filters.endDate || undefined,
      chipValue: formatDate,
    },
    {
      key: 'endDate',
      label: t('reports.endDate'),
      type: 'date',
      min: filters.startDate || undefined,
      chipValue: formatDate,
    },
  ], [t, users, clients, filters.startDate, filters.endDate]);

  /** Colonnes communes au tableau et aux deux exports : ils ne peuvent pas diverger. */
  const exportColumns = useMemo(() => [
    { header: t('reports.columns.date'), value: (o) => formatDate(orderDateOf(o)) },
    { header: t('reports.columns.orderNumber'), value: (o) => o.orderNumber || '' },
    { header: t('reports.columns.cashier'), value: (o) => cashierNameOf(o, '—') },
    { header: t('reports.columns.client'), value: (o) => clientNameOf(o, t('reports.anonymousClient')) },
    { header: t('reports.columns.items'), value: (o) => orderItemCount(o), align: 'right' },
    { header: t('reports.columns.status'), value: (o) => t(`caisse.status.${o.status}`), align: 'left' },
    { header: t('reports.columns.amount'), value: (o) => formatAmount(orderAmountOf(o)), align: 'right' },
  ], [t]);

  const exportSummary = useMemo(() => [
    { label: t('reports.totalSales'), value: formatCurrency(stats.totalSales) },
    { label: t('reports.totalOrders'), value: String(stats.totalOrders) },
    { label: t('reports.totalItems'), value: String(stats.totalQuantity) },
    { label: t('reports.averageBasket'), value: formatCurrency(stats.averageOrderValue) },
  ], [t, stats]);

  const handleExportCsv = () => {
    exportToCsv({ filename: 'rapport-ventes', columns: exportColumns, rows: sortedOrders });
    toast.success(t('reports.exportDone'));
  };

  // Voir Caisses.jsx : jspdf est chargé au premier appel, la confirmation suit le document,
  // et l'échec de ce chargement doit se voir.
  const handleExportPdf = async () => {
    try {
      await exportToPdf({
        filename: 'rapport-ventes',
        title: t('reports.title'),
        subtitle: t('reports.exportScope', { period: periodLabel, count: sortedOrders.length }),
        summary: exportSummary,
        columns: exportColumns,
        rows: sortedOrders,
      });
      toast.success(t('reports.exportDone'));
    } catch (error) {
      console.error('Error exporting sales report PDF:', error);
      toast.error(t('reports.exportPdfError'));
    }
  };

  const openOrder = (orderId) => navigate(`/orders?orderId=${orderId}`);

  const canExport = sortedOrders.length > 0 && !loading;

  return (
    <div className="space-y-6">
      {/* En-tête */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <TrendingUp aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('reports.title')}</h1>
            <p className="page-subtitle">{t('reports.subtitle')}</p>
          </div>
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          <button
            type="button"
            onClick={fetchAll}
            disabled={loading}
            className="flex items-center gap-2 px-4 py-2 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors disabled:opacity-60"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
            {t('common.refresh')}
          </button>
          <button
            type="button"
            onClick={handleExportCsv}
            disabled={!canExport}
            className="btn-secondary py-2 disabled:opacity-50"
          >
            <FileSpreadsheet className="w-4 h-4" aria-hidden="true" />
            {t('reports.exportCsv')}
          </button>
          <button
            type="button"
            onClick={handleExportPdf}
            disabled={!canExport}
            className="btn-primary py-2 disabled:opacity-50"
          >
            <FileText className="w-4 h-4" aria-hidden="true" />
            {t('reports.exportPdf')}
          </button>
        </div>
      </div>

      {/* Recherche et filtres — même dispositif que Commandes, Factures et Historique : la
          recherche et la période toujours accessibles, les critères précis repliés derrière un
          bouton qui annonce combien sont actifs, et des pastilles rappelant ce qui filtre le
          rapport. Sans ces pastilles, un critère oublié dans un panneau fermé fait passer un
          rapport tronqué pour le rapport complet — et ici il est exporté tel quel. */}
      <motion.section
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        aria-label={t('reports.filters')}
        className="card"
      >
        <AdvancedFilters
          id="reports"
          fields={filterFields}
          values={filters}
          defaults={EMPTY_FILTERS}
          onChange={setFilter}
          onReset={resetFilters}
          resettable={activeFilterCount > 0}
          expanded={filtersExpanded}
          onToggleExpanded={() => setFiltersExpanded((v) => !v)}
          search={(
            <SearchBox
              id="reports-search"
              value={filters.search}
              onChange={(value) => setFilter('search', value)}
              placeholder={t('reports.searchPlaceholder')}
              suggestions={orderSuggestions}
              getKey={(order) => order.id}
              onSelectSuggestion={(order) => setFilter('search', order.orderNumber)}
              renderSuggestion={(order) => (
                <span className="flex items-center justify-between gap-2">
                  <span className="flex flex-col min-w-0">
                    <span className="font-medium truncate">{order.orderNumber}</span>
                    <span className="text-xs text-gray-400 truncate">
                      {clientNameOf(order, t('reports.anonymousClient'))} · {formatDate(orderDateOf(order))}
                    </span>
                  </span>
                  <span className="text-xs text-gray-500 shrink-0 tabular-nums">
                    {formatCurrency(orderAmountOf(order))}
                  </span>
                </span>
              )}
            />
          )}
          quickFilters={(
            /* La période pilote tout le rapport : elle reste hors du panneau, en accès direct.
               Le segment sélectionné est DÉDUIT des deux bornes — deux dates saisies à la main
               affichent « Personnalisé » au lieu de laisser « Ce mois » allumé à tort. */
            <SegmentedFilter
              label={t('reports.period')}
              value={activePreset}
              onChange={applyPreset}
              options={[
                ...periodPresets.map(({ value, label }) => ({ value, label })),
                ...(activePreset === 'custom'
                  ? [{ value: 'custom', label: t('reports.presetCustom') }]
                  : []),
              ]}
            />
          )}
        />
      </motion.section>

      {/* Indicateurs de la sélection */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 sm:gap-6">
        <StatCard
          title={t('reports.totalSales')}
          value={formatCurrency(stats.totalSales)}
          subtitle={
            stats.canceledOrders > 0
              ? t('reports.canceledExcluded', { count: stats.canceledOrders })
              : t('reports.honoredOnly')
          }
          icon={Euro}
          tone="success"
          loading={loading}
        />
        <StatCard
          title={t('reports.totalOrders')}
          value={stats.totalOrders}
          subtitle={t('reports.overTotal', { total: filteredOrders.length })}
          icon={ShoppingCart}
          tone="info"
          loading={loading}
        />
        <StatCard
          title={t('reports.totalItems')}
          value={stats.totalQuantity}
          icon={Package}
          tone="accent"
          loading={loading}
        />
        <StatCard
          title={t('reports.averageBasket')}
          value={formatCurrency(stats.averageOrderValue)}
          subtitle={t('reports.averageBasketHint')}
          icon={TrendingUp}
          tone="warning"
          loading={loading}
        />
      </div>

      {/* Évolution du chiffre d'affaires sur la sélection */}
      <motion.section
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        aria-labelledby="reports-trend-heading"
        className="card"
      >
        <div className="flex items-center justify-between flex-wrap gap-3 mb-4">
          <div className="flex items-center gap-2">
            <CalendarClock className="w-5 h-5 text-primary-600 dark:text-primary-400" aria-hidden="true" />
            <h2 id="reports-trend-heading" className="section-title">{t('reports.trend')}</h2>
            <span className="text-xs text-gray-400 dark:text-gray-500">{t('reports.trendHint')}</span>
          </div>
          <SegmentedFilter
            label={t('reports.groupBy')}
            value={granularity}
            onChange={setGranularity}
            options={[
              { value: 'day', label: t('reports.byDay') },
              { value: 'week', label: t('reports.byWeek') },
              { value: 'month', label: t('reports.byMonth') },
            ]}
          />
        </div>

        {loading ? (
          <div className="h-64 rounded-lg bg-gray-100 dark:bg-gray-700/40 animate-pulse" />
        ) : trend.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-500 dark:text-gray-400">
            <TrendingUp className="empty-state-icon mb-2" aria-hidden="true" />
            {t('reports.noDataForPeriod')}
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <AreaChart data={trend} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <defs>
                <linearGradient id="reportsTrendFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#1f77b4" stopOpacity={0.35} />
                  <stop offset="100%" stopColor="#1f77b4" stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="currentColor" className="text-gray-100 dark:text-gray-700" />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 12, fill: '#647697' }}
                tickLine={false}
                axisLine={{ stroke: '#d3dae8' }}
                minTickGap={16}
              />
              <YAxis
                tickFormatter={formatCompactCurrency}
                tick={{ fontSize: 12, fill: '#647697' }}
                tickLine={false}
                axisLine={false}
                width={52}
              />
              <Tooltip content={<TrendTooltip t={t} />} cursor={{ stroke: '#1f77b4', strokeOpacity: 0.2 }} />
              <Area type="monotone" dataKey="total" stroke="#1f77b4" strokeWidth={2} fill="url(#reportsTrendFill)" />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </motion.section>

      {/* Analyse des achats d'un client */}
      {clientAnalytics && (
        <motion.section
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          aria-labelledby="reports-client-heading"
          className="card"
        >
          <div className="flex items-center gap-3 mb-6">
            <div className="panel-icon panel-tone-info">
              <User aria-hidden="true" />
            </div>
            <div className="min-w-0">
              <h2 id="reports-client-heading" className="section-title truncate">
                {t('reports.clientAnalysis', {
                  name: selectedClient?.name
                    || `${selectedClient?.firstName || ''} ${selectedClient?.lastName || ''}`.trim()
                    || t('reports.client'),
                })}
              </h2>
              <p className="text-sm text-gray-500 dark:text-gray-400">
                {clientAnalytics.orderCount > 0
                  ? t('reports.fromTo', {
                      from: formatDate(clientAnalytics.firstDate),
                      to: formatDate(clientAnalytics.lastDate),
                    })
                  : t('reports.noPurchaseInPeriod')}
                {clientAnalytics.canceledCount > 0
                  && ` · ${t('reports.canceledExcluded', { count: clientAnalytics.canceledCount })}`}
              </p>
            </div>
          </div>

          {selectedClient && (
            <div className="flex flex-wrap gap-2 mb-6">
              {selectedClient.company && (
                <span className="badge-neutral">
                  <Building2 className="w-3.5 h-3.5" aria-hidden="true" /> {selectedClient.company}
                </span>
              )}
              {selectedClient.email && (
                <a href={`mailto:${selectedClient.email}`} className="badge-neutral hover:ring-gray-500/40 transition-shadow">
                  <Mail className="w-3.5 h-3.5" aria-hidden="true" /> {selectedClient.email}
                </a>
              )}
              {selectedClient.phone && (
                <a href={`tel:${selectedClient.phone}`} className="badge-neutral hover:ring-gray-500/40 transition-shadow">
                  <Phone className="w-3.5 h-3.5" aria-hidden="true" /> {selectedClient.phone}
                </a>
              )}
              {(selectedClient.city || selectedClient.postalCode) && (
                <span className="badge-neutral">
                  <MapPin className="w-3.5 h-3.5" aria-hidden="true" />
                  {[selectedClient.postalCode, selectedClient.city].filter(Boolean).join(' ')}
                </span>
              )}
            </div>
          )}

          {clientAnalytics.orderCount === 0 ? (
            <div className="py-10 text-center text-gray-500 dark:text-gray-400">
              <ShoppingBag className="empty-state-icon mb-2" aria-hidden="true" />
              {t('reports.noClientPurchase')}
            </div>
          ) : (
            <>
              <dl className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3 mb-6">
                <div className="mini-stat-success">
                  <dt className="flex items-center gap-2"><Euro className="w-4 h-4" aria-hidden="true" />{t('reports.totalSpent')}</dt>
                  <dd className="font-bold tabular-nums">{formatCurrency(clientAnalytics.totalSpent)}</dd>
                </div>
                <div className="mini-stat-info">
                  <dt className="flex items-center gap-2"><ShoppingBag className="w-4 h-4" aria-hidden="true" />{t('reports.purchaseCount')}</dt>
                  <dd className="font-bold tabular-nums">{clientAnalytics.orderCount}</dd>
                </div>
                <div className="mini-stat-accent">
                  <dt className="flex items-center gap-2"><TrendingUp className="w-4 h-4" aria-hidden="true" />{t('reports.averageBasket')}</dt>
                  <dd className="font-bold tabular-nums">{formatCurrency(clientAnalytics.averageBasket)}</dd>
                </div>
                <div className="mini-stat-warning">
                  <dt className="flex items-center gap-2"><Repeat className="w-4 h-4" aria-hidden="true" />{t('reports.frequency')}</dt>
                  <dd className="font-bold tabular-nums">
                    {clientAnalytics.avgDaysBetween != null
                      ? t('reports.daysBetween', { days: Math.round(clientAnalytics.avgDaysBetween) })
                      : '—'}
                  </dd>
                </div>
              </dl>

              <div className="rounded-xl border border-gray-100 dark:border-gray-700 p-4">
                <div className="flex items-center gap-2 mb-4">
                  <Package className="w-5 h-5 text-secondary-500" aria-hidden="true" />
                  <h3 className="subsection-title">{t('reports.topProducts')}</h3>
                </div>
                {clientAnalytics.topProducts.length === 0 ? (
                  <p className="text-sm text-gray-500 dark:text-gray-400 py-6 text-center">
                    {t('reports.noProductDetail')}
                  </p>
                ) : (
                  <ul className="space-y-3">
                    {clientAnalytics.topProducts.map((product) => (
                      <li key={product.name}>
                        <div className="flex items-center justify-between text-sm mb-1 gap-2">
                          <span className="font-medium text-gray-800 dark:text-gray-200 truncate">{product.name}</span>
                          <span className="text-gray-500 dark:text-gray-400 whitespace-nowrap tabular-nums">
                            {t('reports.unitsAndAmount', {
                              units: product.quantity,
                              amount: formatCurrency(product.amount),
                            })}
                          </span>
                        </div>
                        <div className="metric-track">
                          <div
                            className="metric-bar-info"
                            style={{ width: `${safeRatio(product.quantity, clientAnalytics.maxProductQty) * 100}%` }}
                          />
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </>
          )}
        </motion.section>
      )}

      {/* Détail des commandes */}
      <motion.section
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        aria-labelledby="reports-orders-heading"
        className="bg-white dark:bg-gray-800 rounded-2xl shadow-card border border-gray-200/80 dark:border-gray-700 overflow-hidden"
      >
        <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between flex-wrap gap-2">
          <h2 id="reports-orders-heading" className="section-title">{t('reports.orderDetails')}</h2>
          <span className="text-sm text-gray-500 dark:text-gray-400">
            {t('reports.resultCount', { count: sortedOrders.length })} · {periodLabel}
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
            <thead className="bg-gray-50 dark:bg-gray-900/40">
              <tr>
                <SortableHeader label={t('reports.columns.date')} columnKey="date" sort={sort} onSort={handleSort} />
                <SortableHeader label={t('reports.columns.orderNumber')} columnKey="orderNumber" sort={sort} onSort={handleSort} />
                <SortableHeader label={t('reports.columns.cashier')} columnKey="cashier" sort={sort} onSort={handleSort} />
                <SortableHeader label={t('reports.columns.client')} columnKey="client" sort={sort} onSort={handleSort} />
                <SortableHeader label={t('reports.columns.items')} columnKey="items" sort={sort} onSort={handleSort} align="right" />
                <SortableHeader label={t('reports.columns.status')} columnKey="status" sort={sort} onSort={handleSort} />
                <SortableHeader label={t('reports.columns.amount')} columnKey="amount" sort={sort} onSort={handleSort} align="right" />
              </tr>
            </thead>
            <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
              {loading ? (
                <tr>
                  <td colSpan="7" className="px-6 py-12 text-center text-gray-500 dark:text-gray-400">
                    <RefreshCw className="w-8 h-8 animate-spin mx-auto mb-2" aria-hidden="true" />
                    {t('common.loading')}
                  </td>
                </tr>
              ) : pageOrders.length === 0 ? (
                <tr>
                  <td colSpan="7" className="px-6 py-12 text-center">
                    <FileText className="empty-state-icon mb-3" aria-hidden="true" />
                    <p className="text-gray-500 dark:text-gray-400 font-medium">{t('reports.noOrderFound')}</p>
                    {activeFilterCount > 0 && (
                      <button
                        type="button"
                        onClick={resetFilters}
                        className="mt-3 text-sm text-primary-600 dark:text-primary-400 hover:text-primary-700 font-medium"
                      >
                        {t('reports.resetFilters')}
                      </button>
                    )}
                  </td>
                </tr>
              ) : (
                pageOrders.map((order) => {
                  const isCanceled = order.status === 'CANCELED';
                  return (
                    <tr
                      key={order.id}
                      tabIndex={0}
                      onClick={() => openOrder(order.id)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          openOrder(order.id);
                        }
                      }}
                      className={`hover:bg-gray-50 dark:hover:bg-gray-700/40 cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary-500 ${
                        isCanceled ? 'opacity-60' : ''
                      }`}
                    >
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-gray-100">
                        {formatDate(orderDateOf(order))}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-primary-600 dark:text-primary-400">
                        {order.orderNumber}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-gray-100">
                        {cashierNameOf(order, '—')}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-gray-100">
                        {clientNameOf(order, t('reports.anonymousClient'))}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-900 dark:text-gray-100 tabular-nums">
                        {orderItemCount(order)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        {/* Badge partagé : une commande facturée puis réglée s'affiche « Payée »,
                            comme sur la caisse et la supervision. Cette page tenait sa propre
                            table de libellés, qui ignorait le règlement. */}
                        <OrderStatusBadge order={order} />
                      </td>
                      <td
                        className={`px-6 py-4 whitespace-nowrap text-sm font-semibold text-right tabular-nums ${
                          isCanceled ? 'line-through text-gray-500 dark:text-gray-400' : 'text-gray-900 dark:text-gray-100'
                        }`}
                      >
                        {formatCurrency(orderAmountOf(order))}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination partagée : la version précédente en réimplémentait une, sans choix du
            nombre de lignes ni raccourci vers la première ou la dernière page. */}
        {!loading && sortedOrders.length > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={sortedOrders.length}
            itemsPerPage={itemsPerPage}
            onPageChange={setCurrentPage}
            onItemsPerPageChange={setItemsPerPage}
          />
        )}
      </motion.section>
    </div>
  );
};

export default Reports;
