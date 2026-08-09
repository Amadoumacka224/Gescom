import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { UserCheck, UserX } from 'lucide-react';
import Table from '../../components/Table';
import Pagination from '../../components/Pagination';
import SearchBox from '../../components/SearchBox';
import SegmentedFilter from '../../components/SegmentedFilter';
import FormSelect from '../../components/FormSelect';
import ConfirmModal from '../../components/ConfirmModal';
import { badgeClass } from '../../constants/statusBadges';
import { formatDate } from '../../utils/format';
import { extractErrorMessage } from '../../utils/apiError';
import platformService from '../../services/platformService';

/**
 * Utilisateurs de tout le parc.
 *
 * L'écran répond à une question de support : « quel compte pose problème chez ce client ? ».
 * D'où les deux colonnes qui le distinguent de l'écran Utilisateurs d'une entreprise —
 * l'entreprise d'appartenance, et la dernière connexion, qui dit si le compte sert encore.
 *
 * Le propriétaire de la plateforme n'y figure pas : il n'est l'utilisateur d'aucune
 * entreprise, le serveur l'exclut de la requête.
 */

const ROLE_TONE = { ADMIN: 'accent', CAISSIER: 'info' };

const COMPANY_STATUS_TONE = {
  ACTIVE: 'success',
  TRIAL: 'info',
  SUSPENDED: 'warning',
  CANCELED: 'danger',
};

const STATUS_FILTERS = [
  { value: '', key: 'all' },
  { value: 'true', key: 'active' },
  { value: 'false', key: 'inactive' },
];

const PlatformUsers = () => {
  const { t } = useTranslation();

  const [users, setUsers] = useState([]);
  const [companies, setCompanies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [companyId, setCompanyId] = useState('');
  const [role, setRole] = useState('');
  const [active, setActive] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [confirm, setConfirm] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await platformService.getUsers({
        page: page - 1,
        size: pageSize,
        ...(search ? { search } : {}),
        ...(companyId ? { companyId } : {}),
        ...(role ? { role } : {}),
        ...(active ? { active } : {}),
      });
      const data = response.data;
      setUsers(data.content ?? []);
      setTotalItems(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search, companyId, role, active, t]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    platformService
      .getCompanies({ page: 0, size: 200 })
      .then((response) => setCompanies(response.data?.content ?? []))
      .catch(() => setCompanies([]));
  }, []);

  const runAction = async () => {
    if (!confirm) return;
    try {
      await confirm.run();
      toast.success(confirm.successMessage);
      load();
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setConfirm(null);
    }
  };

  const columns = [
    {
      key: 'fullName',
      label: t('platform.users.user'),
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-gray-900 dark:text-gray-100">{row.fullName}</p>
          <p className="truncate text-xs text-gray-500 dark:text-gray-400">{row.email}</p>
        </div>
      ),
    },
    {
      key: 'username',
      label: t('platform.users.username'),
      className: 'hidden xl:table-cell',
      render: (row) => (
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-gray-700">
          {row.username}
        </code>
      ),
    },
    {
      key: 'companyName',
      label: t('platform.users.company'),
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate text-sm text-gray-900 dark:text-gray-100">{row.companyName}</p>
          {row.companyStatus && row.companyStatus !== 'ACTIVE' && (
            <span className={badgeClass(COMPANY_STATUS_TONE[row.companyStatus])}>
              {t(`platform.companyStatus.${row.companyStatus}`)}
            </span>
          )}
        </div>
      ),
    },
    {
      key: 'role',
      label: t('platform.users.role'),
      className: 'hidden md:table-cell',
      render: (row) => (
        <span className={badgeClass(ROLE_TONE[row.role])}>{t(`roles.${row.role}`)}</span>
      ),
    },
    {
      key: 'lastLoginAt',
      label: t('platform.users.lastLogin'),
      className: 'hidden lg:table-cell',
      render: (row) =>
        row.lastLoginAt ? (
          <span className="text-sm text-gray-500 dark:text-gray-400">
            {formatDate(row.lastLoginAt)}
          </span>
        ) : (
          // Un compte jamais utilisé est le premier signal d'un déploiement qui a échoué
          // chez un client : il mérite mieux qu'un tiret muet.
          <span className="text-sm text-amber-600 dark:text-amber-400">
            {t('platform.users.neverConnected')}
          </span>
        ),
    },
    {
      key: 'active',
      label: t('platform.users.status'),
      render: (row) => (
        <span className={badgeClass(row.active ? 'success' : 'danger')}>
          {t(row.active ? 'common.active' : 'common.inactive')}
        </span>
      ),
    },
  ];

  const actions = (row) => (
    <div className="flex items-center justify-end">
      <button
        onClick={() =>
          setConfirm({
            title: row.active
              ? t('platform.users.deactivateTitle')
              : t('platform.users.activateTitle'),
            message: row.active
              ? t('platform.users.deactivateMessage', { name: row.fullName })
              : t('platform.users.activateMessage', { name: row.fullName }),
            successMessage: row.active
              ? t('platform.users.deactivated')
              : t('platform.users.activated'),
            run: () => platformService.setUserActive(row.id, !row.active),
          })
        }
        className={`rounded p-1.5 ${
          row.active
            ? 'text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10'
            : 'text-green-600 hover:bg-green-50 dark:hover:bg-green-500/10'
        }`}
        title={row.active ? t('platform.users.deactivate') : t('platform.users.activate')}
      >
        {row.active ? <UserX className="h-4 w-4" /> : <UserCheck className="h-4 w-4" />}
      </button>
    </div>
  );

  const resetToFirstPage = (setter) => (value) => {
    setter(value);
    setPage(1);
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {t('platform.users.title')}
        </h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {t('platform.users.subtitle')}
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-[16rem] flex-1">
          <SearchBox
            value={search}
            onChange={resetToFirstPage(setSearch)}
            placeholder={t('platform.users.searchPlaceholder')}
          />
        </div>
        <div className="w-56">
          <FormSelect
            label={t('platform.users.company')}
            name="companyFilter"
            value={companyId}
            onChange={(e) => resetToFirstPage(setCompanyId)(e.target.value)}
            placeholder={t('platform.filters.all')}
            options={companies.map((c) => ({ value: String(c.id), label: c.name }))}
          />
        </div>
        <div className="w-48">
          <FormSelect
            label={t('platform.users.role')}
            name="roleFilter"
            value={role}
            onChange={(e) => resetToFirstPage(setRole)(e.target.value)}
            placeholder={t('platform.filters.all')}
            options={[
              { value: 'ADMIN', label: t('roles.ADMIN') },
              { value: 'CAISSIER', label: t('roles.CAISSIER') },
            ]}
          />
        </div>
        <SegmentedFilter
          label={t('platform.users.status')}
          value={active}
          onChange={resetToFirstPage(setActive)}
          options={STATUS_FILTERS.map((f) => ({
            value: f.value,
            label: t(`platform.users.filter.${f.key}`),
          }))}
        />
      </div>

      <div className="card overflow-hidden">
        <Table columns={columns} data={users} actions={actions} loading={loading} />
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

      <ConfirmModal
        isOpen={Boolean(confirm)}
        onClose={() => setConfirm(null)}
        onConfirm={runAction}
        title={confirm?.title}
        message={confirm?.message}
      />
    </div>
  );
};

export default PlatformUsers;
