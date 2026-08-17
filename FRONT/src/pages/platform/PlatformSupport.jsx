import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Plus, MessageSquare, Lock, Eye } from 'lucide-react';
import Table from '../../components/Table';
import Pagination from '../../components/Pagination';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import FormInput from '../../components/FormInput';
import FormSelect from '../../components/FormSelect';
import SearchBox from '../../components/SearchBox';
import SegmentedFilter from '../../components/SegmentedFilter';
import { badgeClass } from '../../constants/statusBadges';
import { formatDate, formatTime } from '../../utils/format';
import { extractErrorMessage } from '../../utils/apiError';
import platformService from '../../services/platformService';

/**
 * Billetterie du support.
 *
 * Le ticket est ouvert par l'opérateur à partir d'un appel ou d'un courriel : il n'existe
 * pas d'écran client pour en déposer un. Le canal reste humain, GESCOM en garde la trace.
 *
 * Rien n'est envoyé au client depuis cet écran — le fil est un registre. C'est précisément
 * ce que prépare la case « note interne » : le jour où un envoi automatique sera branché,
 * la distinction existera déjà et les notes de service ne partiront pas.
 */

const STATUS_TONE = {
  OPEN: 'info',
  IN_PROGRESS: 'accent',
  WAITING_CUSTOMER: 'warning',
  RESOLVED: 'success',
  CLOSED: 'neutral',
};

const PRIORITY_TONE = { LOW: 'neutral', NORMAL: 'info', HIGH: 'warning', URGENT: 'danger' };

const STATUSES = ['OPEN', 'IN_PROGRESS', 'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED'];
const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
const CATEGORIES = ['TECHNICAL', 'BILLING', 'ACCOUNT', 'FEATURE', 'OTHER'];

const EMPTY_FORM = {
  companyId: '',
  subject: '',
  description: '',
  priority: 'NORMAL',
  category: 'TECHNICAL',
};

const PlatformSupport = () => {
  const { t } = useTranslation();

  const [tickets, setTickets] = useState([]);
  const [companies, setCompanies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [detail, setDetail] = useState(null);
  const [reply, setReply] = useState('');
  const [replyInternal, setReplyInternal] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await platformService.getTickets({
        page: page - 1,
        size: pageSize,
        ...(status ? { status } : {}),
        ...(search ? { search } : {}),
      });
      setTickets(data.content ?? []);
      setTotalItems(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, status, search, t]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    platformService
      .getCompanies({ page: 0, size: 200 })
      .then((response) => setCompanies(response.data?.content ?? []))
      .catch(() => setCompanies([]));
  }, []);

  const setField = (field) => (event) =>
    setForm((current) => ({ ...current, [field]: event.target.value }));

  const openTicket = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      await platformService.openTicket({
        companyId: Number(form.companyId),
        subject: form.subject,
        description: form.description,
        priority: form.priority,
        category: form.category,
        contactUserId: null,
      });
      toast.success(t('platform.support.opened'));
      setCreateOpen(false);
      setForm(EMPTY_FORM);
      load();
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setSaving(false);
    }
  };

  const showDetail = async (ticket) => {
    try {
      const { data } = await platformService.getTicket(ticket.id);
      setDetail(data);
      setReply('');
      setReplyInternal(false);
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  };

  const sendReply = async (event) => {
    event.preventDefault();
    if (!reply.trim()) return;
    setSaving(true);
    try {
      const { data } = await platformService.addTicketMessage(detail.id, {
        body: reply,
        internal: replyInternal,
      });
      setDetail(data);
      setReply('');
      setReplyInternal(false);
      load();
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (value) => {
    try {
      const { data } = await platformService.setTicketStatus(detail.id, value);
      setDetail(data);
      load();
      toast.success(t('platform.support.statusChanged'));
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  };

  const columns = [
    {
      key: 'reference',
      label: t('platform.support.reference'),
      render: (ticket) => (
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-gray-700">
          {ticket.reference}
        </code>
      ),
    },
    {
      key: 'subject',
      label: t('platform.support.subject'),
      render: (ticket) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-gray-900 dark:text-gray-100">{ticket.subject}</p>
          <p className="truncate text-xs text-gray-500 dark:text-gray-400">{ticket.companyName}</p>
        </div>
      ),
    },
    {
      key: 'category',
      label: t('platform.support.category'),
      className: 'hidden xl:table-cell',
      render: (ticket) => (
        <span className="text-sm text-gray-500 dark:text-gray-400">
          {t(`platform.support.categories.${ticket.category}`)}
        </span>
      ),
    },
    {
      key: 'messageCount',
      label: t('platform.support.messages'),
      className: 'hidden lg:table-cell',
      render: (ticket) => (
        <span className="flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400">
          <MessageSquare className="h-3.5 w-3.5" />
          {ticket.messageCount}
        </span>
      ),
    },
    {
      key: 'createdAt',
      label: t('platform.support.opened'),
      className: 'hidden md:table-cell',
      render: (ticket) => (
        <span className="text-sm text-gray-500 dark:text-gray-400">
          {formatDate(ticket.createdAt)}
        </span>
      ),
    },
    {
      key: 'priority',
      label: t('platform.support.priority'),
      render: (ticket) => (
        <span className={badgeClass(PRIORITY_TONE[ticket.priority])}>
          {t(`platform.support.priorities.${ticket.priority}`)}
        </span>
      ),
    },
    {
      key: 'status',
      label: t('platform.support.status'),
      render: (ticket) => (
        <span className={badgeClass(STATUS_TONE[ticket.status])}>
          {t(`platform.support.statuses.${ticket.status}`)}
        </span>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {t('platform.support.title')}
          </h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {t('platform.support.subtitle')}
          </p>
        </div>
        <Button onClick={() => setCreateOpen(true)} icon={Plus}>
          {t('platform.support.newTicket')}
        </Button>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-[16rem] flex-1">
          <SearchBox
            value={search}
            onChange={(value) => {
              setSearch(value);
              setPage(1);
            }}
            placeholder={t('platform.support.searchPlaceholder')}
          />
        </div>
        <SegmentedFilter
          label={t('platform.support.status')}
          value={status}
          onChange={(next) => {
            setStatus(next);
            setPage(1);
          }}
          options={[
            { value: '', label: t('platform.filters.all') },
            ...STATUSES.map((s) => ({ value: s, label: t(`platform.support.statuses.${s}`) })),
          ]}
        />
      </div>

      <div className="card overflow-hidden">
        {/* La ligne entière reste cliquable, mais un bouton explicite rend l'action visible :
            un tableau dont il faut deviner que les lignes s'ouvrent ne se découvre pas. */}
        <Table
          columns={columns}
          data={tickets}
          loading={loading}
          onRowClick={showDetail}
          actions={(ticket) => (
            <div className="flex items-center justify-end">
              <button
                onClick={(e) => {
                  // Sans cela, le clic remonterait au <tr> et ouvrirait la fiche deux fois.
                  e.stopPropagation();
                  showDetail(ticket);
                }}
                className="rounded p-1.5 text-gray-500 hover:bg-gray-100 hover:text-gray-900 dark:hover:bg-gray-700 dark:hover:text-gray-100"
                title={t('common.viewDetails')}
              >
                <Eye className="h-4 w-4" />
              </button>
            </div>
          )}
        />
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

      {/* Ouverture d'un ticket */}
      <Modal
        isOpen={createOpen}
        onClose={() => setCreateOpen(false)}
        title={t('platform.support.newTicket')}
      >
        <form onSubmit={openTicket} className="space-y-4">
          <FormSelect
            label={t('platform.support.company')}
            name="companyId"
            value={form.companyId}
            onChange={setField('companyId')}
            required
            options={companies.map((c) => ({ value: String(c.id), label: c.name }))}
          />
          <FormInput
            label={t('platform.support.subject')}
            name="subject"
            value={form.subject}
            onChange={setField('subject')}
            required
          />
          <div>
            <label
              htmlFor="description"
              className="block text-sm font-medium text-gray-700 dark:text-gray-300"
            >
              {t('platform.support.description')} <span className="text-red-500">*</span>
            </label>
            <textarea
              id="description"
              name="description"
              rows={5}
              required
              value={form.description}
              onChange={setField('description')}
              className="input-field mt-2 w-full"
              placeholder={t('platform.support.descriptionPlaceholder')}
            />
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormSelect
              label={t('platform.support.priority')}
              name="priority"
              value={form.priority}
              onChange={setField('priority')}
              allowEmpty={false}
              options={PRIORITIES.map((p) => ({
                value: p,
                label: t(`platform.support.priorities.${p}`),
              }))}
            />
            <FormSelect
              label={t('platform.support.category')}
              name="category"
              value={form.category}
              onChange={setField('category')}
              allowEmpty={false}
              options={CATEGORIES.map((c) => ({
                value: c,
                label: t(`platform.support.categories.${c}`),
              }))}
            />
          </div>
          <div className="flex justify-end gap-3 border-t border-gray-200 pt-4 dark:border-gray-700">
            <Button type="button" variant="secondary" onClick={() => setCreateOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" loading={saving}>
              {t('platform.support.open')}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Détail : fil de discussion */}
      <Modal
        isOpen={Boolean(detail)}
        onClose={() => setDetail(null)}
        title={detail ? `${detail.reference} — ${detail.subject}` : ''}
        size="lg"
      >
        {detail && (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-3">
              <span className={badgeClass(PRIORITY_TONE[detail.priority])}>
                {t(`platform.support.priorities.${detail.priority}`)}
              </span>
              <span className="text-sm text-gray-500 dark:text-gray-400">
                {detail.companyName} · {t(`platform.support.categories.${detail.category}`)}
              </span>
              <div className="ml-auto w-52">
                <FormSelect
                  name="ticketStatus"
                  value={detail.status}
                  onChange={(e) => changeStatus(e.target.value)}
                  allowEmpty={false}
                  options={STATUSES.map((s) => ({
                    value: s,
                    label: t(`platform.support.statuses.${s}`),
                  }))}
                />
              </div>
            </div>

            <div className="max-h-80 space-y-3 overflow-y-auto pr-1">
              {detail.messages?.map((message) => (
                <div
                  key={message.id}
                  className={`rounded-lg border p-3 ${
                    message.internal
                      ? 'border-amber-300 bg-amber-50 dark:border-amber-500/40 dark:bg-amber-500/10'
                      : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'
                  }`}
                >
                  <div className="mb-1 flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
                    <span className="font-medium text-gray-700 dark:text-gray-300">
                      {message.authorName}
                    </span>
                    <span>
                      {formatDate(message.createdAt)} {formatTime(message.createdAt)}
                    </span>
                    {message.internal && (
                      <span className="ml-auto flex items-center gap-1 font-medium text-amber-700 dark:text-amber-400">
                        <Lock className="h-3 w-3" />
                        {t('platform.support.internalNote')}
                      </span>
                    )}
                  </div>
                  <p className="whitespace-pre-wrap text-sm text-gray-800 dark:text-gray-200">
                    {message.body}
                  </p>
                </div>
              ))}
            </div>

            {detail.status === 'CLOSED' ? (
              <p className="rounded-lg border border-gray-200 bg-gray-50 p-3 text-sm text-gray-500 dark:border-gray-700 dark:bg-gray-800/60 dark:text-gray-400">
                {t('platform.support.closedHint')}
              </p>
            ) : (
              <form onSubmit={sendReply} className="space-y-3 border-t border-gray-200 pt-4 dark:border-gray-700">
                <textarea
                  rows={3}
                  value={reply}
                  onChange={(e) => setReply(e.target.value)}
                  className="input-field w-full"
                  placeholder={t('platform.support.replyPlaceholder')}
                />
                <div className="flex items-center justify-between gap-3">
                  <label className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
                    <input
                      type="checkbox"
                      checked={replyInternal}
                      onChange={(e) => setReplyInternal(e.target.checked)}
                      className="rounded border-gray-300"
                    />
                    {t('platform.support.markInternal')}
                  </label>
                  <Button type="submit" loading={saving} disabled={!reply.trim()}>
                    {t('platform.support.addMessage')}
                  </Button>
                </div>
              </form>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default PlatformSupport;
