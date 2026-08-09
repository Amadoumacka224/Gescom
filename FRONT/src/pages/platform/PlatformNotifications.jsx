import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { CheckCheck, Building2, Eye } from 'lucide-react';
import Pagination from '../../components/Pagination';
import Modal from '../../components/Modal';
import Button from '../../components/Button';
import SegmentedFilter from '../../components/SegmentedFilter';
import { badgeClass } from '../../constants/statusBadges';
import { formatDate, formatTime } from '../../utils/format';
import { extractErrorMessage } from '../../utils/apiError';
import platformService from '../../services/platformService';

/**
 * Journal des événements de la plateforme.
 *
 * À distinguer des alertes du tableau de bord : celles-ci décrivent l'état courant du parc
 * et disparaissent quand la situation est réglée. Ici, chaque ligne est un fait daté qui
 * reste consultable ensuite. Les deux répondent à des questions différentes — « qu'est-ce
 * qui ne va pas maintenant ? » et « que s'est-il passé pendant mon absence ? ».
 *
 * Une liste plutôt qu'un tableau : la lecture se fait au fil, et l'état lu/non-lu doit se
 * voir d'un coup d'œil sans chercher dans une colonne.
 */

const SEVERITY_TONE = { CRITICAL: 'danger', WARNING: 'warning', INFO: 'info' };

const PlatformNotifications = () => {
  const { t } = useTranslation();

  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [unreadOnly, setUnreadOnly] = useState('false');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [detail, setDetail] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await platformService.getNotifications({
        page: page - 1,
        size: pageSize,
        unreadOnly: unreadOnly === 'true',
      });
      setNotifications(data.content ?? []);
      setTotalItems(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, unreadOnly, t]);

  useEffect(() => {
    load();
  }, [load]);

  const markRead = async (notification) => {
    if (notification.readAt) return;
    try {
      await platformService.markNotificationRead(notification.id);
      load();
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  };

  /**
   * Ouvre la fiche et marque comme lue dans le même geste.
   *
   * Le contenu vient de la ligne déjà chargée : la liste porte tout ce que la fiche montre,
   * un aller-retour serveur n'apprendrait rien de plus.
   */
  const openDetail = (notification) => {
    setDetail(notification);
    markRead(notification);
  };

  const markAllRead = async () => {
    try {
      const { data } = await platformService.markAllNotificationsRead();
      toast.success(t('platform.notifications.allRead', { count: data.updated ?? 0 }));
      load();
    } catch (error) {
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {t('platform.notifications.title')}
          </h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {t('platform.notifications.subtitle')}
          </p>
        </div>
        <Button onClick={markAllRead} variant="secondary" icon={CheckCheck}>
          {t('platform.notifications.markAllRead')}
        </Button>
      </div>

      <SegmentedFilter
        label={t('platform.notifications.filter')}
        value={unreadOnly}
        onChange={(next) => {
          setUnreadOnly(next);
          setPage(1);
        }}
        options={[
          { value: 'false', label: t('platform.filters.all') },
          { value: 'true', label: t('platform.notifications.unreadOnly') },
        ]}
      />

      <div className="card overflow-hidden">
        {loading ? (
          <div className="space-y-3 p-5">
            {[0, 1, 2].map((i) => (
              <div key={i} className="h-16 animate-pulse rounded-lg bg-gray-100 dark:bg-gray-700" />
            ))}
          </div>
        ) : notifications.length === 0 ? (
          <p className="p-10 text-center text-sm text-gray-500 dark:text-gray-400">
            {t('platform.notifications.empty')}
          </p>
        ) : (
          <ul className="divide-y divide-gray-200 dark:divide-gray-700">
            {notifications.map((n) => (
              <li
                key={n.id}
                onClick={() => openDetail(n)}
                className={`flex cursor-pointer items-start gap-4 p-4 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/40 ${
                  n.readAt ? '' : 'bg-primary-50/40 dark:bg-primary-500/5'
                }`}
              >
                {/* Pastille de non-lu : l'information la plus scannée de la liste. */}
                <span
                  className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${
                    n.readAt ? 'bg-transparent' : 'bg-primary-500'
                  }`}
                  aria-hidden="true"
                />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className={badgeClass(SEVERITY_TONE[n.severity])}>
                      {t(`platform.severity.${n.severity}`)}
                    </span>
                    <p
                      className={`truncate text-sm ${
                        n.readAt
                          ? 'text-gray-700 dark:text-gray-300'
                          : 'font-semibold text-gray-900 dark:text-gray-100'
                      }`}
                    >
                      {n.title}
                    </p>
                  </div>
                  {n.message && (
                    <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{n.message}</p>
                  )}
                  <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
                    <span>
                      {formatDate(n.createdAt)} {formatTime(n.createdAt)}
                    </span>
                    {n.companyId && (
                      <Link
                        to="/platform/companies"
                        onClick={(e) => e.stopPropagation()}
                        className="flex items-center gap-1 hover:text-primary-600 hover:underline dark:hover:text-primary-400"
                      >
                        <Building2 className="h-3 w-3" />
                        {n.companyName}
                      </Link>
                    )}
                  </div>
                </div>

                <button
                  onClick={(e) => {
                    // Le <li> est déjà cliquable ; sans cela l'ouverture se déclencherait deux fois.
                    e.stopPropagation();
                    openDetail(n);
                  }}
                  className="shrink-0 rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-900 dark:hover:bg-gray-700 dark:hover:text-gray-100"
                  title={t('common.viewDetails')}
                >
                  <Eye className="h-4 w-4" />
                </button>
              </li>
            ))}
          </ul>
        )}

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
        isOpen={Boolean(detail)}
        onClose={() => setDetail(null)}
        title={t('platform.notifications.detailTitle')}
      >
        {detail && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              <span className={badgeClass(SEVERITY_TONE[detail.severity])}>
                {t(`platform.severity.${detail.severity}`)}
              </span>
              {/* Type affiché brut : c'est ce qu'on cite au support ou qu'on cherche dans
                  les journaux, le traduire le rendrait introuvable. */}
              <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-gray-700">
                {detail.type}
              </code>
            </div>

            <div>
              <h3 className="text-base font-semibold text-gray-900 dark:text-gray-100">
                {detail.title}
              </h3>
              {/* La liste tronque le message ; la fiche le montre en entier. */}
              {detail.message && (
                <p className="mt-1 whitespace-pre-wrap text-sm text-gray-600 dark:text-gray-300">
                  {detail.message}
                </p>
              )}
            </div>

            <dl className="grid grid-cols-2 gap-x-4 gap-y-2 rounded-lg border border-gray-200 p-3 text-sm dark:border-gray-700">
              <div className="min-w-0">
                <dt className="text-xs text-gray-500 dark:text-gray-400">
                  {t('platform.companies.company')}
                </dt>
                <dd className="truncate font-medium text-gray-900 dark:text-gray-100">
                  {detail.companyName || '—'}
                </dd>
              </div>
              <div className="min-w-0">
                <dt className="text-xs text-gray-500 dark:text-gray-400">
                  {t('platform.notifications.occurredAt')}
                </dt>
                <dd className="font-medium text-gray-900 dark:text-gray-100">
                  {formatDate(detail.createdAt)} {formatTime(detail.createdAt)}
                </dd>
              </div>
            </dl>

            <div className="flex justify-end border-t border-gray-200 pt-4 dark:border-gray-700">
              <Button variant="secondary" onClick={() => setDetail(null)}>
                {t('common.close')}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default PlatformNotifications;
