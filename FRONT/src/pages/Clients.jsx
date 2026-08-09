import { useState, useEffect, useMemo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  Plus, Edit, Trash2, Mail, Phone, MapPin, Building2, User, Users, UserCheck,
  Eye, RefreshCw, Download, Hash, CalendarClock, X, Copy, ShoppingCart,
} from 'lucide-react';
import { toast } from 'react-hot-toast';
import { useAuth } from '../context/useAuth';
import clientService from '../services/clientService';
import api from '../services/api';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Pagination from '../components/Pagination';
import Button from '../components/Button';
import Table from '../components/Table';
import SearchBox from '../components/SearchBox';
import StatCard from '../components/StatCard';
import SegmentedFilter from '../components/SegmentedFilter';
import InfoRow from '../components/InfoRow';
import AdvancedFilters from '../components/AdvancedFilters';
import OrderStatusBadge from '../components/OrderStatusBadge';
import ClientFormFields from '../components/ClientFormFields';
import { rankSuggestions } from '../utils/searchSuggestions';
import { formatCurrency, formatDate, formatPercent, safeRatio } from '../utils/format';
import {
  CLIENT_FIELD_ORDER,
  EMPTY_CLIENT_FORM,
  buildClientPayload,
  isClientFormDirty,
  validateClient,
} from '../utils/clientForm';

/** Mémorise le mode d'affichage entre deux visites, comme la page Produits. */
const VIEW_MODE_KEY = 'clientsViewMode';

/** Nombre de clients mis en avant dans la vue d'aperçu (les derniers ajoutés). */
const RECENT_COUNT = 5;

/** Commandes détaillées dans l'activité commerciale de la fiche ; au-delà, un simple décompte. */
const ACTIVITY_ORDERS_SHOWN = 3;

/**
 * Critères de filtrage, à l'état neutre. Sert de valeur initiale, de cible du bouton
 * « Réinitialiser » et de référence pour savoir quels critères sont actifs.
 * Type et statut y figurent au même titre que les autres : ils étaient portés par des bandeaux
 * segmentés au-dessus de la liste, qui doublonnaient avec les tuiles d'indicateurs.
 */
const EMPTY_ADVANCED = {
  type: 'ALL',
  status: 'ALL',
  city: '',
  country: '',
  company: '',
  contact: 'ALL',
  createdFrom: '',
  createdTo: '',
};

const fullName = (client) => `${client?.firstName || ''} ${client?.lastName || ''}`.trim();

/** Adresse postale mise en lignes, dans l'ordre où on l'écrit sur une enveloppe. */
const addressLines = (client) => [
  client?.address,
  [client?.postalCode, client?.city].filter(Boolean).join(' '),
  client?.country,
].filter((line) => line && line.trim());

/** Copie d'une valeur isolée (e-mail, téléphone, adresse), au bout de sa propre ligne. */
const CopyButton = ({ value, label, onCopy }) => (
  <button
    type="button"
    onClick={() => onCopy(value)}
    className="rounded-lg p-2 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-700 dark:hover:text-gray-200"
    title={label}
    aria-label={label}
  >
    <Copy className="h-4 w-4" aria-hidden="true" />
  </button>
);

/** Indicateur chiffré de l'activité commerciale (commandes, chiffre d'affaires, panier moyen). */
const ActivityTile = ({ label, value }) => (
  <div className="rounded-xl border border-gray-200 px-4 py-3 dark:border-gray-700">
    <dt className="text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">{label}</dt>
    <dd className="mt-1 text-lg font-semibold tabular-nums text-gray-900 dark:text-gray-100">{value}</dd>
  </div>
);

/** Raccourci vers le formulaire, proposé là où une information essentielle manque. */
const CompleteButton = ({ label, onClick }) => (
  <button
    type="button"
    onClick={onClick}
    className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-xs font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-500/10"
  >
    <Plus className="h-3.5 w-3.5" aria-hidden="true" />
    {label}
  </button>
);

const initials = (client) =>
  `${(client?.firstName || '?').charAt(0)}${(client?.lastName || '').charAt(0)}`.toUpperCase();

const formatDateTime = (iso) =>
  iso
    ? new Date(iso).toLocaleString('fr-FR', {
        day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
      })
    : '—';

/**
 * Pastille d'identité. Le particulier porte ses initiales dans un disque, l'entreprise un
 * médaillon carré à l'icône d'immeuble : le type se lit avant même la colonne « Type », ce qui
 * évite d'avoir à parcourir la ligne entière pour savoir à qui l'on a affaire.
 */
const ClientAvatar = ({ client, size = 'md' }) => {
  const isCompany = client?.type === 'ENTREPRISE';
  const dimensions = size === 'lg' ? 'w-14 h-14 text-lg' : 'w-10 h-10 text-sm';

  return (
    <div
      aria-hidden="true"
      className={`${dimensions} flex items-center justify-center flex-shrink-0 font-semibold ${
        isCompany
          ? 'rounded-xl bg-secondary-100 text-secondary-700 dark:bg-secondary-400/20 dark:text-secondary-200'
          : 'rounded-full bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-200'
      }`}
    >
      {isCompany
        ? <Building2 className={size === 'lg' ? 'w-7 h-7' : 'w-5 h-5'} />
        : initials(client)}
    </div>
  );
};

const Clients = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  // Le backend réserve la suppression, la désactivation et l'export à l'ADMIN
  // (cf. ClientController) : on masque ces actions au caissier plutôt que de le laisser
  // déclencher un 403. La création et la modification lui restent ouvertes.
  const isAdmin = user?.role === 'ADMIN';

  const [clients, setClients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [searchTerm, setSearchTerm] = useState('');
  const [advanced, setAdvanced] = useState(EMPTY_ADVANCED);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const [sortConfig, setSortConfig] = useState({ key: 'createdAt', direction: 'desc' });

  const [showModal, setShowModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [editingClient, setEditingClient] = useState(null);
  const [selectedClient, setSelectedClient] = useState(null);
  const [clientToDelete, setClientToDelete] = useState(null);

  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  // Vue par défaut : seul le dernier client ajouté est mis en avant ; la liste complète
  // s'obtient par la bascule d'affichage, ou dès qu'une recherche / un filtre est actif.
  const [viewMode, setViewMode] = useState(() => localStorage.getItem(VIEW_MODE_KEY) || 'recent');

  const [formData, setFormData] = useState(EMPTY_CLIENT_FORM);
  // Valeurs à l'ouverture : comparées à la saisie pour savoir si le formulaire a bougé
  // (bouton d'enregistrement inutile à vide, garde-fou à la fermeture).
  const [initialForm, setInitialForm] = useState(EMPTY_CLIENT_FORM);
  const [touched, setTouched] = useState({});
  const [submitAttempted, setSubmitAttempted] = useState(false);
  // Erreurs renvoyées par l'API (`fieldErrors` du GlobalExceptionHandler, email déjà pris…) :
  // conservées à part des erreurs locales, elles ne se recalculent pas à la frappe et sont
  // levées champ par champ dès que l'utilisateur corrige la valeur incriminée.
  const [serverErrors, setServerErrors] = useState({});
  const [showDiscardConfirm, setShowDiscardConfirm] = useState(false);

  // Activité commerciale de la fiche : chargée à l'ouverture d'un client, jamais avec la liste.
  // Le répertoire n'a pas besoin des commandes, et les charger toutes pour n'en montrer que
  // celles d'un client coûterait une requête énorme à chaque affichage de la page.
  const [activity, setActivity] = useState({ loading: false, error: false, orders: [] });

  useEffect(() => {
    fetchClients();
  }, []);

  const fetchClients = async () => {
    setLoading(true);
    try {
      const response = await clientService.getAllClients();
      setClients(response.data);
    } catch (error) {
      console.error('Error fetching clients:', error);
      toast.error(t('clients.loadError'));
    } finally {
      setLoading(false);
    }
  };

  // ---- Formulaire : saisie, validation, enregistrement ----

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    // Le verdict du serveur portait sur l'ancienne valeur : il n'a plus de sens dès qu'elle change.
    setServerErrors((prev) => (prev[name] === undefined ? prev : { ...prev, [name]: undefined }));
  };

  // Une erreur ne s'affiche qu'une fois le champ quitté : signaler « le prénom est obligatoire »
  // à la première lettre tapée serait juste mais insupportable.
  const handleBlur = (e) => {
    const { name } = e.target;
    setTouched((prev) => (prev[name] ? prev : { ...prev, [name]: true }));
  };

  const formErrors = useMemo(() => validateClient(formData, t), [formData, t]);

  const visibleErrors = useMemo(() => {
    const shown = {};
    Object.entries(formErrors).forEach(([field, message]) => {
      if (submitAttempted || touched[field]) shown[field] = message;
    });
    Object.entries(serverErrors).forEach(([field, message]) => {
      if (message) shown[field] = message;
    });
    return shown;
  }, [formErrors, serverErrors, submitAttempted, touched]);

  const isDirty = isClientFormDirty(formData, initialForm);
  // En modification, un enregistrement à l'identique n'apporte rien : le bouton reste inactif
  // tant que rien n'a bougé, et la mention à côté explique pourquoi.
  const canSubmit = !saving && (!editingClient || isDirty);

  const focusField = (field) => {
    document.getElementById(field)?.focus();
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setSubmitAttempted(true);

    const remaining = CLIENT_FIELD_ORDER.filter((field) => formErrors[field]);
    if (remaining.length > 0) {
      focusField(remaining[0]);
      return;
    }
    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    setSaving(true);
    const toastId = 'client-save';
    toast.loading(editingClient ? t('clients.savingEdit') : t('clients.savingCreate'), { id: toastId });

    try {
      const payload = buildClientPayload(formData);
      if (editingClient) {
        await clientService.updateClient(editingClient.id, payload);
        toast.success(t('clients.updatedSuccess'), { id: toastId });
      } else {
        await clientService.createClient(payload);
        toast.success(t('clients.createdSuccess'), { id: toastId });
      }

      await fetchClients();
      closeForm();
    } catch (error) {
      console.error('Error saving client:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string' ? raw : (raw?.message || raw?.error || t('clients.saveError'));

      // Le refus du serveur est ramené sur le champ concerné plutôt que sur un simple toast :
      // l'utilisateur voit quoi corriger sans relire tout le formulaire.
      const fieldErrors = typeof raw === 'object' && raw?.fieldErrors ? { ...raw.fieldErrors } : {};
      if (error.response?.status === 409) {
        fieldErrors.email = t('clients.errorEmailTaken');
      }
      const flagged = CLIENT_FIELD_ORDER.filter((field) => fieldErrors[field]);
      if (flagged.length > 0) {
        setServerErrors(fieldErrors);
        setSubmitAttempted(true);
        setTimeout(() => focusField(flagged[0]), 0);
      }

      toast.error(`${t('common.errorPrefix')}${message}`, { id: toastId, duration: 6000 });
    } finally {
      setSaving(false);
    }
  };

  /** Ouvre le formulaire sur des valeurs données, en repartant d'un état de validation vierge. */
  const openForm = (client) => {
    // On ne reprend que les champs du formulaire : l'objet reçu de l'API porte aussi `id`,
    // `name`, `createdAt`… que le DTO de requête n'attend pas.
    const values = client
      ? {
          firstName: client.firstName || '',
          lastName: client.lastName || '',
          email: client.email || '',
          phone: client.phone || '',
          address: client.address || '',
          city: client.city || '',
          postalCode: client.postalCode || '',
          country: client.country || '',
          company: client.company || '',
          type: client.type || 'PARTICULIER',
          active: client.active !== false,
        }
      : EMPTY_CLIENT_FORM;

    setEditingClient(client || null);
    setFormData(values);
    setInitialForm(values);
    setTouched({});
    setSubmitAttempted(false);
    setServerErrors({});
    setSelectedClient(null);
    setShowModal(true);
  };

  const handleEdit = (client) => openForm(client);

  // Le premier champ prend le focus à l'ouverture : la saisie démarre au clavier sans détour
  // par la souris. Le délai laisse l'animation d'ouverture de la modale se poser.
  useEffect(() => {
    if (!showModal) return undefined;
    const timer = setTimeout(() => focusField('firstName'), 150);
    return () => clearTimeout(timer);
  }, [showModal]);

  /**
   * Copie une valeur de la fiche dans le presse-papiers.
   * L'API n'est disponible qu'en contexte sécurisé (https / localhost) : l'échec est signalé
   * plutôt que silencieux, sans quoi l'utilisateur croirait avoir copié.
   */
  const copyValue = async (value, message) => {
    try {
      await navigator.clipboard.writeText(value);
      toast.success(message || t('clients.copied'));
    } catch (error) {
      console.error('Clipboard error:', error);
      toast.error(t('clients.copyError'));
    }
  };

  /** Coordonnées complètes en un bloc, prêtes à coller dans un e-mail ou un carnet d'adresses. */
  const copyContactCard = (client) => {
    const lines = [fullName(client), client.company, client.email, client.phone, ...addressLines(client)];
    copyValue(lines.filter(Boolean).join('\n'), t('clients.contactCopied'));
  };

  const selectedAddress = selectedClient ? addressLines(selectedClient) : [];

  // ---- Activité commerciale ----

  const loadActivity = useCallback(async (clientId) => {
    setActivity({ loading: true, error: false, orders: [] });
    try {
      const { data } = await api.get(`/orders/client/${clientId}`);
      return { loading: false, error: false, orders: data };
    } catch (error) {
      console.error('Error fetching client orders:', error);
      return { loading: false, error: true, orders: [] };
    }
  }, []);

  useEffect(() => {
    const clientId = selectedClient?.id;
    if (!clientId) return undefined;

    // Passer d'une fiche à l'autre plus vite que le réseau ne répond ferait afficher
    // l'activité du client précédent sous le nom du nouveau.
    let abandoned = false;
    loadActivity(clientId).then((result) => {
      if (!abandoned) setActivity(result);
    });
    return () => { abandoned = true; };
  }, [selectedClient?.id, loadActivity]);

  const activityStats = useMemo(() => {
    // Une commande annulée n'a rien facturé : la compter fausserait le chiffre d'affaires
    // et le panier moyen. Elle reste dans le décompte total, signalée à part.
    const billable = activity.orders.filter((order) => order.status !== 'CANCELED');
    const revenue = billable.reduce(
      (sum, order) => sum + (Number(order.finalAmount ?? order.totalAmount) || 0),
      0,
    );
    const byRecency = [...activity.orders]
      .sort((a, b) => (new Date(b.createdAt || 0) - new Date(a.createdAt || 0)) || (b.id - a.id));

    return {
      count: activity.orders.length,
      canceled: activity.orders.length - billable.length,
      revenue,
      average: billable.length > 0 ? revenue / billable.length : 0,
      recent: byRecency.slice(0, ACTIVITY_ORDERS_SHOWN),
    };
  }, [activity.orders]);

  // La page Commandes sait ouvrir une commande précise via `?orderId=` : la fiche s'appuie
  // dessus plutôt que de dupliquer un écran de détail de commande.
  const openOrder = (orderId) => navigate(`/orders?orderId=${orderId}`);

  /** Corps de la section d'activité : chargement, échec, aucune commande, ou les chiffres. */
  const renderActivity = () => {
    if (activity.loading) {
      return (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3" aria-busy="true">
          {[0, 1, 2].map((slot) => (
            <div key={slot} className="h-[4.75rem] animate-pulse rounded-xl bg-gray-100 dark:bg-gray-700/50" />
          ))}
        </div>
      );
    }

    if (activity.error) {
      return (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-dashed border-gray-300 px-4 py-3 dark:border-gray-600">
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('clients.activityError')}</p>
          <Button
            variant="secondary"
            size="sm"
            icon={RefreshCw}
            onClick={async () => setActivity(await loadActivity(selectedClient.id))}
          >
            {t('clients.activityRetry')}
          </Button>
        </div>
      );
    }

    if (activityStats.count === 0) {
      return (
        <div className="rounded-xl border border-dashed border-gray-300 px-4 py-3 dark:border-gray-600">
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('clients.activityEmpty')}</p>
        </div>
      );
    }

    return (
      <>
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <ActivityTile label={t('clients.activityOrders')} value={activityStats.count} />
          <ActivityTile label={t('clients.activityRevenue')} value={formatCurrency(activityStats.revenue)} />
          <ActivityTile label={t('clients.activityAverage')} value={formatCurrency(activityStats.average)} />
        </dl>

        <ul className="divide-y divide-gray-100 rounded-xl border border-gray-200 dark:divide-gray-700/60 dark:border-gray-700">
          {activityStats.recent.map((order) => (
            <li key={order.id}>
              <button
                type="button"
                onClick={() => openOrder(order.id)}
                className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/40"
                aria-label={`${t('clients.openOrder')} — ${order.orderNumber}`}
              >
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                    {order.orderNumber}
                  </span>
                  <span className="block text-xs text-gray-500 dark:text-gray-400">
                    {formatDate(order.createdAt)}
                  </span>
                </span>
                <span className="flex flex-shrink-0 items-center gap-3">
                  <span className="text-sm font-semibold tabular-nums text-gray-900 dark:text-gray-100">
                    {formatCurrency(order.finalAmount ?? order.totalAmount)}
                  </span>
                  <OrderStatusBadge order={order} />
                </span>
              </button>
            </li>
          ))}
        </ul>

        {activityStats.count > activityStats.recent.length && (
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {t('clients.activityMore', { count: activityStats.count - activityStats.recent.length })}
          </p>
        )}
      </>
    );
  };

  const confirmDelete = async () => {
    if (!clientToDelete) return;
    const toastId = 'client-delete';
    toast.loading(t('clients.deleting'), { id: toastId });

    try {
      await clientService.deleteClient(clientToDelete.id);
      toast.success(t('clients.deleteSuccess'), { id: toastId });
      // La suppression peut être lancée depuis la fiche elle-même : la laisser ouverte
      // afficherait un client qui n'existe plus.
      if (selectedClient?.id === clientToDelete.id) setSelectedClient(null);
      await fetchClients();
    } catch (error) {
      console.error('Error deleting client:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string' ? raw : (raw?.message || raw?.error || t('clients.deleteError'));
      toast.error(`${t('common.errorPrefix')}${message}`, { id: toastId, duration: 6000 });
    } finally {
      setClientToDelete(null);
    }
  };

  const handleExport = async () => {
    const toastId = 'client-export';
    toast.loading(t('clients.exporting'), { id: toastId });
    try {
      const response = await clientService.exportClients();
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `clients_${new Date().toISOString().split('T')[0]}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      toast.success(t('clients.exportSuccess'), { id: toastId });
    } catch (error) {
      console.error('Error exporting clients:', error);
      toast.error(t('clients.exportError'), { id: toastId });
    }
  };

  const closeForm = () => {
    setShowModal(false);
    setShowDiscardConfirm(false);
    setEditingClient(null);
    setFormData(EMPTY_CLIENT_FORM);
    setInitialForm(EMPTY_CLIENT_FORM);
    setTouched({});
    setSubmitAttempted(false);
    setServerErrors({});
  };

  /**
   * Fermeture demandée par l'utilisateur (bouton Annuler, croix, clic sur le fond).
   * Une saisie en cours n'est jamais jetée sans confirmation : le fond de la modale se ferme
   * au moindre clic à côté, et perdre un formulaire rempli à cette occasion est un incident réel.
   */
  const requestCloseForm = () => {
    if (isDirty) {
      setShowDiscardConfirm(true);
      return;
    }
    closeForm();
  };

  const stats = useMemo(() => ({
    total: clients.length,
    active: clients.filter((c) => c.active).length,
    individuals: clients.filter((c) => c.type === 'PARTICULIER').length,
    companies: clients.filter((c) => c.type === 'ENTREPRISE').length,
  }), [clients]);

  // Listes déduites des clients eux-mêmes : n'afficher que les valeurs réellement présentes
  // évite les critères qui ne rendent aucun résultat.
  const cityOptions = useMemo(() => {
    const set = new Set(clients.map((c) => c.city).filter(Boolean));
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [clients]);

  const countryOptions = useMemo(() => {
    const set = new Set(clients.map((c) => c.country).filter(Boolean));
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [clients]);

  const advancedFields = useMemo(() => [
    {
      key: 'type',
      label: t('clients.typeLabel'),
      type: 'select',
      options: [
        { value: 'ALL', label: t('clients.filterAll') },
        { value: 'PARTICULIER', label: t('clients.typeIndividual') },
        { value: 'ENTREPRISE', label: t('clients.typeBusiness') },
      ],
    },
    {
      key: 'status',
      label: t('clients.columnStatus'),
      type: 'select',
      options: [
        { value: 'ALL', label: t('clients.filterAll') },
        { value: 'ACTIVE', label: t('clients.active') },
        { value: 'INACTIVE', label: t('clients.inactive') },
      ],
    },
    {
      key: 'city',
      label: t('clients.city'),
      type: 'select',
      options: [{ value: '', label: t('clients.filterAll') }, ...cityOptions.map((c) => ({ value: c, label: c }))],
    },
    {
      key: 'country',
      label: t('clients.country'),
      type: 'select',
      options: [{ value: '', label: t('clients.filterAll') }, ...countryOptions.map((c) => ({ value: c, label: c }))],
    },
    { key: 'company', label: t('clients.companyLabel'), type: 'text', placeholder: t('clients.companyPlaceholder') },
    {
      key: 'contact',
      label: t('clients.sectionContact'),
      type: 'select',
      options: [
        { value: 'ALL', label: t('clients.filterAll') },
        { value: 'WITH_EMAIL', label: t('clients.withEmail') },
        { value: 'WITHOUT_EMAIL', label: t('clients.withoutEmail') },
      ],
    },
    { key: 'createdFrom', label: t('clients.createdFromLabel'), type: 'date' },
    { key: 'createdTo', label: t('clients.createdToLabel'), type: 'date' },
  ], [t, cityOptions, countryOptions]);

  const filteredClients = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return clients.filter((client) => {
      if (advanced.type !== 'ALL' && client.type !== advanced.type) return false;
      if (advanced.status === 'ACTIVE' && !client.active) return false;
      if (advanced.status === 'INACTIVE' && client.active) return false;

      if (advanced.city && client.city !== advanced.city) return false;
      if (advanced.country && client.country !== advanced.country) return false;
      if (advanced.company
        && !(client.company || '').toLowerCase().includes(advanced.company.trim().toLowerCase())) {
        return false;
      }
      if (advanced.contact === 'WITH_EMAIL' && !client.email) return false;
      if (advanced.contact === 'WITHOUT_EMAIL' && client.email) return false;

      // Bornes inclusives, comparées sur la partie `yyyy-MM-dd` de l'horodatage : comparer des
      // dates entières exclurait les clients créés le jour de fin passé minuit.
      if (advanced.createdFrom || advanced.createdTo) {
        const day = (client.createdAt || '').slice(0, 10);
        if (!day) return false;
        if (advanced.createdFrom && day < advanced.createdFrom) return false;
        if (advanced.createdTo && day > advanced.createdTo) return false;
      }

      if (!term) return true;
      return [fullName(client), client.company, client.email, client.phone, client.city]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(term);
    });
  }, [clients, searchTerm, advanced]);

  const sortedClients = useMemo(() => {
    const direction = sortConfig.direction === 'asc' ? 1 : -1;
    const valueOf = (client) => {
      switch (sortConfig.key) {
        case 'name': return fullName(client).toLowerCase();
        case 'location': return (client.city || '').toLowerCase();
        case 'type': return client.type || '';
        case 'status': return client.active ? 1 : 0;
        case 'createdAt': return new Date(client.createdAt || 0).getTime();
        default: return client.id;
      }
    };
    return [...filteredClients].sort((a, b) => {
      const left = valueOf(a);
      const right = valueOf(b);
      if (left < right) return -direction;
      if (left > right) return direction;
      // Départage stable : le plus récemment créé d'abord.
      return b.id - a.id;
    });
  }, [filteredClients, sortConfig]);

  // Suggestions d'autocomplétion classées par pertinence (nom complet et société
  // prioritaires sur e-mail / téléphone), distinctes du filtrage du tableau.
  const clientSuggestions = rankSuggestions(
    clients,
    searchTerm,
    (c) => [fullName(c), c.company, c.email, c.phone],
    8
  );

  const hasAdvancedFilters = Object.keys(EMPTY_ADVANCED)
    .some((key) => advanced[key] !== EMPTY_ADVANCED[key]);
  const hasActiveFilters = searchTerm.trim() !== '' || hasAdvancedFilters;
  // Un filtre actif force la liste complète : filtrer pour ne voir qu'une ligne n'aurait aucun sens.
  const showFullList = hasActiveFilters || viewMode === 'all';

  const totalPages = Math.ceil(sortedClients.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const paginatedClients = sortedClients.slice(startIndex, startIndex + itemsPerPage);

  // Les derniers clients ajoutés, du plus récent au plus ancien.
  // À défaut de date de création exploitable, on retombe sur l'id le plus élevé.
  const recentClients = useMemo(() => (
    [...clients]
      .sort((a, b) => (new Date(b.createdAt || 0) - new Date(a.createdAt || 0)) || (b.id - a.id))
      .slice(0, RECENT_COUNT)
  ), [clients]);

  const displayedClients = showFullList ? paginatedClients : recentClients;

  // Toute modification du périmètre ramène à la première page : rester en page 4 d'un
  // résultat qui n'en compte plus que 2 afficherait un tableau vide à tort.
  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, advanced, sortConfig, itemsPerPage, viewMode]);

  const handleSort = (key) => {
    setSortConfig((prev) => {
      if (prev.key === key) {
        return { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' };
      }
      // Une date s'ouvre du plus récent au plus ancien, un texte de A à Z.
      return { key, direction: key === 'createdAt' ? 'desc' : 'asc' };
    });
  };

  const handleViewModeChange = (mode) => {
    setViewMode(mode);
    localStorage.setItem(VIEW_MODE_KEY, mode);
  };

  const resetFilters = () => {
    setSearchTerm('');
    setAdvanced(EMPTY_ADVANCED);
  };

  const handleAdvancedChange = (field, value) => {
    setAdvanced((prev) => ({ ...prev, [field]: value }));
  };

  const typeBadge = (client) => (
    <span className={client.type === 'ENTREPRISE' ? 'badge-accent' : 'badge-info'}>
      {client.type === 'ENTREPRISE' ? t('clients.typeBusiness') : t('clients.typeIndividual')}
    </span>
  );

  const statusBadge = (client) => (
    <span className={client.active ? 'badge-success' : 'badge-neutral'}>
      {client.active ? t('clients.active') : t('clients.inactive')}
    </span>
  );

  /* Colonnes ordonnées par importance décroissante — identité, moyens de contact, puis contexte.
   * Les colonnes secondaires disparaissent sur écran étroit (`hidden … table-cell`) plutôt que
   * de pousser le tableau dans un défilement horizontal où la colonne d'actions devient
   * inatteignable. */
  const columns = [
    {
      key: 'name',
      label: t('clients.columnName'),
      sortable: true,
      render: (client) => (
        <div className="flex items-center gap-3">
          <ClientAvatar client={client} />
          <div className="min-w-0">
            <div className="font-semibold text-gray-900 dark:text-gray-100 truncate">
              {fullName(client)}
            </div>
            <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
              {client.company || t('clients.typeIndividual')}
            </div>
          </div>
        </div>
      ),
    },
    {
      key: 'contact',
      label: t('clients.columnContact'),
      nowrap: false,
      render: (client) => (
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <Mail className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" aria-hidden="true" />
            {client.email ? (
              <a
                href={`mailto:${client.email}`}
                onClick={(e) => e.stopPropagation()}
                className="text-gray-700 dark:text-gray-300 hover:text-primary-600 dark:hover:text-primary-400 hover:underline truncate"
              >
                {client.email}
              </a>
            ) : (
              <span className="text-gray-400">{t('clients.noEmail')}</span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Phone className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" aria-hidden="true" />
            <a
              href={`tel:${client.phone}`}
              onClick={(e) => e.stopPropagation()}
              className="text-gray-700 dark:text-gray-300 hover:text-primary-600 dark:hover:text-primary-400 hover:underline"
            >
              {client.phone}
            </a>
          </div>
        </div>
      ),
    },
    {
      key: 'location',
      label: t('clients.columnLocation'),
      sortable: true,
      className: 'hidden xl:table-cell',
      render: (client) => (
        client.city || client.country ? (
          <div>
            <div className="text-gray-700 dark:text-gray-300">
              {[client.postalCode, client.city].filter(Boolean).join(' ') || '—'}
            </div>
            <div className="text-xs text-gray-500 dark:text-gray-400">{client.country || '—'}</div>
          </div>
        ) : (
          <span className="text-gray-400 text-xs">{t('clients.noAddress')}</span>
        )
      ),
    },
    {
      key: 'type',
      label: t('clients.type'),
      sortable: true,
      className: 'hidden md:table-cell',
      render: typeBadge,
    },
    {
      key: 'status',
      label: t('clients.columnStatus'),
      sortable: true,
      className: 'hidden sm:table-cell',
      render: statusBadge,
    },
    {
      key: 'createdAt',
      label: t('clients.columnSince'),
      sortable: true,
      className: 'hidden lg:table-cell',
      render: (client) => (
        <span className="text-gray-600 dark:text-gray-400 tabular-nums">{formatDate(client.createdAt)}</span>
      ),
    },
  ];

  const emptyState = hasActiveFilters ? (
    <div className="flex flex-col items-center gap-3">
      <Users className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('clients.noResultsTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('clients.noResultsHint')}</p>
      </div>
      <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
        {t('clients.resetFilters')}
      </Button>
    </div>
  ) : (
    <div className="flex flex-col items-center gap-3">
      <Users className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('clients.emptyTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('clients.emptyHint')}</p>
      </div>
      <Button variant="primary" size="sm" icon={Plus} onClick={() => openForm(null)}>
        {t('clients.addClient')}
      </Button>
    </div>
  );

  return (
    <div className="space-y-6">
      {/* ---- En-tête ---- */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <Users aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('clients.title')}</h1>
            <p className="page-subtitle">{t('clients.subtitle')}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" icon={RefreshCw} onClick={fetchClients} loading={loading}>
            {t('common.refresh')}
          </Button>
          {isAdmin && (
            <Button variant="secondary" icon={Download} onClick={handleExport}>
              {t('common.export')}
            </Button>
          )}
          <Button variant="primary" icon={Plus} onClick={() => openForm(null)}>
            {t('clients.addClient')}
          </Button>
        </div>
      </div>

      {/* ---- Indicateurs ----
       * Les quatre tuiles partagent le composant StatCard : même échelle typographique, même
       * squelette de chargement et même comportement en tuile étroite que les autres écrans. */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 sm:gap-6">
        <StatCard
          title={t('clients.totalCount')}
          value={stats.total}
          subtitle={t('clients.portfolioHint')}
          icon={Users}
          tone="info"
          loading={loading}
        />
        <StatCard
          title={t('clients.activeCount')}
          value={stats.active}
          subtitle={t('clients.shareOfTotal', { percent: formatPercent(safeRatio(stats.active, stats.total)) })}
          icon={UserCheck}
          tone="success"
          loading={loading}
        />
        <StatCard
          title={t('clients.individualCount')}
          value={stats.individuals}
          subtitle={t('clients.shareOfTotal', { percent: formatPercent(safeRatio(stats.individuals, stats.total)) })}
          icon={User}
          tone="accent"
          loading={loading}
        />
        <StatCard
          title={t('clients.businessCount')}
          value={stats.companies}
          subtitle={t('clients.shareOfTotal', { percent: formatPercent(safeRatio(stats.companies, stats.total)) })}
          icon={Building2}
          tone="warning"
          loading={loading}
        />
      </div>

      {/* ---- Recherche et filtres ---- */}
      <div className="card space-y-4">
        <AdvancedFilters
          id="clients"
          fields={advancedFields}
          values={advanced}
          defaults={EMPTY_ADVANCED}
          onChange={handleAdvancedChange}
          onReset={resetFilters}
          resettable={hasActiveFilters}
          expanded={filtersExpanded}
          onToggleExpanded={() => setFiltersExpanded((v) => !v)}
          dateRange={{ fromKey: 'createdFrom', toKey: 'createdTo' }}
          search={(
            <SearchBox
              value={searchTerm}
              onChange={setSearchTerm}
              placeholder={t('clients.searchPlaceholder')}
              suggestions={clientSuggestions}
              getKey={(c) => c.id}
              onSelectSuggestion={(c) => setSearchTerm(fullName(c))}
              renderSuggestion={(c) => (
                <span className="flex items-center justify-between gap-2">
                  <span className="flex flex-col min-w-0">
                    <span className="font-medium truncate">{fullName(c)}</span>
                    <span className="text-xs text-gray-400 truncate">{c.email || c.phone || ''}</span>
                  </span>
                  {c.company && <span className="text-xs text-gray-500 shrink-0">{c.company}</span>}
                </span>
              )}
            />
          )}
        />
      </div>

      {/* ---- Répertoire ---- */}
      <div className="card overflow-hidden p-0">
        <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div>
            <h2 className="section-title">{t('clients.directory')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
              {showFullList ? t('clients.directoryHint') : t('clients.recentHint', { n: RECENT_COUNT })}
            </p>
          </div>
          {!hasActiveFilters && clients.length > 0 && (
            <SegmentedFilter
              label={t('clients.displayLabel')}
              value={viewMode}
              onChange={handleViewModeChange}
              options={[
                { value: 'recent', label: t('clients.viewRecent', { n: RECENT_COUNT }) },
                { value: 'all', label: t('clients.viewAll'), count: stats.total },
              ]}
            />
          )}
        </div>

        <Table
          columns={columns}
          data={displayedClients}
          loading={loading}
          emptyState={emptyState}
          sortKey={sortConfig.key}
          sortDirection={sortConfig.direction}
          onSort={handleSort}
          onRowClick={setSelectedClient}
          actions={(client) => (
            <>
              <button
                onClick={(e) => { e.stopPropagation(); setSelectedClient(client); }}
                className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                title={t('clients.viewProfile')}
                aria-label={`${t('clients.viewProfile')} — ${fullName(client)}`}
              >
                <Eye className="w-4 h-4" aria-hidden="true" />
              </button>
              <button
                onClick={(e) => { e.stopPropagation(); handleEdit(client); }}
                className="text-primary-600 hover:text-primary-900 dark:hover:text-primary-300 p-2 hover:bg-primary-50 dark:hover:bg-primary-500/10 rounded-lg transition-colors"
                title={t('common.edit')}
                aria-label={`${t('common.edit')} — ${fullName(client)}`}
              >
                <Edit className="w-4 h-4" aria-hidden="true" />
              </button>
              {isAdmin && (
                <button
                  onClick={(e) => { e.stopPropagation(); setClientToDelete(client); }}
                  className="text-red-600 hover:text-red-900 dark:hover:text-red-300 p-2 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors"
                  title={t('common.delete')}
                  aria-label={`${t('common.delete')} — ${fullName(client)}`}
                >
                  <Trash2 className="w-4 h-4" aria-hidden="true" />
                </button>
              )}
            </>
          )}
        />

        {/* Pagination : seulement quand la liste complète est affichée (la vue « dernier ajout »
            ne montre qu'une ligne, un pied de pagination y serait trompeur). */}
        {showFullList && !loading && sortedClients.length > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={sortedClients.length}
            itemsPerPage={itemsPerPage}
            onPageChange={setCurrentPage}
            onItemsPerPageChange={setItemsPerPage}
          />
        )}
      </div>

      {/* ---- Fiche client (lecture seule) ----
       * Même largeur que le formulaire : passer de l'un à l'autre ne fait plus sauter la fenêtre,
       * et les coordonnées tiennent à côté de l'adresse au lieu de se suivre en un seul fil. */}
      <Modal
        isOpen={!!selectedClient}
        onClose={() => setSelectedClient(null)}
        title={t('clients.clientDetails')}
        size="lg"
      >
        {selectedClient && (
          <div className="space-y-6">
            {/* En-tête pleine largeur : identité, qualification et actions de contact au même
                endroit. Ce sont les seules choses que l'on vient chercher en ouvrant une fiche —
                elles ne doivent pas se disputer la place avec le reste. */}
            <header className="-mx-6 -mt-6 border-b border-gray-200 bg-gray-50 px-6 py-6 dark:border-gray-700 dark:bg-gray-900/40">
              {/* Sur large écran, identité à gauche et actions de contact à droite : la largeur
                  gagnée sert à mettre les deux à hauteur de regard plutôt qu'à les empiler. */}
              <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between lg:gap-8">
                <div className="flex min-w-0 items-start gap-4">
                  <ClientAvatar client={selectedClient} size="lg" />
                  <div className="min-w-0">
                    <h3 className="truncate text-xl font-bold tracking-tight text-gray-900 dark:text-gray-100">
                      {fullName(selectedClient)}
                    </h3>
                    {selectedClient.company && (
                      <p className="truncate text-sm text-gray-500 dark:text-gray-400">{selectedClient.company}</p>
                    )}
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                      {typeBadge(selectedClient)}
                      {statusBadge(selectedClient)}
                      <span className="inline-flex items-center gap-1 text-xs tabular-nums text-gray-400 dark:text-gray-500">
                        <Hash className="h-3 w-3" aria-hidden="true" />
                        {selectedClient.id}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Actions de contact : joindre le client est le geste le plus fréquent après
                    la consultation, il ne doit pas demander de recopier une adresse à la main. */}
                <div className="flex flex-wrap gap-2 lg:flex-shrink-0 lg:justify-end">
                  {selectedClient.email && (
                    <a href={`mailto:${selectedClient.email}`} className="quick-action">
                      <Mail className="h-4 w-4" aria-hidden="true" />
                      {t('clients.quickEmail')}
                    </a>
                  )}
                  {selectedClient.phone && (
                    <a href={`tel:${selectedClient.phone}`} className="quick-action">
                      <Phone className="h-4 w-4" aria-hidden="true" />
                      {t('clients.quickCall')}
                    </a>
                  )}
                  <button type="button" onClick={() => copyContactCard(selectedClient)} className="quick-action">
                    <Copy className="h-4 w-4" aria-hidden="true" />
                    {t('clients.copyContact')}
                  </button>
                </div>
              </div>
            </header>

            {/* `items-start` : les deux blocs n'ont pas la même hauteur et n'ont aucune raison
                de s'étirer l'un sur l'autre. */}
            <div className="grid gap-6 lg:grid-cols-2 lg:items-start">
              <section className="space-y-3">
                <h4 className="subsection-title flex items-center gap-2">
                  <Mail className="h-4 w-4 text-gray-400" aria-hidden="true" />
                  {t('clients.sectionContact')}
                </h4>
                <dl className="divide-y divide-gray-100 rounded-xl border border-gray-200 dark:divide-gray-700/60 dark:border-gray-700">
                  <InfoRow
                    icon={Mail}
                    label={t('clients.email')}
                    value={selectedClient.email}
                    href={selectedClient.email ? `mailto:${selectedClient.email}` : undefined}
                    className="px-4 py-3"
                    action={selectedClient.email
                      ? <CopyButton value={selectedClient.email} label={t('clients.copyEmail')} onCopy={copyValue} />
                      : <CompleteButton label={t('clients.completeProfile')} onClick={() => handleEdit(selectedClient)} />}
                  />
                  <InfoRow
                    icon={Phone}
                    label={t('clients.phone')}
                    value={selectedClient.phone}
                    href={selectedClient.phone ? `tel:${selectedClient.phone}` : undefined}
                    className="px-4 py-3"
                    action={selectedClient.phone
                      ? <CopyButton value={selectedClient.phone} label={t('clients.copyPhone')} onCopy={copyValue} />
                      : null}
                  />
                </dl>
              </section>

              <section className="space-y-3">
                <h4 className="subsection-title flex items-center gap-2">
                  <MapPin className="h-4 w-4 text-gray-400" aria-hidden="true" />
                  {t('clients.sectionAddress')}
                </h4>
                {/* Une adresse postale se lit en bloc, pas champ par champ : trois lignes
                    étiquetées séparément obligeaient à la recomposer mentalement pour l'utiliser. */}
                {selectedAddress.length > 0 ? (
                  <dl className="rounded-xl border border-gray-200 dark:border-gray-700">
                    <InfoRow
                      icon={MapPin}
                      label={t('clients.postalAddressLabel')}
                      value={(
                        <span className="block leading-relaxed">
                          {selectedAddress.map((line, index) => (
                            <span key={`${index}-${line}`} className="block">{line}</span>
                          ))}
                        </span>
                      )}
                      className="px-4 py-3"
                      action={(
                        <CopyButton
                          value={selectedAddress.join('\n')}
                          label={t('clients.copyAddress')}
                          onCopy={copyValue}
                        />
                      )}
                    />
                  </dl>
                ) : (
                  <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-dashed border-gray-300 px-4 py-3 dark:border-gray-600">
                    <p className="text-sm text-gray-500 dark:text-gray-400">{t('clients.noAddress')}</p>
                    <CompleteButton label={t('clients.completeProfile')} onClick={() => handleEdit(selectedClient)} />
                  </div>
                )}
              </section>
            </div>

            {/* Activité commerciale : ce que le client pèse. Placée après les coordonnées, qui
                restent le motif d'ouverture le plus fréquent, mais avant la traçabilité. */}
            <section className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h4 className="subsection-title flex items-center gap-2">
                  <ShoppingCart className="h-4 w-4 text-gray-400" aria-hidden="true" />
                  {t('clients.sectionActivity')}
                </h4>
                {activityStats.canceled > 0 && (
                  <span className="text-xs text-gray-400 dark:text-gray-500">
                    {t('clients.activityCanceled', { count: activityStats.canceled })}
                  </span>
                )}
              </div>
              {renderActivity()}
            </section>

            {/* Traçabilité : utile pour arbitrer un doute, jamais pour travailler. Réduite à une
                ligne de bas de fiche plutôt qu'à une section de même rang que les coordonnées. */}
            <p className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-gray-200 pt-4 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">
              <span className="inline-flex items-center gap-1.5">
                <CalendarClock className="h-3.5 w-3.5" aria-hidden="true" />
                {t('clients.createdAtLabel')} {formatDateTime(selectedClient.createdAt)}
              </span>
              <span>{t('clients.updatedAtLabel')} {formatDateTime(selectedClient.updatedAt)}</span>
            </p>

            <div className="sticky bottom-0 -mx-6 -mb-6 flex flex-wrap items-center gap-3 border-t border-gray-200 bg-white/95 px-6 py-4 backdrop-blur dark:border-gray-700 dark:bg-gray-800/95">
              {isAdmin && (
                <Button variant="danger" icon={Trash2} onClick={() => setClientToDelete(selectedClient)}>
                  {t('common.delete')}
                </Button>
              )}
              <div className="ml-auto flex items-center gap-3">
                <Button variant="secondary" onClick={() => setSelectedClient(null)}>
                  {t('common.close')}
                </Button>
                <Button variant="primary" icon={Edit} onClick={() => handleEdit(selectedClient)}>
                  {t('common.edit')}
                </Button>
              </div>
            </div>
          </div>
        )}
      </Modal>

      {/* ---- Formulaire ---- */}
      <Modal
        isOpen={showModal}
        onClose={requestCloseForm}
        title={editingClient ? t('clients.editClient') : t('clients.newClient')}
        size="lg"
      >
        {/* `noValidate` : la validation est celle du formulaire, pas celle du navigateur, dont les
            bulles natives s'affichent hors de la charte et dans la langue du navigateur. */}
        <form onSubmit={handleSubmit} noValidate>
          <ClientFormFields
            values={formData}
            errors={visibleErrors}
            onChange={handleInputChange}
            onBlur={handleBlur}
            onFocusField={focusField}
            showErrorSummary={submitAttempted}
          />

          {/* Barre d'actions collée au bas de la modale : sur un écran court, le formulaire
              défile mais l'enregistrement reste sous la main, sans avoir à chercher le bas. */}
          <div className="sticky bottom-0 -mx-6 -mb-6 mt-6 flex flex-col-reverse gap-3 border-t border-gray-200 bg-white/95 px-6 py-4 backdrop-blur sm:flex-row sm:items-center sm:justify-between dark:border-gray-700 dark:bg-gray-800/95">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              {editingClient && !isDirty ? t('clients.noChanges') : t('clients.requiredHint')}
            </p>
            <div className="flex items-center justify-end gap-3">
              <Button variant="secondary" onClick={requestCloseForm} type="button">
                {t('common.cancel')}
              </Button>
              <Button variant="primary" type="submit" loading={saving} disabled={!canSubmit}>
                {editingClient ? t('common.saveChanges') : t('clients.createButton')}
              </Button>
            </div>
          </div>
        </form>
      </Modal>

      {/* ---- Confirmations ---- */}
      <ConfirmModal
        isOpen={showDiscardConfirm}
        onClose={() => setShowDiscardConfirm(false)}
        onConfirm={closeForm}
        title={t('clients.discardTitle')}
        message={t('clients.discardMessage')}
        type="warning"
        confirmLabel={t('clients.discardConfirm')}
        cancelLabel={t('clients.discardCancel')}
      />

      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmSubmit}
        title={editingClient ? t('clients.confirmEdit') : t('clients.confirmCreate')}
        message={editingClient
          ? t('clients.confirmEditMessage', { name: fullName(formData) })
          : t('clients.confirmCreateMessage', { name: fullName(formData) })}
        type="info"
      />

      <ConfirmModal
        isOpen={!!clientToDelete}
        onClose={() => setClientToDelete(null)}
        onConfirm={confirmDelete}
        title={t('clients.confirmDeleteTitle')}
        message={t('clients.confirmDeleteNamed', { name: fullName(clientToDelete) })}
        type="danger"
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
      />
    </div>
  );
};

export default Clients;
