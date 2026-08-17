import { useEffect, useMemo, useState } from 'react';
import {
  History as HistoryIcon,
  RefreshCw,
  Download,
  FileText,
  CalendarClock,
  Users as UsersIcon,
  Trash2,
  User,
  Hash,
  Globe,
  Eye,
  X,
  Clock,
  Layers,
} from 'lucide-react';
import { toast } from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import activityService from '../services/activityService';
import Modal from '../components/Modal';
import Table from '../components/Table';
import Button from '../components/Button';
import StatCard from '../components/StatCard';
import SearchBox from '../components/SearchBox';
import SegmentedFilter from '../components/SegmentedFilter';
import AdvancedFilters from '../components/AdvancedFilters';
import InfoRow from '../components/InfoRow';
import Pagination from '../components/Pagination';
import { rankSuggestions } from '../utils/searchSuggestions';
import { exportToCsv, exportToPdf } from '../utils/exportData';
import { formatPercent, safeRatio } from '../utils/format';
import i18n from '../i18n';
import { ACTIVITY_TONE, badgeClass } from '../constants/statusBadges';
import { ACTION_TYPES, actionLabelKey } from '../constants/activityActions';

/**
 * Journal d'audit. Écran de consultation pure : aucune écriture n'est possible depuis
 * l'application (cf. ActivityLogController, qui n'expose pas de création).
 *
 * Il reprend le gabarit des autres tableaux de bord — en-tête, rangée d'indicateurs, barre de
 * recherche `AdvancedFilters`, tableau `Table` — au lieu des champs et du tableau écrits à la
 * main qu'il portait : le mode sombre, les squelettes de chargement, le tri et les pastilles de
 * filtres actifs viennent alors de ces composants plutôt que d'être réécrits ici.
 */

/** Entités traçables, dans l'ordre du flux métier plutôt qu'alphabétique. */
const ENTITIES = ['Client', 'Product', 'Category', 'Order', 'Invoice', 'Delivery', 'Stock', 'User', 'Settings'];

/**
 * Critères de filtrage à l'état neutre : valeur initiale, cible de « Réinitialiser » et
 * référence permettant de savoir quels critères sont réellement actifs.
 */
const EMPTY_FILTERS = {
  userId: '',
  actionType: '',
  entity: '',
  from: '',
  to: '',
};

const pad = (n) => String(n).padStart(2, '0');

/** Date locale au format attendu par `<input type="date">`. */
const toDateInput = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

/**
 * Jour d'un horodatage backend, comparable en chaîne (« 2026-07-29 »).
 * Le backend renvoie un LocalDateTime sans fuseau : on découpe la chaîne au lieu de la passer
 * par `new Date()`, qui interprétait « 2026-07-29 » en UTC et écartait à tort les activités
 * de la première tranche horaire du jour de début.
 */
const dayOf = (isoDateTime) => (isoDateTime ? String(isoDateTime).slice(0, 10) : '');

const shiftedDay = (days) => {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - days);
  return d;
};

/**
 * Raccourcis de période, placés au premier niveau de la barre d'outils : sur un journal
 * d'audit, restreindre la fenêtre de temps est le geste le plus fréquent, il ne doit pas
 * demander d'ouvrir un panneau. Ils écrivent dans les mêmes champs `from`/`to` que la saisie
 * manuelle — un raccourci reste donc ajustable à la main, et la période n'a qu'un seul état.
 */
const PERIODS = [
  { value: 'ALL', labelKey: 'history.periods.all' },
  { value: 'TODAY', labelKey: 'history.periods.today', days: 0 },
  { value: '7D', labelKey: 'history.periods.last7', days: 6 },
  { value: '30D', labelKey: 'history.periods.last30', days: 29 },
];

const formatDateTime = (iso) =>
  iso
    ? new Date(iso).toLocaleString(i18n.t('export.locale'), {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
      })
    : '—';

const formatClock = (iso) =>
  iso
    ? new Date(iso).toLocaleTimeString(i18n.t('export.locale'), {
        hour: '2-digit', minute: '2-digit', second: '2-digit',
      })
    : '—';

/**
 * Jour lisible : « Aujourd'hui » / « Hier » pour les deux jours qui portent l'essentiel des
 * consultations, date complète au-delà. Repère plus vite qu'un « 29/07/2026 » répété, sans
 * l'inconvénient d'un « il y a 5 min » qui devient faux sans rechargement.
 */
const formatDayLabel = (iso) => {
  const day = dayOf(iso);
  if (!day) return '—';
  if (day === toDateInput(shiftedDay(0))) return i18n.t('history.today');
  if (day === toDateInput(shiftedDay(1))) return i18n.t('history.yesterday');
  return new Date(`${day}T00:00:00`).toLocaleDateString(i18n.t('export.locale'));
};

const userFullName = (activity) => {
  const user = activity?.user;
  if (!user) return null;
  return `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.username || null;
};

/** Auteur d'une activité, « Système » à défaut : une action sans auteur reste tracée. */
const userLabel = (activity) => userFullName(activity) || i18n.t('history.systemUser');

const initialsOf = (activity) => {
  const user = activity?.user;
  if (!user) return '—';
  const initials = `${user.firstName?.charAt(0) || ''}${user.lastName?.charAt(0) || ''}`.trim();
  return initials || user.username?.charAt(0)?.toUpperCase() || '—';
};

/* Colonnes de l'export, communes au CSV et au PDF : ce qui est exporté est par construction
 * ce qui est affiché, et l'échappement / le BOM / le séparateur sont gérés par `exportData`. */
const exportColumns = (t) => [
  { header: t('history.columns.timestamp'), value: (a) => formatDateTime(a.createdAt) },
  { header: t('history.columns.user'), value: (a) => userLabel(a) },
  { header: t('history.columns.username'), value: (a) => (a.user?.username ? `@${a.user.username}` : '—') },
  { header: t('history.columns.action'), value: (a) => t(actionLabelKey(a.actionType)) },
  { header: t('history.columns.entity'), value: (a) => a.entity || '—' },
  { header: t('history.columns.entityId'), value: (a) => a.entityId ?? '—', align: 'right' },
  { header: t('common.description'), value: (a) => a.description || '—' },
  { header: t('history.columns.ipAddress'), value: (a) => a.ipAddress || '—' },
];

// Correspondance entre les colonnes triables et les champs triés en base. Le tri est délégué
// au serveur : trier les lignes reçues ne réordonnerait que la page affichée.
const SORT_FIELDS = {
  createdAt: 'createdAt',
  actionType: 'actionType',
  entity: 'entity',
  user: 'user.firstName',
};

const History = () => {
  const { t } = useTranslation();
  // `activities` ne contient que la page courante — le journal entier n'est jamais chargé.
  const [activities, setActivities] = useState([]);
  const [pageMeta, setPageMeta] = useState({ totalElements: 0, totalPages: 1 });
  const [summary, setSummary] = useState(null);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);

  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const [sortConfig, setSortConfig] = useState({ key: 'createdAt', direction: 'desc' });
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(20);
  const [selectedActivity, setSelectedActivity] = useState(null);

  const today = toDateInput(shiftedDay(0));

  // La recherche part au serveur : on attend une frappe stabilisée plutôt qu'une requête
  // par caractère.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchTerm.trim()), 350);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  /* ---- Paramètres de requête ---- */

  // Critères seuls, sans pagination ni tri : c'est le périmètre que l'export doit reprendre.
  const filterParams = useMemo(() => {
    const params = {};
    if (filters.userId) params.userId = filters.userId;
    if (filters.actionType) params.actionType = filters.actionType;
    if (filters.entity) params.entity = filters.entity;
    // Bornes inclusives sur le jour entier.
    if (filters.from) params.start = `${filters.from}T00:00:00`;
    if (filters.to) params.end = `${filters.to}T23:59:59`;
    if (debouncedSearch) params.search = debouncedSearch;
    return params;
  }, [filters, debouncedSearch]);

  const sortParam = `${SORT_FIELDS[sortConfig.key] ?? 'createdAt'},${sortConfig.direction}`;

  const queryParams = useMemo(() => ({
    ...filterParams,
    page: currentPage - 1,
    size: itemsPerPage,
    sort: sortParam,
  }), [filterParams, currentPage, itemsPerPage, sortParam]);

  useEffect(() => {
    fetchReference();
  }, []);

  useEffect(() => {
    fetchPage();
  }, [queryParams]);

  // Utilisateurs (options de filtre) et indicateurs : indépendants de la page affichée.
  const fetchReference = async () => {
    try {
      const [usersRes, summaryRes] = await Promise.all([
        api.get('/users'),
        activityService.getSummary(),
      ]);
      setUsers(usersRes.data);
      setSummary(summaryRes.data);
    } catch (error) {
      console.error('Error fetching reference data:', error);
      toast.error(t('history.loadError'));
    }
  };

  const fetchPage = async () => {
    setLoading(true);
    try {
      const { data } = await activityService.getActivities(queryParams);
      setActivities(data.content || []);
      setPageMeta({
        totalElements: data.totalElements ?? 0,
        totalPages: Math.max(1, data.totalPages ?? 1),
      });
    } catch (error) {
      console.error('Error fetching activities:', error);
      toast.error(t('history.loadError'));
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = async () => {
    await Promise.all([fetchPage(), fetchReference()]);
    toast.success(t('history.refreshed'));
  };

  const handleFilterChange = (key, value) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
  };

  const resetFilters = () => {
    setSearchTerm('');
    setFilters(EMPTY_FILTERS);
  };

  /* ---- Période ---- */

  const activePeriod = useMemo(() => {
    if (!filters.from && !filters.to) return 'ALL';
    const match = PERIODS.find(
      (period) => period.days !== undefined
        && filters.from === toDateInput(shiftedDay(period.days))
        && filters.to === today
    );
    // Une plage saisie à la main n'est aucun des raccourcis : aucun n'apparaît alors enfoncé.
    return match?.value ?? 'CUSTOM';
  }, [filters.from, filters.to, today]);

  const applyPeriod = (value) => {
    const period = PERIODS.find((p) => p.value === value);
    if (!period || period.days === undefined) {
      setFilters((prev) => ({ ...prev, from: '', to: '' }));
      return;
    }
    setFilters((prev) => ({ ...prev, from: toDateInput(shiftedDay(period.days)), to: today }));
  };

  /* ---- Indicateurs ---- */

  // Agrégés par le serveur sur tout le journal : les compter sur les lignes reçues ne
  // donnerait que le volume de la page affichée.
  const stats = useMemo(() => ({
    total: summary?.total ?? 0,
    today: summary?.today ?? 0,
    week: summary?.week ?? 0,
    activeUsers: summary?.activeUsersToday ?? 0,
    deletions: summary?.deletions ?? 0,
  }), [summary]);

  /* ---- Filtrage ---- */

  // Listes déduites du journal lui-même : un critère qui ne rend aucun résultat n'est pas
  // proposé. Elles viennent du résumé, et non de la page, pour rester exhaustives.
  const actionOptions = useMemo(() => {
    const present = new Set(summary?.actionTypes ?? []);
    return ACTION_TYPES
      .filter((key) => present.has(key))
      .map((key) => ({ value: key, label: t(actionLabelKey(key)) }));
  }, [summary, t]);

  const entityOptions = useMemo(() => {
    const present = summary?.entities ?? [];
    const known = ENTITIES.filter((entity) => present.includes(entity));
    const others = present.filter((entity) => !ENTITIES.includes(entity)).sort();
    return [...known, ...others].map((entity) => ({ value: entity, label: entity }));
  }, [summary]);

  const filterFields = useMemo(() => [
    {
      key: 'userId',
      label: t('history.columns.user'),
      type: 'select',
      options: [
        { value: '', label: t('orders.filters.allUsers') },
        ...users.map((user) => ({
          value: String(user.id),
          label: `${user.firstName} ${user.lastName} (@${user.username})`,
        })),
      ],
    },
    {
      key: 'actionType',
      label: t('history.actionTypeLabel'),
      type: 'select',
      options: [{ value: '', label: t('history.allActions') }, ...actionOptions],
    },
    {
      key: 'entity',
      label: t('history.entityLabel'),
      type: 'select',
      options: [{ value: '', label: t('history.allEntities') }, ...entityOptions],
    },
    { key: 'from', label: t('history.fromLabel'), type: 'date' },
    { key: 'to', label: t('history.toLabel'), type: 'date' },
  ], [users, actionOptions, entityOptions, t]);

  // Filtrage, tri et découpage sont faits en base (cf. `queryParams`) : `activities` est
  // déjà la page à afficher. Les suggestions, elles, restent tirées des lignes visibles.
  const suggestions = rankSuggestions(
    activities,
    searchTerm,
    (a) => [a.description, userFullName(a), a.entity, a.user?.username],
    8
  );

  const hasFieldFilters = Object.keys(EMPTY_FILTERS).some((key) => filters[key] !== EMPTY_FILTERS[key]);
  const hasActiveFilters = searchTerm.trim() !== '' || hasFieldFilters;

  // Nombre de lignes répondant aux critères, toutes pages confondues.
  const matchingCount = pageMeta.totalElements;
  const totalPages = pageMeta.totalPages;
  const safePage = Math.min(currentPage, totalPages);

  useEffect(() => {
    setCurrentPage(1);
  }, [debouncedSearch, filters, sortConfig, itemsPerPage]);

  const handleSort = (key) => {
    setSortConfig((prev) => (
      prev.key === key
        ? { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' }
        // Une colonne de date s'ouvre du plus récent au plus ancien, le texte de A à Z.
        : { key, direction: key === 'createdAt' ? 'desc' : 'asc' }
    ));
  };

  /* ---- Exports ---- */

  // Le périmètre exporté est le résultat filtré complet, pas la page affichée : sans ce
  // rappel, un export filtré passe pour complet.
  const exportScope = hasActiveFilters
    ? t('history.scopeFiltered', { shown: matchingCount, total: stats.total })
    : t('history.scopeAll', { count: stats.total });

  /**
   * Récupère toutes les lignes du résultat filtré, page après page. L'écran n'en charge
   * jamais qu'une : exporter `activities` n'emporterait que les lignes visibles tout en
   * affichant le total filtré en sous-titre.
   */
  const collectRowsToExport = async () => {
    const rows = await activityService.fetchAllMatching({ ...filterParams, sort: sortParam });
    if (rows.length === 0) toast.error(t('history.loadError'));
    return rows;
  };

  const handleExportCsv = async () => {
    setExporting(true);
    try {
      const rows = await collectRowsToExport();
      if (rows.length === 0) return;
      exportToCsv({
        filename: t('history.exportFilename'),
        columns: exportColumns(t),
        rows,
      });
      toast.success(t('history.csvDownloaded'));
    } catch (error) {
      console.error('Error exporting activities:', error);
      toast.error(t('history.loadError'));
    } finally {
      setExporting(false);
    }
  };

  const handleExportPdf = async () => {
    setExporting(true);
    try {
      const rows = await collectRowsToExport();
      if (rows.length === 0) return;
      await exportToPdf({
        filename: t('history.exportFilename'),
        title: t('history.exportTitle'),
        subtitle: exportScope,
        summary: [
          { label: t('history.summaryExported'), value: rows.length },
          { label: t('history.statTodayTitle'), value: stats.today },
          { label: t('history.statActiveUsersTitle'), value: stats.activeUsers },
          { label: t('history.statDeletionsTitle'), value: stats.deletions },
        ],
        columns: exportColumns(t),
        rows,
      });
      toast.success(t('history.pdfDownloaded'));
    } catch (error) {
      console.error('Error exporting activities:', error);
      toast.error(t('history.loadError'));
    } finally {
      setExporting(false);
    }
  };

  /* ---- Colonnes ----
   * Ordonnées comme se lit une ligne d'audit : quand, qui, quoi, sur quel objet, dans quel
   * détail. La description et l'adresse IP se retirent sur écran étroit plutôt que d'imposer
   * un défilement horizontal ; elles restent intégralement lisibles dans la fiche. */
  const columns = [
    {
      key: 'createdAt',
      label: t('history.columns.timestamp'),
      sortable: true,
      render: (activity) => (
        <div className="flex flex-col">
          <span className="font-medium text-gray-900 dark:text-gray-100">
            {formatDayLabel(activity.createdAt)}
          </span>
          <span className="text-xs text-gray-500 dark:text-gray-400 tabular-nums">
            {formatClock(activity.createdAt)}
          </span>
        </div>
      ),
    },
    {
      key: 'user',
      label: t('history.columns.user'),
      sortable: true,
      render: (activity) => (
        <div className="flex items-center gap-3">
          <div
            className={`w-9 h-9 rounded-full flex items-center justify-center flex-shrink-0 text-xs font-semibold ${
              activity.user
                ? 'bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-200'
                : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-300'
            }`}
            aria-hidden="true"
          >
            {initialsOf(activity)}
          </div>
          <div className="min-w-0">
            <div className="font-medium text-gray-900 dark:text-gray-100 truncate">
              {userLabel(activity)}
            </div>
            <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
              {activity.user?.username ? `@${activity.user.username}` : t('history.automatedAction')}
            </div>
          </div>
        </div>
      ),
    },
    {
      key: 'actionType',
      // « Type d'action » et non « Action » : la colonne de boutons du tableau s'intitule
      // « Actions », et les deux en-têtes se confondaient.
      label: t('history.actionTypeLabel'),
      sortable: true,
      render: (activity) => (
        <span className={badgeClass(ACTIVITY_TONE[activity.actionType])}>
          {t(actionLabelKey(activity.actionType))}
        </span>
      ),
    },
    {
      key: 'entity',
      label: t('history.columns.entity'),
      sortable: true,
      className: 'hidden sm:table-cell',
      render: (activity) => (
        <div className="flex flex-col">
          <span className="text-gray-900 dark:text-gray-100">{activity.entity || '—'}</span>
          {activity.entityId && (
            <span className="text-xs text-gray-500 dark:text-gray-400 tabular-nums">#{activity.entityId}</span>
          )}
        </div>
      ),
    },
    {
      key: 'description',
      label: t('common.description'),
      nowrap: false,
      className: 'hidden lg:table-cell',
      render: (activity) => (
        <p className="max-w-md truncate text-gray-700 dark:text-gray-300" title={activity.description || ''}>
          {activity.description || '—'}
        </p>
      ),
    },
    {
      key: 'ipAddress',
      label: t('history.columns.ipAddress'),
      className: 'hidden xl:table-cell',
      render: (activity) => (
        <span className="font-mono text-xs text-gray-500 dark:text-gray-400">
          {activity.ipAddress || '—'}
        </span>
      ),
    },
  ];

  const emptyState = hasActiveFilters ? (
    <div className="flex flex-col items-center gap-3">
      <HistoryIcon className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('history.emptyFilteredTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
          {t('history.emptyFilteredHint')}
        </p>
      </div>
      <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
        {t('orders.page.resetFilters')}
      </Button>
    </div>
  ) : (
    <div className="flex flex-col items-center gap-3">
      <HistoryIcon className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('history.emptyTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
          {t('history.emptyHint')}
        </p>
      </div>
    </div>
  );

  return (
    <div className="space-y-6">
      {/* ---- En-tête ---- */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <HistoryIcon aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('history.title')}</h1>
            <p className="page-subtitle">{t('history.subtitle')}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" icon={RefreshCw} onClick={handleRefresh} loading={loading}>
            {t('common.refresh')}
          </Button>
          <Button
            variant="secondary"
            icon={Download}
            onClick={handleExportCsv}
            loading={exporting}
            disabled={loading || exporting || matchingCount === 0}
          >
            CSV
          </Button>
          <Button
            variant="secondary"
            icon={FileText}
            onClick={handleExportPdf}
            loading={exporting}
            disabled={loading || exporting || matchingCount === 0}
          >
            PDF
          </Button>
        </div>
      </div>

      {/* ---- Indicateurs ----
       * Quatre compteurs qui décrivent le journal, pas la liste affichée : le nombre de
       * résultats filtrés est déjà donné par l'en-tête de la liste et par la pagination.
       * La dernière tuile isole les suppressions, seule action irréversible du journal. */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 sm:gap-6">
        <StatCard
          title={t('history.statTotalTitle')}
          value={stats.total}
          subtitle={t('history.statTotalSubtitle')}
          icon={HistoryIcon}
          tone="info"
          loading={loading}
        />
        <StatCard
          title={t('history.statTodayTitle')}
          value={stats.today}
          subtitle={t('history.statTodaySubtitle', { count: stats.week })}
          icon={CalendarClock}
          tone="accent"
          loading={loading}
        />
        <StatCard
          title={t('history.statActiveUsersTitle')}
          value={stats.activeUsers}
          subtitle={t('history.today')}
          icon={UsersIcon}
          tone="success"
          loading={loading}
        />
        <StatCard
          title={t('history.statDeletionsTitle')}
          value={stats.deletions}
          subtitle={t('history.statDeletionsSubtitle', {
            percent: formatPercent(safeRatio(stats.deletions, stats.total)),
          })}
          icon={Trash2}
          tone="danger"
          loading={loading}
        />
      </div>

      {/* ---- Recherche et filtres ---- */}
      <div className="card space-y-4">
        <AdvancedFilters
          id="history"
          fields={filterFields}
          values={filters}
          defaults={EMPTY_FILTERS}
          onChange={handleFilterChange}
          onReset={resetFilters}
          resettable={hasActiveFilters}
          expanded={filtersExpanded}
          onToggleExpanded={() => setFiltersExpanded((v) => !v)}
          quickFilters={(
            <SegmentedFilter
              label={t('history.periodLabel')}
              value={activePeriod}
              onChange={applyPeriod}
              options={PERIODS.map((p) => ({ ...p, label: t(p.labelKey) }))}
            />
          )}
          search={(
            <SearchBox
              value={searchTerm}
              onChange={setSearchTerm}
              placeholder={t('history.searchPlaceholder')}
              suggestions={suggestions}
              getKey={(a) => a.id}
              onSelectSuggestion={(a) => setSearchTerm(a.description || a.entity || '')}
              renderSuggestion={(a) => (
                <span className="flex items-center justify-between gap-2">
                  <span className="flex flex-col min-w-0">
                    <span className="font-medium truncate">{a.description || t(actionLabelKey(a.actionType))}</span>
                    <span className="text-xs text-gray-400 truncate">
                      {userLabel(a)} · {a.entity || '—'}
                    </span>
                  </span>
                  <span className="text-xs text-gray-500 shrink-0">{formatDayLabel(a.createdAt)}</span>
                </span>
              )}
            />
          )}
        />
      </div>

      {/* ---- Journal ---- */}
      <div className="card overflow-hidden p-0">
        <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div>
            <h2 className="section-title">{t('history.auditTrail')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
              {loading ? t('history.loadingEntries') : exportScope}
            </p>
          </div>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {t('history.readOnlyNotice')}
          </p>
        </div>

        <Table
          columns={columns}
          data={activities}
          loading={loading}
          emptyState={emptyState}
          sortKey={sortConfig.key}
          sortDirection={sortConfig.direction}
          onSort={handleSort}
          onRowClick={setSelectedActivity}
          skeletonRows={8}
          actions={(activity) => (
            // Doublonne le clic sur la ligne, qui n'est pas atteignable au clavier.
            <button
              onClick={(e) => { e.stopPropagation(); setSelectedActivity(activity); }}
              className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
              title={t('orders.page.viewDetail')}
              aria-label={t('history.viewEntryAria', { id: activity.id })}
            >
              <Eye className="w-4 h-4" aria-hidden="true" />
            </button>
          )}
        />

        {!loading && matchingCount > 0 && (
          <Pagination
            currentPage={safePage}
            totalPages={totalPages}
            totalItems={matchingCount}
            itemsPerPage={itemsPerPage}
            onPageChange={setCurrentPage}
            onItemsPerPageChange={setItemsPerPage}
          />
        )}
      </div>

      {/* ---- Fiche d'activité ----
       * Seul endroit où la description longue et le champ `details` sont lisibles en entier ;
       * le tableau les tronque pour garder ses lignes à hauteur constante. */}
      <Modal
        isOpen={!!selectedActivity}
        onClose={() => setSelectedActivity(null)}
        title={t('history.detailTitle')}
        size="md"
      >
        {selectedActivity && (
          <div className="space-y-6">
            <div className="flex flex-wrap items-start justify-between gap-3 pb-5 border-b border-gray-200 dark:border-gray-700">
              <div className="min-w-0">
                <h3 className="text-lg font-bold text-gray-900 dark:text-gray-100">
                  {t(actionLabelKey(selectedActivity.actionType))}
                  {selectedActivity.entity && (
                    <span className="text-gray-500 dark:text-gray-400 font-normal"> · {selectedActivity.entity}</span>
                  )}
                </h3>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {formatDateTime(selectedActivity.createdAt)}
                </p>
              </div>
              <span className={badgeClass(ACTIVITY_TONE[selectedActivity.actionType])}>
                {t(actionLabelKey(selectedActivity.actionType))}
              </span>
            </div>

            <section className="space-y-3">
              <h4 className="subsection-title">{t('common.description')}</h4>
              <p className="text-sm text-gray-700 dark:text-gray-300 break-words">
                {selectedActivity.description || <span className="text-gray-400">—</span>}
              </p>
            </section>

            <section className="space-y-3">
              <h4 className="subsection-title">{t('history.traceability')}</h4>
              <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <InfoRow icon={User} label={t('history.columns.user')} value={userLabel(selectedActivity)} />
                <InfoRow
                  icon={User}
                  label={t('history.columns.username')}
                  value={selectedActivity.user?.username ? `@${selectedActivity.user.username}` : null}
                />
                <InfoRow icon={Layers} label={t('history.entityLabel')} value={selectedActivity.entity} />
                <InfoRow
                  icon={Hash}
                  label={t('history.entityRef')}
                  value={selectedActivity.entityId ? `#${selectedActivity.entityId}` : null}
                />
                <InfoRow icon={Globe} label={t('history.columns.ipAddress')} value={selectedActivity.ipAddress} />
                <InfoRow icon={Clock} label={t('history.logEntry')} value={`#${selectedActivity.id}`} />
              </dl>
            </section>

            {selectedActivity.details && (
              <section className="space-y-3">
                <h4 className="subsection-title">{t('history.technicalData')}</h4>
                <pre className="max-h-64 overflow-auto rounded-lg bg-gray-50 dark:bg-gray-900/40 border border-gray-200 dark:border-gray-700 p-3 text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap break-words">
                  {selectedActivity.details}
                </pre>
              </section>
            )}

            <div className="flex items-center justify-end pt-4 border-t border-gray-200 dark:border-gray-700">
              <Button variant="secondary" onClick={() => setSelectedActivity(null)}>
                {t('common.close')}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default History;
