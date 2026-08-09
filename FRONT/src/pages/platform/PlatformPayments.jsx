import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Plus } from 'lucide-react';
import Table from '../../components/Table';
import Pagination from '../../components/Pagination';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import FormInput from '../../components/FormInput';
import FormSelect from '../../components/FormSelect';
import SegmentedFilter from '../../components/SegmentedFilter';
import { badgeClass } from '../../constants/statusBadges';
import { formatCurrency, formatDate } from '../../utils/format';
import { extractErrorMessage } from '../../utils/apiError';
import platformService from '../../services/platformService';

/**
 * Registre des encaissements d'abonnement — les versements des entreprises à GESCOM.
 *
 * À ne pas confondre avec la page Caisse, qui concerne les encaissements d'une entreprise
 * auprès de ses propres clients.
 *
 * Les échecs se saisissent comme les succès, et c'est voulu : sans eux, ni taux de réussite
 * ni suivi des impayés. Enregistrer un échec bascule d'ailleurs l'abonnement en impayé,
 * comme un succès en renouvelle la période — le serveur s'en charge.
 */

const STATUS_TONE = {
  SUCCEEDED: 'success',
  PENDING: 'info',
  FAILED: 'danger',
  REFUNDED: 'warning',
};

const STATUS_FILTERS = ['', 'SUCCEEDED', 'PENDING', 'FAILED', 'REFUNDED'];

const EMPTY_FORM = {
  companyId: '',
  amount: '',
  status: 'SUCCEEDED',
  method: 'TRANSFER',
  failureMessage: '',
};

const PlatformPayments = () => {
  const { t } = useTranslation();

  const [payments, setPayments] = useState([]);
  const [companies, setCompanies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await platformService.getPayments({
        page: page - 1,
        size: pageSize,
        ...(status ? { status } : {}),
      });
      const data = response.data;
      setPayments(data.content ?? []);
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

  // Liste déroulante des entreprises du formulaire : une page large suffit, le parc d'un
  // back-office SaaS se compte en centaines, pas en millions.
  useEffect(() => {
    platformService
      .getCompanies({ page: 0, size: 200 })
      .then((response) => setCompanies(response.data?.content ?? []))
      .catch(() => setCompanies([]));
  }, []);

  const setField = (field) => (event) =>
    setForm((current) => ({ ...current, [field]: event.target.value }));

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      await platformService.recordPayment({
        companyId: Number(form.companyId),
        subscriptionId: null,
        amount: Number(form.amount),
        status: form.status,
        method: form.method,
        failureMessage: form.status === 'FAILED' ? form.failureMessage || null : null,
      });
      toast.success(t('platform.payments.recorded'));
      setModalOpen(false);
      setForm(EMPTY_FORM);
      load();
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      key: 'reference',
      label: t('platform.payments.reference'),
      render: (row) => (
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-gray-700">
          {row.reference}
        </code>
      ),
    },
    {
      key: 'companyName',
      label: t('platform.payments.company'),
      render: (row) => (
        <span className="font-medium text-gray-900 dark:text-gray-100">{row.companyName}</span>
      ),
    },
    {
      key: 'amount',
      label: t('platform.payments.amount'),
      render: (row) => (
        <span className="font-semibold text-gray-900 dark:text-gray-100">
          {formatCurrency(row.amount)}
        </span>
      ),
    },
    {
      key: 'method',
      label: t('platform.payments.method'),
      className: 'hidden lg:table-cell',
      render: (row) => (
        <span className="text-sm text-gray-500 dark:text-gray-400">
          {t(`platform.paymentMethod.${row.method}`)}
        </span>
      ),
    },
    {
      key: 'paidAt',
      label: t('platform.payments.date'),
      className: 'hidden md:table-cell',
      render: (row) => (
        <span className="text-sm text-gray-500 dark:text-gray-400">
          {formatDate(row.paidAt ?? row.createdAt)}
        </span>
      ),
    },
    {
      key: 'status',
      label: t('platform.payments.status'),
      render: (row) => (
        <div>
          <span className={badgeClass(STATUS_TONE[row.status])}>
            {t(`platform.paymentStatus.${row.status}`)}
          </span>
          {row.failureMessage && (
            <p className="mt-1 max-w-[16rem] truncate text-xs text-red-600 dark:text-red-400">
              {row.failureMessage}
            </p>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {t('platform.payments.title')}
          </h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {t('platform.payments.subtitle')}
          </p>
        </div>
        <Button onClick={() => setModalOpen(true)} icon={Plus}>
          {t('platform.payments.record')}
        </Button>
      </div>

      <SegmentedFilter
        label={t('platform.payments.status')}
        value={status}
        onChange={(next) => {
          setStatus(next);
          setPage(1);
        }}
        options={STATUS_FILTERS.map((value) => ({
          value,
          label: value ? t(`platform.paymentStatus.${value}`) : t('platform.filters.all'),
        }))}
      />

      <div className="card overflow-hidden">
        <Table columns={columns} data={payments} loading={loading} />
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

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title={t('platform.payments.recordTitle')}
      >
        <form onSubmit={submit} className="space-y-4">
          <FormSelect
            label={t('platform.payments.company')}
            name="companyId"
            value={form.companyId}
            onChange={setField('companyId')}
            required
            options={companies.map((company) => ({
              value: String(company.id),
              label: company.name,
            }))}
          />
          <FormInput
            label={t('platform.payments.amount')}
            name="amount"
            type="number"
            step="0.01"
            min="0"
            value={form.amount}
            onChange={setField('amount')}
            required
          />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormSelect
              label={t('platform.payments.status')}
              name="status"
              value={form.status}
              onChange={setField('status')}
              placeholder={t('platform.paymentStatus.SUCCEEDED')}
              options={['SUCCEEDED', 'PENDING', 'FAILED', 'REFUNDED'].map((value) => ({
                value,
                label: t(`platform.paymentStatus.${value}`),
              }))}
            />
            <FormSelect
              label={t('platform.payments.method')}
              name="method"
              value={form.method}
              onChange={setField('method')}
              placeholder={t('platform.paymentMethod.TRANSFER')}
              options={['TRANSFER', 'CARD', 'DIRECT_DEBIT', 'MANUAL'].map((value) => ({
                value,
                label: t(`platform.paymentMethod.${value}`),
              }))}
            />
          </div>

          {/* Le motif n'a de sens que sur un échec : c'est ce qui distingue un rejet
              bancaire d'un virement simplement en attente. */}
          {form.status === 'FAILED' && (
            <FormInput
              label={t('platform.payments.failureMessage')}
              name="failureMessage"
              value={form.failureMessage}
              onChange={setField('failureMessage')}
            />
          )}

          <div className="flex justify-end gap-3 border-t border-gray-200 pt-4 dark:border-gray-700">
            <Button type="button" variant="secondary" onClick={() => setModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" loading={saving}>
              {t('common.save')}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default PlatformPayments;
