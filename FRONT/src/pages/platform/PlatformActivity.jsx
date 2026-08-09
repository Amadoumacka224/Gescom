import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import Table from '../../components/Table';
import Pagination from '../../components/Pagination';
import SearchBox from '../../components/SearchBox';
import { formatDate, formatTime } from '../../utils/format';
import { extractErrorMessage } from '../../utils/apiError';
import platformService from '../../services/platformService';

/**
 * Journal d'activité consolidé de tout le parc.
 *
 * La colonne « entreprise » est ce qui distingue cet écran de l'historique interne d'une
 * société : dans un journal global, savoir qu'un article a été supprimé n'a de sens que si
 * l'on sait chez qui.
 *
 * La recherche et la pagination sont traitées côté serveur — le journal est le registre qui
 * croît sans borne, filtrer les seules lignes reçues ne porterait que sur la page affichée.
 */
const PlatformActivity = () => {
  const { t } = useTranslation();

  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await platformService.getActivity({
        page: page - 1,
        size: pageSize,
        ...(search ? { search } : {}),
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
  }, [page, pageSize, search, t]);

  useEffect(() => {
    load();
  }, [load]);

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
        <span className="text-sm text-gray-500 dark:text-gray-400">{row.actionType}</span>
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
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {t('platform.activity.title')}
        </h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {t('platform.activity.subtitle')}
        </p>
      </div>

      <SearchBox
        value={search}
        onChange={(value) => {
          setSearch(value);
          setPage(1);
        }}
        placeholder={t('platform.activity.searchPlaceholder')}
      />

      <div className="card overflow-hidden">
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
