import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Download } from 'lucide-react';
import Table from '../../components/Table';
import Pagination from '../../components/Pagination';
import SearchBox from '../../components/SearchBox';
import AdvancedFilters from '../../components/AdvancedFilters';
import Button from '../../components/Button';
import { formatDate, formatTime } from '../../utils/format';
import { extractErrorMessage } from '../../utils/apiError';
import { ACTION_TYPES, actionLabelKey } from '../../constants/activityActions';
import { ACTIVITY_TONE, badgeClass } from '../../constants/statusBadges';
import platformService from '../../services/platformService';

/**
 * Journal d'activité consolidé de tout le parc.
 *
 * La colonne « entreprise » est ce qui distingue cet écran de l'historique interne d'une
 * société : dans un journal global, savoir qu'un article a été supprimé n'a de sens que si
 * l'on sait chez qui. C'est aussi le premier critère de filtrage, pour la même raison.
 *
 * La recherche, les filtres, la pagination et l'export sont traités côté serveur — le journal
 * est le registre qui croît sans borne, filtrer ou exporter les seules lignes reçues ne
 * porterait que sur la page affichée.
 */

/** Valeur du filtre entreprise désignant les lignes qui n'en ont aucune : celles du propriétaire. */
const PLATFORM_SCOPE = 'PLATFORM';

/** Doit refléter ACTIVITY_EXPORT_LIMIT côté serveur : sert à prévenir d'un export tronqué. */
const EXPORT_LIMIT = 10000;

const EMPTY_FILTERS = {
  company: '',
  actionType: '',
  entity: '',
  from: '',
  to: '',
};

const PlatformActivity = () => {
  const { t } = useTranslation();

  const [entries, setEntries] = useState([]);
  const [companies, setCompanies] = useState([]);
  const [options, setOptions] = useState({ actionTypes: [], entities: [] });
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // La recherche part au serveur : on attend une frappe stabilisée plutôt qu'une requête
  // par caractère.
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search.trim());
      setPage(1);
    }, 350);
    return () => clearTimeout(timer);
  }, [search]);

  // Critères seuls, sans pagination : c'est le périmètre que l'export doit reprendre.
  const filterParams = useMemo(() => {
    const params = {};
    if (filters.company === PLATFORM_SCOPE) params.platformScope = true;
    else if (filters.company) params.companyId = filters.company;
    if (filters.actionType) params.actionType = filters.actionType;
    if (filters.entity) params.entity = filters.entity;
    // Bornes inclusives sur le jour entier.
    if (filters.from) params.start = `${filters.from}T00:00:00`;
    if (filters.to) params.end = `${filters.to}T23:59:59`;
    if (debouncedSearch) params.search = debouncedSearch;
    return params;
  }, [filters, debouncedSearch]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await platformService.getActivity({
        ...filterParams,
        page: page - 1,
        size: pageSize,
      });
      const data = response.data;
      setEntries(data.content ?? []);
      setTotalItems(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setLoading(false);
    }
  }, [filterParams, page, pageSize, t]);

  useEffect(() => {
    load();
  }, [load]);

  // Listes de filtrage : entreprises du parc, et valeurs réellement présentes au journal.
  // Le parc d'un back-office SaaS se compte en centaines, une page large suffit.
  useEffect(() => {
    platformService
      .getCompanies({ page: 0, size: 200 })
      .then((response) => setCompanies(response.data?.content ?? []))
      .catch(() => setCompanies([]));
    platformService
      .getActivityFilters()
      .then((response) => setOptions(response.data ?? { actionTypes: [], entities: [] }))
      .catch(() => setOptions({ actionTypes: [], entities: [] }));
  }, []);

  // Tout changement de critère renvoie à la première page : rester sur la page 7 d'un résultat
  // qui n'en compte plus que deux affiche une liste vide.
  const handleFilterChange = (key, value) => {
    setFilters((current) => ({ ...current, [key]: value }));
    setPage(1);
  };

  const resetFilters = () => {
    setSearch('');
    setFilters(EMPTY_FILTERS);
    setPage(1);
  };

  const hasFieldFilters = Object.keys(EMPTY_FILTERS).some((key) => filters[key] !== EMPTY_FILTERS[key]);
  const hasActiveFilters = search.trim() !== '' || hasFieldFilters;

  /**
   * Le fichier est assemblé par le serveur, qui seul voit le résultat filtré complet : le
   * navigateur n'a jamais que la page affichée sous la main.
   */
  const handleExport = async () => {
    setExporting(true);
    try {
      const response = await platformService.exportActivity(filterParams);
      const url = URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.download = `${t('platform.activity.exportFilename')}-${new Date().toISOString().split('T')[0]}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      toast.success(t('platform.activity.exported'));
      if (totalItems > EXPORT_LIMIT) {
        toast(t('platform.activity.exportCapped', { limit: EXPORT_LIMIT }));
      }
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setExporting(false);
    }
  };

  const companyOptions = useMemo(() => ([
    { value: '', label: t('platform.activity.allCompanies') },
    // Les actions du propriétaire ne portent aucune entreprise : sans cette entrée, elles ne
    // sont isolables par aucun critère.
    { value: PLATFORM_SCOPE, label: t('platform.activity.platformScope') },
    ...companies.map((company) => ({ value: String(company.id), label: company.name })),
  ]), [companies, t]);

  // Déduites du journal lui-même : un critère qui ne rendrait aucun résultat n'est pas proposé.
  const actionOptions = useMemo(() => {
    const present = new Set(options.actionTypes ?? []);
    return [
      { value: '', label: t('platform.activity.allActions') },
      ...ACTION_TYPES.filter((key) => present.has(key))
        .map((key) => ({ value: key, label: t(actionLabelKey(key)) })),
    ];
  }, [options, t]);

  const entityOptions = useMemo(() => ([
    { value: '', label: t('platform.activity.allEntities') },
    ...(options.entities ?? []).map((entity) => ({ value: entity, label: entity })),
  ]), [options, t]);

  const filterFields = useMemo(() => [
    { key: 'company', label: t('platform.activity.company'), type: 'select', options: companyOptions },
    { key: 'actionType', label: t('platform.activity.actionTypeLabel'), type: 'select', options: actionOptions },
    { key: 'entity', label: t('platform.activity.entityLabel'), type: 'select', options: entityOptions },
    { key: 'from', label: t('platform.activity.fromLabel'), type: 'date' },
    { key: 'to', label: t('platform.activity.toLabel'), type: 'date' },
  ], [companyOptions, actionOptions, entityOptions, t]);

  const columns = [
    {
      key: 'createdAt',
      label: t('platform.activity.when'),
      render: (row) => (
        <div>
          <p className="text-sm text-gray-900 dark:text-gray-100">{formatDate(row.createdAt)}</p>
          <p className="text-xs text-gray-500 dark:text-gray-400">{formatTime(row.createdAt)}</p>
        </div>
      ),
    },
    {
      key: 'companyName',
      label: t('platform.activity.company'),
      render: (row) => (
        <span className="font-medium text-gray-900 dark:text-gray-100">
          {row.companyName ?? t('platform.activity.platformScope')}
        </span>
      ),
    },
    {
      key: 'userFullName',
      label: t('platform.activity.user'),
      className: 'hidden md:table-cell',
      render: (row) => (
        <div>
          <p className="text-sm text-gray-900 dark:text-gray-100">{row.userFullName ?? '—'}</p>
          <p className="text-xs text-gray-500 dark:text-gray-400">{row.userRole}</p>
        </div>
      ),
    },
    {
      key: 'actionType',
      label: t('platform.activity.action'),
      render: (row) => (
        <span className={badgeClass(ACTIVITY_TONE[row.actionType])}>
          {t(actionLabelKey(row.actionType))}
        </span>
      ),
    },
    {
      key: 'description',
      label: t('platform.activity.description'),
      className: 'hidden lg:table-cell',
      render: (row) => (
        <span className="text-sm text-gray-600 dark:text-gray-300">{row.description}</span>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {t('platform.activity.title')}
          </h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {t('platform.activity.subtitle')}
          </p>
        </div>
        <Button
          variant="secondary"
          icon={Download}
          onClick={handleExport}
          loading={exporting}
          disabled={loading || exporting || totalItems === 0}
        >
          {t('platform.activity.export')}
        </Button>
      </div>

      <div className="card">
        <AdvancedFilters
          id="platform-activity"
          fields={filterFields}
          values={filters}
          defaults={EMPTY_FILTERS}
          onChange={handleFilterChange}
          onReset={resetFilters}
          resettable={hasActiveFilters}
          expanded={filtersExpanded}
          onToggleExpanded={() => setFiltersExpanded((value) => !value)}
          dateRange={{ fromKey: 'from', toKey: 'to' }}
          search={(
            <SearchBox
              value={search}
              onChange={setSearch}
              placeholder={t('platform.activity.searchPlaceholder')}
            />
          )}
        />
      </div>

      <div className="card overflow-hidden p-0">
        {/* Ce qui est exporté est le résultat filtré entier, pas la page affichée : sans ce
            rappel du périmètre, un export filtré passe pour un export complet. */}
        <div className="border-b border-gray-200 px-6 py-4 text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">
          {hasActiveFilters
            ? t('platform.activity.scopeFiltered', { total: totalItems })
            : t('platform.activity.scopeAll', { total: totalItems })}
        </div>
        <Table columns={columns} data={entries} loading={loading} />
        {totalPages > 0 && (
          <Pagination
            currentPage={page}
            totalPages={totalPages}
            totalItems={totalItems}
            itemsPerPage={pageSize}
            onPageChange={setPage}
            onItemsPerPageChange={(size) => {
              setPageSize(size);
              setPage(1);
            }}
          />
        )}
      </div>
    </div>
  );
};

export default PlatformActivity;
