import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { RefreshCw, XCircle } from 'lucide-react';
import Table from '../../components/Table';
import Pagination from '../../components/Pagination';
import ConfirmModal from '../../components/ConfirmModal';
import SegmentedFilter from '../../components/SegmentedFilter';
import { badgeClass } from '../../constants/statusBadges';
import { formatCurrency, formatDate } from '../../utils/format';
import { extractErrorMessage } from '../../utils/apiError';
import platformService from '../../services/platformService';

/**
 * Contrats du parc.
 *
 * `monthlyAmount` est affiché à côté du montant facturé : c'est la contribution réelle du
 * contrat au MRR, un abonnement annuel n'y pesant qu'au douzième. Le serveur le calcule, la
 * page ne le recalcule pas — deux règles d'arrondi divergentes finiraient par ne plus
 * s'accorder avec le tableau de bord.
 */

const STATUS_TONE = {
  ACTIVE: 'success',
  TRIALING: 'info',
  PAST_DUE: 'warning',
  CANCELED: 'danger',
  EXPIRED: 'neutral',
};

const STATUS_FILTERS = ['', 'ACTIVE', 'TRIALING', 'PAST_DUE', 'CANCELED', 'EXPIRED'];

const PlatformSubscriptions = () => {
  const { t } = useTranslation();

  const [subscriptions, setSubscriptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [confirm, setConfirm] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await platformService.getSubscriptions({
        page: page - 1,
        size: pageSize,
        ...(status ? { status } : {}),
      });
      const data = response.data;
      setSubscriptions(data.content ?? []);
      setTotalItems(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, status, t]);

  useEffect(() => {
    load();
  }, [load]);

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
      key: 'companyName',
      label: t('platform.subscriptions.company'),
      render: (row) => (
        <span className="font-medium text-gray-900 dark:text-gray-100">{row.companyName}</span>
      ),
    },
    {
      key: 'planName',
      label: t('platform.subscriptions.plan'),
      render: (row) => (
        <div>
          <p className="text-sm text-gray-900 dark:text-gray-100">{row.planName}</p>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {t(`platform.billingPeriod.${row.billingPeriod}`)}
          </p>
        </div>
      ),
    },
    {
      key: 'amount',
      label: t('platform.subscriptions.amount'),
      render: (row) => (
        <div>
          <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {formatCurrency(row.amount)}
          </p>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {t('platform.subscriptions.mrrShare', { value: formatCurrency(row.monthlyAmount) })}
          </p>
        </div>
      ),
    },
    {
      key: 'currentPeriodEnd',
      label: t('platform.subscriptions.periodEnd'),
      className: 'hidden md:table-cell',
      render: (row) => (
        <span className="text-sm text-gray-500 dark:text-gray-400">
          {formatDate(row.currentPeriodEnd)}
        </span>
      ),
    },
    {
      key: 'status',
      label: t('platform.subscriptions.status'),
      render: (row) => (
        <span className={badgeClass(STATUS_TONE[row.status])}>
          {t(`platform.subscriptionStatus.${row.status}`)}
        </span>
      ),
    },
  ];

  // Un contrat clos (résilié, expiré) n'offre plus d'action : ni renouvellement ni résiliation.
  const isLive = (row) => ['ACTIVE', 'TRIALING', 'PAST_DUE'].includes(row.status);

  const actions = (row) =>
    isLive(row) ? (
      <div className="flex items-center justify-end gap-1">
        <button
          onClick={() =>
            setConfirm({
              title: t('platform.subscriptions.renewTitle'),
              message: t('platform.subscriptions.renewMessage', { name: row.companyName }),
              successMessage: t('platform.subscriptions.renewed'),
              run: () => platformService.renewSubscription(row.id),
            })
          }
          className="rounded p-1.5 text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-500/10"
          title={t('platform.subscriptions.renew')}
        >
          <RefreshCw className="h-4 w-4" />
        </button>
        <button
          onClick={() =>
            setConfirm({
              title: t('platform.subscriptions.cancelTitle'),
              message: t('platform.subscriptions.cancelMessage', { name: row.companyName }),
              successMessage: t('platform.subscriptions.canceled'),
              run: () => platformService.cancelSubscription(row.id, null),
            })
          }
          className="rounded p-1.5 text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10"
          title={t('platform.subscriptions.cancel')}
        >
          <XCircle className="h-4 w-4" />
        </button>
      </div>
    ) : null;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {t('platform.subscriptions.title')}
        </h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {t('platform.subscriptions.subtitle')}
        </p>
      </div>

      <SegmentedFilter
        label={t('platform.subscriptions.status')}
        value={status}
        onChange={(next) => {
          setStatus(next);
          setPage(1);
        }}
        options={STATUS_FILTERS.map((value) => ({
          value,
          label: value ? t(`platform.subscriptionStatus.${value}`) : t('platform.filters.all'),
        }))}
      />

      <div className="card overflow-hidden">
        <Table columns={columns} data={subscriptions} actions={actions} loading={loading} />
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

export default PlatformSubscriptions;
