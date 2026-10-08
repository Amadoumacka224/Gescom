import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Plus, Truck, MapPin, Calendar, Edit, Trash2, Clock, CheckCircle, User, Phone, Hash,
  PackageCheck, Copy, X, RefreshCw, Eye, AlertTriangle, ShoppingCart, Globe,
  StickyNote,
} from 'lucide-react';
import { toast } from 'react-hot-toast';
import api from '../services/api';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Pagination from '../components/Pagination';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import Button from '../components/Button';
import Table from '../components/Table';
import SearchBox from '../components/SearchBox';
import StatCard from '../components/StatCard';
import SegmentedFilter from '../components/SegmentedFilter';
import InfoRow from '../components/InfoRow';
import FormSection from '../components/FormSection';
import AdvancedFilters from '../components/AdvancedFilters';
import { rankSuggestions } from '../utils/searchSuggestions';
import { formatPercent, safeRatio } from '../utils/format';
import { DELIVERY_STATUS_TONE, badgeClass } from '../constants/statusBadges';

// Une livraison n'a que deux états : planifiée puis livrée. DELIVERED est final (reprendre
// une marchandise livrée, c'est un retour, ça ne passe pas par ici).
const TERMINAL_STATUSES = ['DELIVERED'];

/** Garde le mode d'affichage d'une visite à l'autre, comme sur les autres écrans. */
const VIEW_MODE_KEY = 'deliveriesViewMode';

/** Nombre de livraisons affichées dans l'aperçu (les dernières créées). */
const RECENT_COUNT = 5;

/**
 * Colonnes triables, avec le champ de l'entité utilisé pour le `sort` de Spring Data.
 * `order` trie sur le nom du client, comme avant.
 */
const SORT_FIELDS = {
  deliveryNumber: 'deliveryNumber',
  order: 'order.client.lastName',
  destination: 'deliveryCity',
  status: 'status',
  scheduledDate: 'scheduledDate',
};

/**
 * Filtres avec leurs valeurs vides. Sert pour l'état initial, pour le bouton « Réinitialiser »
 * et pour savoir quels filtres sont actifs.
 * Le statut est ici comme les autres : avant il était dans des boutons au-dessus de la liste,
 * ce qui faisait doublon avec les cartes de stats.
 */
const EMPTY_ADVANCED = {
  status: 'ALL',
  clientId: '',
  city: '',
  country: '',
  contact: '',
  scheduledFrom: '',
  scheduledTo: '',
};

const EMPTY_FORM = {
  orderId: '',
  deliveryAddress: '',
  deliveryCity: '',
  deliveryPostalCode: '',
  deliveryCountry: 'Belgique',
  scheduledDate: '',
  contactName: '',
  contactPhone: '',
  notes: '',
  status: 'PENDING',
};

const FORM_KEYS = Object.keys(EMPTY_FORM);

/**
 * Longueurs max, les mêmes que les `@Size` de `DeliveryCreateRequest` /
 * `DeliveryUpdateRequest`. Utilisées pour `maxLength` et pour la validation.
 */
const MAX_LENGTHS = {
  deliveryAddress: 255,
  deliveryCity: 100,
  deliveryPostalCode: 20,
  deliveryCountry: 100,
  contactName: 100,
  contactPhone: 20,
  notes: 500,
};

/** Même regex que dans les autres formulaires. */
const PHONE_PATTERN = /^[0-9+\- ]{6,20}$/;

/** Ordre des champs à l'écran : sert à savoir lequel prend le focus s'il y a plusieurs erreurs. */
const FIELD_ORDER = [
  'orderId', 'deliveryAddress', 'deliveryPostalCode', 'deliveryCity', 'deliveryCountry',
  'scheduledDate', 'contactName', 'contactPhone', 'notes',
];

/**
 * Valide tout le formulaire et renvoie les erreurs par champ.
 *
 * Ville, code postal et pays sont facultatifs dans l'API mais obligatoires ici : sans ville,
 * on ne peut pas livrer. L'écran les demandait déjà, maintenant c'est notre validation qui
 * le gère au lieu de celle du navigateur.
 */
const validateDelivery = (data, t, { isEdit, today }) => {
  const errors = {};
  const trimmed = (field) => (data[field] || '').trim();

  if (!isEdit && !data.orderId) errors.orderId = t('deliveries.errorOrderRequired');
  if (!trimmed('deliveryAddress')) errors.deliveryAddress = t('deliveries.errorAddressRequired');
  if (!trimmed('deliveryCity')) errors.deliveryCity = t('deliveries.errorCityRequired');
  if (!trimmed('deliveryPostalCode')) errors.deliveryPostalCode = t('deliveries.errorPostalCodeRequired');
  if (!trimmed('deliveryCountry')) errors.deliveryCountry = t('deliveries.errorCountryRequired');
  if (!trimmed('contactName')) errors.contactName = t('deliveries.errorContactNameRequired');

  if (!trimmed('contactPhone')) errors.contactPhone = t('deliveries.errorContactPhoneRequired');
  else if (!PHONE_PATTERN.test(trimmed('contactPhone'))) {
    errors.contactPhone = t('deliveries.errorContactPhoneFormat');
  }

  if (!data.scheduledDate) errors.scheduledDate = t('deliveries.errorScheduledDateRequired');
  // Une date passée n'est acceptée que pour une livraison déjà enregistrée, qui peut
  // normalement avoir une date avant aujourd'hui.
  else if (!isEdit && data.scheduledDate < today) {
    errors.scheduledDate = t('deliveries.errorScheduledDatePast');
  }

  Object.entries(MAX_LENGTHS).forEach(([field, max]) => {
    if (!errors[field] && trimmed(field).length > max) {
      errors[field] = t('deliveries.errorMaxLength', { max });
    }
  });

  return errors;
};

// Le backend (LocalDateTime) renvoie "2025-12-01T10:00:00" mais l'input type="date"
// veut "YYYY-MM-DD". Ces deux fonctions font la conversion dans les deux sens.
const toDateInputValue = (isoDateTime) => {
  if (!isoDateTime) return '';
  return String(isoDateTime).split('T')[0];
};

const toLocalDateTime = (dateInputValue) => {
  if (!dateInputValue) return null;
  // Pas de 'Z' à la fin : LocalDateTime côté backend refuse les fuseaux horaires.
  return `${dateInputValue}T00:00:00`;
};

/** Juste le jour d'une date du backend, comparable en texte (« 2026-07-28 »). */
const dayOf = (isoDateTime) => (isoDateTime ? String(isoDateTime).slice(0, 10) : '');

const formatDay = (isoDateTime) => {
  const day = dayOf(isoDateTime);
  return day ? new Date(`${day}T00:00:00`).toLocaleDateString('fr-FR') : '—';
};

const formatDateTime = (iso) =>
  iso
    ? new Date(iso).toLocaleString('fr-FR', {
        day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
      })
    : '—';

const clientNameOf = (delivery) => {
  const client = delivery?.order?.client;
  if (!client) return null;
  return `${client.firstName || ''} ${client.lastName || ''}`.trim();
};

/** Une livraison est en retard si elle n'est pas faite et que la date prévue est passée. */
const isLate = (delivery, today) =>
  delivery.status === 'PENDING' && !!dayOf(delivery.scheduledDate) && dayOf(delivery.scheduledDate) < today;

const Deliveries = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();

  const [deliveries, setDeliveries] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [showModal, setShowModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [editingDelivery, setEditingDelivery] = useState(null);
  const [selectedDelivery, setSelectedDelivery] = useState(null);
  const [deliveryToDelete, setDeliveryToDelete] = useState(null);
  const [deliveryToMark, setDeliveryToMark] = useState(null);

  const [searchTerm, setSearchTerm] = useState('');
  const [advanced, setAdvanced] = useState(EMPTY_ADVANCED);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const [sortConfig, setSortConfig] = useState({ key: 'scheduledDate', direction: 'asc' });
  const [currentPage, setCurrentPage] = useState(1);
  // Nombre total de résultats, renvoyé par le serveur.
  const [pageMeta, setPageMeta] = useState({ totalElements: 0, totalPages: 1 });
  // Compteurs en haut de page, calculés en base. `late` fait partie de `pending`, il ne
  // s'ajoute pas : une livraison faite en retard n'est plus en retard, elle est faite.
  const [summary, setSummary] = useState({ total: 0, pending: 0, delivered: 0, late: 0 });
  // Options des filtres, complètes : une ville qui n'apparaît qu'en page 3 doit être
  // proposée dès la page 1.
  const [filterOptions, setFilterOptions] = useState({ clients: [], cities: [], countries: [] });
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [viewMode, setViewMode] = useState(() => localStorage.getItem(VIEW_MODE_KEY) || 'recent');
  const [formData, setFormData] = useState(EMPTY_FORM);
  // Valeurs au moment de l'ouverture, pour savoir si le formulaire a été modifié
  // (bouton enregistrer désactivé sinon, confirmation à la fermeture).
  const [initialForm, setInitialForm] = useState(EMPTY_FORM);
  const [touched, setTouched] = useState({});
  const [submitAttempted, setSubmitAttempted] = useState(false);
  // Erreurs renvoyées par l'API (`fieldErrors` du GlobalExceptionHandler). Gardées à part des
  // erreurs locales, elles disparaissent dès que la valeur du champ change.
  const [serverErrors, setServerErrors] = useState({});
  const [showDiscardConfirm, setShowDiscardConfirm] = useState(false);

  // Date du jour, recalculée à chaque rendu : sert à détecter les retards.
  const today = new Date().toISOString().slice(0, 10);
  const todayISO = today;

  // Données de référence et compteurs : chargés une fois. La page elle-même est rechargée
  // par l'effet plus bas, après `queryParams` dont il dépend.
  useEffect(() => {
    fetchOrders();
    fetchSummary();
    fetchFilterOptions();
  }, []);

  /**
   * Charge une page de livraisons.
   *
   * C'est ici qu'on gère `loading`, et plus dans `refresh` : la page est maintenant rechargée
   * par un effet sur les filtres, qui ne passe pas par `refresh`. Quand `loading` était géré
   * dans `refresh`, l'écran restait bloqué sur le squelette au premier affichage (il démarre
   * à `true` et rien ne le remettait à false).
   */
  const fetchDeliveries = async () => {
    setLoading(true);
    try {
      const { data } = await api.get('/deliveries/search', { params: queryParams });
      setDeliveries(data.content || []);
      setPageMeta({
        totalElements: data.totalElements ?? 0,
        totalPages: Math.max(1, data.totalPages ?? 1),
      });
      return data.content || [];
    } catch (error) {
      console.error('Error fetching deliveries:', error);
      toast.error(t('deliveries.loadError'));
      setDeliveries([]);
      return [];
    } finally {
      setLoading(false);
    }
  };

  /** Compteurs en haut de page : sur toutes les livraisons, pas juste la page. */
  const fetchSummary = async () => {
    try {
      const { data } = await api.get('/deliveries/summary');
      setSummary(data);
    } catch (error) {
      console.error('Error fetching delivery summary:', error);
    }
  };

  /** Clients, villes et pays proposés dans les filtres. */
  const fetchFilterOptions = async () => {
    try {
      const { data } = await api.get('/deliveries/filter-options');
      setFilterOptions(data);
    } catch (error) {
      console.error('Error fetching delivery filter options:', error);
    }
  };


  /**
   * Commandes qu'on peut choisir dans le formulaire : facturées et sans livraison.
   *
   * Avant, on calculait ça ici en enlevant des commandes facturées celles qui avaient déjà une
   * livraison dans la liste chargée. Comme on ne charge plus toutes les livraisons, on ne
   * verrait que la page affichée et on pourrait proposer une commande déjà livrée. C'est donc
   * le serveur qui renvoie la bonne liste.
   */
  const fetchOrders = async () => {
    try {
      const { data } = await api.get('/orders/deliverable');
      setOrders(data);
    } catch (error) {
      console.error('Error fetching deliverable orders:', error);
      setOrders([]);
    }
  };

  /**
   * Rechargement après une modif. On recharge aussi les compteurs et les options : une
   * livraison marquée faite change une carte, et une nouvelle livraison peut ajouter une
   * ville dans le filtre.
   */
  const refresh = async () => {
    await Promise.all([fetchDeliveries(), fetchOrders(), fetchSummary(), fetchFilterOptions()]);
  };

  const buildClientDefaults = (client) => {
    if (!client) return {};
    const fullName = [client.firstName, client.lastName].filter(Boolean).join(' ').trim();
    return {
      contactName: fullName,
      contactPhone: client.phone || '',
      deliveryAddress: client.address || '',
      deliveryCity: client.city || '',
      deliveryPostalCode: client.postalCode || '',
      deliveryCountry: client.country || 'Belgique',
    };
  };

  useEffect(() => {
    // Si on vient de la page Commandes : on ouvre le formulaire avec la commande déjà choisie
    // (INVOICED → livrer). On attend que la liste des commandes livrables soit chargée pour
    // pouvoir remplir les coordonnées du client.
    if (location.state?.createForOrderId && orders.length > 0) {
      const id = location.state.createForOrderId;
      const selectedOrder = orders.find(o => o.id === parseInt(id));
      if (selectedOrder) {
        // Le formulaire s'ouvre vide puis on remplit la commande et les coordonnées du client.
        // `initialForm` reste EMPTY_FORM, donc ce remplissage compte comme une saisie et la
        // fermeture demandera confirmation. C'est voulu.
        const defaults = buildClientDefaults(selectedOrder.client);
        openForm(null);
        setFormData(prev => ({ ...prev, orderId: String(id), ...defaults }));
      }
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [location.state, orders]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    // L'erreur du serveur concernait l'ancienne valeur, on l'enlève dès que la valeur change.
    setServerErrors((prev) => (prev[name] === undefined ? prev : { ...prev, [name]: undefined }));

    if (name === 'orderId') {
      // Remplit l'adresse de livraison avec celle du client de la commande, mais seulement
      // pour les champs encore vides (on n'écrase pas ce que l'utilisateur a tapé).
      const selectedOrder = value ? orders.find(o => o.id === parseInt(value)) : null;
      const defaults = selectedOrder ? buildClientDefaults(selectedOrder.client) : {};
      setFormData(prev => {
        const next = { ...prev, orderId: value };
        Object.entries(defaults).forEach(([key, val]) => {
          if (!prev[key] && val) {
            next[key] = val;
          }
        });
        return next;
      });
      return;
    }

    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleCopyFromClient = () => {
    // En création, on trouve la commande dans `orders`. En modification, elle n'y est plus
    // (`orders` ne contient que les INVOICED), donc on prend la commande liée à la
    // livraison qu'on modifie.
    const selectedOrder = formData.orderId
      ? orders.find(o => o.id === parseInt(formData.orderId))
      : null;
    const client = selectedOrder?.client || editingDelivery?.order?.client;
    if (!client) {
      toast.error(t('deliveries.selectOrderFirst'));
      return;
    }
    setFormData(prev => ({ ...prev, ...buildClientDefaults(client) }));
  };

  // L'erreur s'affiche quand on quitte le champ, ou au premier essai d'enregistrement :
  // l'afficher dès la première lettre serait pénible.
  const handleBlur = (e) => {
    const { name } = e.target;
    setTouched((prev) => (prev[name] ? prev : { ...prev, [name]: true }));
  };

  const formErrors = useMemo(
    () => validateDelivery(formData, t, { isEdit: !!editingDelivery, today: todayISO }),
    [formData, t, editingDelivery, todayISO]
  );

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

  // Noms des champs comme à l'écran : la liste des erreurs doit utiliser les mêmes noms que
  // ceux que voit l'utilisateur, pas ceux du DTO.
  const fieldLabels = useMemo(() => ({
    orderId: t('deliveries.orderLabel'),
    deliveryAddress: t('deliveries.addressLabel'),
    deliveryPostalCode: t('deliveries.postalCodeLabel'),
    deliveryCity: t('deliveries.cityLabel'),
    deliveryCountry: t('deliveries.countryLabel'),
    scheduledDate: t('deliveries.scheduledDateLabel'),
    contactName: t('deliveries.contactNameLabel'),
    contactPhone: t('deliveries.contactPhoneLabel'),
    notes: t('deliveries.notesLabel'),
  }), [t]);

  const invalidFields = FIELD_ORDER.filter((field) => visibleErrors[field]);
  const isDirty = FORM_KEYS.some((key) => formData[key] !== initialForm[key]);
  const canSubmit = !saving && (!editingDelivery || isDirty);

  const focusField = (field) => {
    document.getElementById(field)?.focus();
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setSubmitAttempted(true);

    const remaining = FIELD_ORDER.filter((field) => formErrors[field]);
    if (remaining.length > 0) {
      focusField(remaining[0]);
      return;
    }
    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    const toastId = 'delivery-save';
    setSaving(true);
    toast.loading(t('deliveries.saving'), { id: toastId });

    try {
      const trim = (field) => (formData[field] || '').trim();
      const deliveryData = {
        orderId: parseInt(formData.orderId),
        deliveryAddress: trim('deliveryAddress'),
        deliveryCity: trim('deliveryCity'),
        deliveryPostalCode: trim('deliveryPostalCode'),
        deliveryCountry: trim('deliveryCountry'),
        contactName: trim('contactName'),
        contactPhone: trim('contactPhone'),
        scheduledDate: toLocalDateTime(formData.scheduledDate),
        // On envoie le statut seulement en modification : à la création, `DeliveryService` le
        // met de toute façon à PENDING (le passage à DELIVERED se fait par
        // `markDeliveryAsDelivered`).
        ...(editingDelivery ? { status: formData.status } : {}),
        notes: trim('notes') || null,
      };

      if (editingDelivery) {
        await api.put(`/deliveries/${editingDelivery.id}`, deliveryData);
        toast.success(t('deliveries.updatedSuccess'), { id: toastId });
      } else {
        await api.post('/deliveries', deliveryData);
        toast.success(t('deliveries.createdSuccess'), { id: toastId });
      }

      closeForm();
      refresh();
    } catch (error) {
      console.error('Error saving delivery:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string' ? raw : (raw?.message || raw?.error || t('deliveries.saveError'));

      // L'erreur du serveur est affichée sur le champ concerné plutôt que dans un toast,
      // comme ça on voit directement quoi corriger.
      const fieldErrors = typeof raw === 'object' && raw?.fieldErrors ? { ...raw.fieldErrors } : {};
      const flagged = FIELD_ORDER.filter((field) => fieldErrors[field]);
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

  /** Ouvre le formulaire avec des valeurs données et des erreurs remises à zéro. */
  const openForm = (delivery) => {
    const values = delivery
      ? {
          orderId: delivery.order.id.toString(),
          deliveryAddress: delivery.deliveryAddress || '',
          deliveryCity: delivery.deliveryCity || '',
          deliveryPostalCode: delivery.deliveryPostalCode || '',
          deliveryCountry: delivery.deliveryCountry || 'Belgique',
          scheduledDate: toDateInputValue(delivery.scheduledDate),
          contactName: delivery.contactName || '',
          contactPhone: delivery.contactPhone || '',
          notes: delivery.notes || '',
          status: delivery.status,
        }
      : EMPTY_FORM;

    setEditingDelivery(delivery || null);
    setFormData(values);
    setInitialForm(values);
    setTouched({});
    setSubmitAttempted(false);
    setServerErrors({});
    setSelectedDelivery(null);
    setShowModal(true);
  };

  const handleEdit = (delivery) => openForm(delivery);

  const confirmDelete = async () => {
    if (!deliveryToDelete) return;
    const toastId = 'delivery-delete';
    toast.loading(t('deliveries.deleting'), { id: toastId });

    try {
      await api.delete(`/deliveries/${deliveryToDelete.id}`);
      toast.success(t('deliveries.deleteSuccess'), { id: toastId });
      refresh();
    } catch (error) {
      console.error('Error deleting delivery:', error);
      toast.error(t('deliveries.deleteError'), { id: toastId });
    } finally {
      setDeliveryToDelete(null);
    }
  };

  const confirmMarkDelivered = async () => {
    if (!deliveryToMark) return;
    const delivery = deliveryToMark;
    const toastId = 'delivery-mark';
    toast.loading(t('deliveries.marking'), { id: toastId });

    try {
      await api.patch(`/deliveries/${delivery.id}/mark-delivered`, {
        deliveredBy: delivery.deliveredBy || '',
      });
      toast.success(t('deliveries.markedSuccess'), { id: toastId });
      refresh();
    } catch (error) {
      console.error('Error marking delivery as delivered:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string' ? raw : (raw?.message || raw?.error || t('deliveries.markDeliveredError'));
      toast.error(`${t('common.errorPrefix')}${message}`, { id: toastId, duration: 6000 });
    } finally {
      setDeliveryToMark(null);
    }
  };

  const closeForm = () => {
    setShowModal(false);
    setShowDiscardConfirm(false);
    setEditingDelivery(null);
    setFormData(EMPTY_FORM);
    setInitialForm(EMPTY_FORM);
    setTouched({});
    setSubmitAttempted(false);
    setServerErrors({});
  };

  /**
   * Fermeture par l'utilisateur (bouton Annuler, croix, clic à côté).
   * Un clic à côté suffit à fermer le modal, et perdre une adresse de livraison déjà tapée
   * comme ça, c'est vraiment énervant.
   */
  const requestCloseForm = () => {
    if (isDirty) {
      setShowDiscardConfirm(true);
      return;
    }
    closeForm();
  };

  const statusBadge = (delivery) => {
    const meta = delivery.status === 'DELIVERED'
      ? { key: 'deliveries.statusDelivered', icon: CheckCircle }
      : { key: 'deliveries.statusPending', icon: Clock };
    const Icon = meta.icon;
    return (
      <span className={badgeClass(DELIVERY_STATUS_TONE[delivery.status] ?? DELIVERY_STATUS_TONE.PENDING)}>
        <Icon className="w-3 h-3" aria-hidden="true" />
        {t(meta.key)}
      </span>
    );
  };

  // Compteurs sur toutes les livraisons, calculés en base (voir /api/deliveries/summary).
  // Si on les calculait ici, ils ne porteraient que sur la page affichée.
  const stats = summary;

  // Options renvoyées par /deliveries/filter-options. Si on les tirait de `deliveries`, on
  // n'aurait que celles de la page affichée et un filtre disparaîtrait en changeant de page.
  const clientOptions = filterOptions.clients;
  const cityOptions = filterOptions.cities;
  const countryOptions = filterOptions.countries;
  const advancedFields = useMemo(() => [
    {
      key: 'status',
      label: t('deliveries.status'),
      type: 'select',
      options: [
        { value: 'ALL', label: t('deliveries.filterAll') },
        { value: 'PENDING', label: t('deliveries.statusPending') },
        { value: 'LATE', label: t('deliveries.countLate') },
        { value: 'DELIVERED', label: t('deliveries.statusDelivered') },
      ],
    },
    {
      key: 'clientId',
      label: t('deliveries.clientLabel'),
      type: 'select',
      options: [
        { value: '', label: t('deliveries.filterAll') },
        ...clientOptions.map((c) => ({ value: String(c.id), label: c.label })),
      ],
    },
    {
      key: 'city',
      label: t('deliveries.cityLabel'),
      type: 'select',
      options: [{ value: '', label: t('deliveries.filterAll') }, ...cityOptions.map((c) => ({ value: c, label: c }))],
    },
    {
      key: 'country',
      label: t('deliveries.countryLabel'),
      type: 'select',
      options: [{ value: '', label: t('deliveries.filterAll') }, ...countryOptions.map((c) => ({ value: c, label: c }))],
    },
    {
      key: 'contact',
      label: t('deliveries.contactFilterLabel'),
      type: 'text',
      placeholder: t('deliveries.contactNamePlaceholder'),
    },
    { key: 'scheduledFrom', label: t('deliveries.scheduledFromLabel'), type: 'date' },
    { key: 'scheduledTo', label: t('deliveries.scheduledToLabel'), type: 'date' },
  ], [t, clientOptions, cityOptions, countryOptions]);

  const deliverySuggestions = rankSuggestions(
    deliveries,
    searchTerm,
    (d) => [d.deliveryNumber, d.order?.orderNumber, clientNameOf(d), d.contactName],
    8
  );

  const hasAdvancedFilters = Object.keys(EMPTY_ADVANCED)
    .some((key) => advanced[key] !== EMPTY_ADVANCED[key]);
  const hasActiveFilters = searchTerm.trim() !== '' || hasAdvancedFilters;
  const showFullList = hasActiveFilters || viewMode === 'all';

  // Debounce sur la saisie, sinon chaque caractère lancerait une requête.
  const [debouncedSearch, setDebouncedSearch] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchTerm.trim()), 300);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  /**
   * Paramètres envoyés au serveur.
   *
   * « En retard » n'est pas un statut : c'est une livraison ENCORE EN ATTENTE dont la date
   * prévue est passée (une livraison faite en retard n'est plus en retard). À l'écran il est
   * dans la même liste que les statuts, mais pour le serveur c'est un paramètre à part.
   */
  const queryParams = useMemo(() => {
    if (!showFullList) {
      return { page: 0, size: RECENT_COUNT, sort: 'scheduledDate,desc' };
    }
    const params = {
      page: currentPage - 1,
      size: itemsPerPage,
      sort: `${SORT_FIELDS[sortConfig.key] ?? 'scheduledDate'},${sortConfig.direction}`,
    };
    if (debouncedSearch) params.search = debouncedSearch;
    if (advanced.status === 'LATE') params.late = true;
    else if (advanced.status !== 'ALL') params.status = advanced.status;
    if (advanced.clientId) params.clientId = advanced.clientId;
    if (advanced.city) params.city = advanced.city;
    if (advanced.country) params.country = advanced.country;
    if (advanced.contact.trim()) params.contact = advanced.contact.trim();
    if (advanced.scheduledFrom) params.scheduledFrom = advanced.scheduledFrom;
    if (advanced.scheduledTo) params.scheduledTo = advanced.scheduledTo;
    return params;
  }, [showFullList, currentPage, itemsPerPage, sortConfig, debouncedSearch, advanced]);

  // Le filtrage, le tri et la pagination sont faits en base : `deliveries` contient déjà la
  // bonne page dans le bon ordre.
  const displayedDeliveries = deliveries;
  const totalPages = pageMeta.totalPages;

  useEffect(() => {
    fetchDeliveries();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryParams]);

  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, advanced, sortConfig, itemsPerPage, viewMode]);

  const handleSort = (key) => {
    setSortConfig((prev) => (
      prev.key === key
        ? { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' }
        : { key, direction: 'asc' }
    ));
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

  // Doit correspondre à Delivery.DeliveryStatus côté backend : une autre valeur serait refusée
  // avec un message incompréhensible pour le caissier.
  const statusOptions = [
    { value: 'PENDING', label: t('deliveries.statusPending') },
    { value: 'DELIVERED', label: t('deliveries.statusDelivered') },
  ];

  /* Colonnes par ordre d'importance : la livraison, le client, le lieu, la date.
   * Le contact et la ville sont cachés sur petit écran pour éviter un scroll horizontal
   * qui rendrait la colonne d'actions inaccessible. */
  const columns = [
    {
      key: 'deliveryNumber',
      label: t('deliveries.deliveryNumber'),
      sortable: true,
      render: (delivery) => (
        <div className="flex items-center gap-3">
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${
            delivery.status === 'DELIVERED'
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-200'
              : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-300'
          }`}>
            {delivery.status === 'DELIVERED'
              ? <PackageCheck className="w-5 h-5" aria-hidden="true" />
              : <Truck className="w-5 h-5" aria-hidden="true" />}
          </div>
          <div className="min-w-0">
            <div className="font-semibold text-gray-900 dark:text-gray-100">{delivery.deliveryNumber}</div>
            <div className="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
              <Hash className="w-3 h-3" aria-hidden="true" />
              {delivery.order?.orderNumber || '—'}
            </div>
          </div>
        </div>
      ),
    },
    {
      key: 'order',
      label: t('deliveries.sectionOrder'),
      sortable: true,
      render: (delivery) => (
        <div className="min-w-0">
          <div className="font-medium text-gray-900 dark:text-gray-100 truncate">
            {clientNameOf(delivery) || t('deliveries.guestClient')}
          </div>
          {delivery.order?.client?.company && (
            <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
              {delivery.order.client.company}
            </div>
          )}
        </div>
      ),
    },
    {
      key: 'destination',
      label: t('deliveries.address'),
      sortable: true,
      nowrap: false,
      className: 'hidden lg:table-cell',
      render: (delivery) => (
        <div className="flex items-start gap-2 max-w-xs">
          <MapPin className="w-3.5 h-3.5 mt-0.5 text-gray-400 flex-shrink-0" aria-hidden="true" />
          <div className="min-w-0">
            <div className="text-gray-700 dark:text-gray-300 truncate">{delivery.deliveryAddress}</div>
            <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
              {[delivery.deliveryPostalCode, delivery.deliveryCity].filter(Boolean).join(' ')}
            </div>
          </div>
        </div>
      ),
    },
    {
      key: 'contact',
      label: t('deliveries.columnContact'),
      className: 'hidden xl:table-cell',
      render: (delivery) => (
        <div className="space-y-1">
          <div className="flex items-center gap-2 text-gray-700 dark:text-gray-300">
            <User className="w-3.5 h-3.5 text-gray-400" aria-hidden="true" />
            <span className="truncate">{delivery.contactName}</span>
          </div>
          <div className="flex items-center gap-2">
            <Phone className="w-3.5 h-3.5 text-gray-400" aria-hidden="true" />
            <a
              href={`tel:${delivery.contactPhone}`}
              onClick={(e) => e.stopPropagation()}
              className="text-gray-700 dark:text-gray-300 hover:text-primary-600 dark:hover:text-primary-400 hover:underline"
            >
              {delivery.contactPhone}
            </a>
          </div>
        </div>
      ),
    },
    {
      key: 'scheduledDate',
      label: t('deliveries.scheduledDate'),
      sortable: true,
      render: (delivery) => {
        const late = isLate(delivery, today);
        return (
          <div className="flex items-center gap-2">
            <Calendar className={`w-4 h-4 ${late ? 'text-red-500' : 'text-gray-400'}`} aria-hidden="true" />
            <div>
              <div className={`tabular-nums ${late ? 'font-semibold text-red-600 dark:text-red-400' : 'text-gray-700 dark:text-gray-300'}`}>
                {formatDay(delivery.scheduledDate)}
              </div>
              {/* Le retard est aussi écrit en texte, pas seulement indiqué par la couleur. */}
              {late && (
                <div className="text-xs font-medium text-red-600 dark:text-red-400">
                  {t('deliveries.lateBadge')}
                </div>
              )}
            </div>
          </div>
        );
      },
    },
    {
      key: 'status',
      label: t('deliveries.status'),
      sortable: true,
      className: 'hidden sm:table-cell',
      render: statusBadge,
    },
  ];

  const emptyState = hasActiveFilters ? (
    <div className="flex flex-col items-center gap-3">
      <Truck className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('deliveries.noResultsTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('deliveries.noResultsHint')}</p>
      </div>
      <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
        {t('deliveries.resetFilters')}
      </Button>
    </div>
  ) : (
    <div className="flex flex-col items-center gap-3">
      <Truck className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('deliveries.emptyTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('deliveries.emptyHint')}</p>
      </div>
      {orders.length > 0 && (
        <Button variant="primary" size="sm" icon={Plus} onClick={() => openForm(null)}>
          {t('deliveries.addDelivery')}
        </Button>
      )}
    </div>
  );

  return (
    <div className="space-y-6">
      {/* ---- En-tête ---- */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <Truck aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('deliveries.title')}</h1>
            <p className="page-subtitle">{t('deliveries.subtitle')}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" icon={RefreshCw} onClick={refresh} loading={loading}>
            {t('common.refresh')}
          </Button>
          <Button variant="primary" icon={Plus} onClick={() => openForm(null)}>
            {t('deliveries.addDelivery')}
          </Button>
        </div>
      </div>

      {/* ---- Indicateurs ----
       * Trois compteurs. Pas de carte pour les retards : on les retrouve en un clic avec le
       * filtre, et ils se voient sur chaque ligne grâce à la date prévue. */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 sm:gap-6">
        <StatCard
          title={t('deliveries.totalCount')}
          value={stats.total}
          subtitle={t('deliveries.totalHint')}
          icon={Truck}
          tone="info"
          loading={loading}
        />
        <StatCard
          title={t('deliveries.countPending')}
          value={stats.pending}
          subtitle={t('deliveries.shareOfTotal', { percent: formatPercent(safeRatio(stats.pending, stats.total)) })}
          icon={Clock}
          tone="warning"
          loading={loading}
        />
        <StatCard
          title={t('deliveries.countDelivered')}
          value={stats.delivered}
          subtitle={t('deliveries.shareOfTotal', { percent: formatPercent(safeRatio(stats.delivered, stats.total)) })}
          icon={CheckCircle}
          tone="success"
          loading={loading}
        />
      </div>

      {/* ---- Recherche et filtres ---- */}
      <div className="card space-y-4">
        <AdvancedFilters
          id="deliveries"
          fields={advancedFields}
          values={advanced}
          defaults={EMPTY_ADVANCED}
          onChange={handleAdvancedChange}
          onReset={resetFilters}
          resettable={hasActiveFilters}
          expanded={filtersExpanded}
          onToggleExpanded={() => setFiltersExpanded((v) => !v)}
          dateRange={{ fromKey: 'scheduledFrom', toKey: 'scheduledTo' }}
          search={(
            <SearchBox
              value={searchTerm}
              onChange={setSearchTerm}
              placeholder={t('deliveries.searchPlaceholder')}
              suggestions={deliverySuggestions}
              getKey={(d) => d.id}
              onSelectSuggestion={(d) => setSearchTerm(d.deliveryNumber)}
              renderSuggestion={(d) => (
                <span className="flex items-center justify-between gap-2">
                  <span className="flex flex-col min-w-0">
                    <span className="font-medium truncate">{d.deliveryNumber}</span>
                    <span className="text-xs text-gray-400 truncate">
                      {d.order?.orderNumber} · {clientNameOf(d) || t('deliveries.guestClient')}
                    </span>
                  </span>
                  <span className="text-xs text-gray-500 shrink-0">{formatDay(d.scheduledDate)}</span>
                </span>
              )}
            />
          )}
        />
      </div>

      {/* ---- Liste ---- */}
      <div className="card overflow-hidden p-0">
        <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div>
            <h2 className="section-title">{t('deliveries.directory')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
              {showFullList ? t('deliveries.directoryHint') : t('deliveries.recentHint', { n: RECENT_COUNT })}
            </p>
          </div>
          {!hasActiveFilters && deliveries.length > 0 && (
            <SegmentedFilter
              label={t('deliveries.displayLabel')}
              value={viewMode}
              onChange={handleViewModeChange}
              options={[
                { value: 'recent', label: t('deliveries.viewRecent', { n: RECENT_COUNT }) },
                { value: 'all', label: t('deliveries.viewAll'), count: stats.total },
              ]}
            />
          )}
        </div>

        <Table
          columns={columns}
          data={displayedDeliveries}
          loading={loading}
          emptyState={emptyState}
          sortKey={sortConfig.key}
          sortDirection={sortConfig.direction}
          onSort={handleSort}
          onRowClick={setSelectedDelivery}
          actions={(delivery) => {
            const terminal = TERMINAL_STATUSES.includes(delivery.status);
            return (
              <>
                {delivery.status === 'PENDING' && (
                  <button
                    onClick={(e) => { e.stopPropagation(); setDeliveryToMark(delivery); }}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-600 hover:bg-green-700 text-white text-xs font-semibold rounded-lg transition-all shadow-sm hover:shadow"
                    title={t('deliveries.markDeliveredTooltip')}
                  >
                    <PackageCheck className="w-4 h-4" aria-hidden="true" />
                    <span>{t('deliveries.markDeliveredShort')}</span>
                  </button>
                )}
                <button
                  onClick={(e) => { e.stopPropagation(); setSelectedDelivery(delivery); }}
                  className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                  title={t('deliveries.viewDetails')}
                  aria-label={`${t('deliveries.viewDetails')} - ${delivery.deliveryNumber}`}
                >
                  <Eye className="w-4 h-4" aria-hidden="true" />
                </button>
                {/* Une livraison faite ne se modifie plus : les boutons restent visibles mais
                    désactivés, avec une infobulle. S'ils disparaissaient, on croirait à un bug. */}
                <button
                  onClick={(e) => { e.stopPropagation(); handleEdit(delivery); }}
                  className="text-primary-600 hover:text-primary-900 dark:hover:text-primary-300 p-2 hover:bg-primary-50 dark:hover:bg-primary-500/10 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                  title={terminal ? t('deliveries.terminalHint') : t('common.edit')}
                  aria-label={`${t('common.edit')} - ${delivery.deliveryNumber}`}
                  disabled={terminal}
                >
                  <Edit className="w-4 h-4" aria-hidden="true" />
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); setDeliveryToDelete(delivery); }}
                  className="text-red-600 hover:text-red-900 dark:hover:text-red-300 p-2 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                  title={terminal ? t('deliveries.terminalHint') : t('common.delete')}
                  aria-label={`${t('common.delete')} - ${delivery.deliveryNumber}`}
                  disabled={terminal}
                >
                  <Trash2 className="w-4 h-4" aria-hidden="true" />
                </button>
              </>
            );
          }}
        />

        {showFullList && !loading && pageMeta.totalElements > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={pageMeta.totalElements}
            itemsPerPage={itemsPerPage}
            onPageChange={setCurrentPage}
            onItemsPerPageChange={setItemsPerPage}
          />
        )}
      </div>

      {/* ---- Fiche livraison ---- */}
      <Modal
        isOpen={!!selectedDelivery}
        onClose={() => setSelectedDelivery(null)}
        title={t('deliveries.detailsTitle')}
        size="md"
      >
        {selectedDelivery && (
          <div className="space-y-6">
            <div className="flex flex-wrap items-start justify-between gap-3 pb-5 border-b border-gray-200 dark:border-gray-700">
              <div className="min-w-0">
                <h3 className="text-lg font-bold text-gray-900 dark:text-gray-100">
                  {selectedDelivery.deliveryNumber}
                </h3>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {selectedDelivery.order?.orderNumber} · {clientNameOf(selectedDelivery) || t('deliveries.guestClient')}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {statusBadge(selectedDelivery)}
                {isLate(selectedDelivery, today) && (
                  <span className="badge-danger">
                    <AlertTriangle className="w-3 h-3" aria-hidden="true" />
                    {t('deliveries.lateBadge')}
                  </span>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              <section className="space-y-3">
                <h4 className="subsection-title">{t('deliveries.sectionAddress')}</h4>
                <dl className="space-y-3">
                  <InfoRow icon={MapPin} label={t('deliveries.addressLabel')} value={selectedDelivery.deliveryAddress} />
                  <InfoRow
                    icon={MapPin}
                    label={t('deliveries.cityLabel')}
                    value={[selectedDelivery.deliveryPostalCode, selectedDelivery.deliveryCity].filter(Boolean).join(' ')}
                  />
                  <InfoRow icon={Globe} label={t('deliveries.countryLabel')} value={selectedDelivery.deliveryCountry} />
                </dl>
              </section>

              <section className="space-y-3">
                <h4 className="subsection-title">{t('deliveries.sectionContact')}</h4>
                <dl className="space-y-3">
                  <InfoRow icon={User} label={t('deliveries.contactNameLabel')} value={selectedDelivery.contactName} />
                  <InfoRow
                    icon={Phone}
                    label={t('deliveries.contactPhoneLabel')}
                    value={selectedDelivery.contactPhone}
                    href={selectedDelivery.contactPhone ? `tel:${selectedDelivery.contactPhone}` : undefined}
                  />
                  <InfoRow
                    icon={StickyNote}
                    label={t('deliveries.notesLabel')}
                    value={selectedDelivery.notes || t('deliveries.noNotes')}
                  />
                </dl>
              </section>
            </div>

            <section className="space-y-3 pt-2 border-t border-gray-200 dark:border-gray-700">
              <h4 className="subsection-title pt-3">{t('deliveries.sectionTracking')}</h4>
              <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <InfoRow icon={Hash} label={t('deliveries.reference')} value={`#${selectedDelivery.id}`} />
                <InfoRow icon={Calendar} label={t('deliveries.scheduledDate')} value={formatDay(selectedDelivery.scheduledDate)} />
                <InfoRow icon={Clock} label={t('deliveries.createdAtLabel')} value={formatDateTime(selectedDelivery.createdAt)} />
                <InfoRow
                  icon={PackageCheck}
                  label={t('deliveries.deliveredAtLabel')}
                  value={selectedDelivery.deliveredDate ? formatDateTime(selectedDelivery.deliveredDate) : null}
                />
              </dl>
            </section>

            <div className="flex flex-wrap items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
              <Button variant="secondary" onClick={() => setSelectedDelivery(null)}>
                {t('common.cancel')}
              </Button>
              {selectedDelivery.status === 'PENDING' && (
                <>
                  <Button variant="secondary" icon={Edit} onClick={() => handleEdit(selectedDelivery)}>
                    {t('common.edit')}
                  </Button>
                  <Button
                    variant="success"
                    icon={PackageCheck}
                    onClick={() => { setDeliveryToMark(selectedDelivery); setSelectedDelivery(null); }}
                  >
                    {t('deliveries.markDeliveredShort')}
                  </Button>
                </>
              )}
            </div>
          </div>
        )}
      </Modal>

      {/* ---- Formulaire ---- */}
      <Modal
        isOpen={showModal}
        onClose={requestCloseForm}
        title={editingDelivery ? t('deliveries.editTitle') : t('deliveries.createTitle')}
        size="lg"
      >
        {/* `noValidate` : on utilise notre propre validation, pas les bulles du navigateur
            (pas au style de l'appli et dans la langue du navigateur). */}
        <form onSubmit={handleSubmit} noValidate>
          {/* Liste des champs à corriger : le formulaire est long, le champ en erreur peut
              être hors de l'écran au moment d'enregistrer. */}
          {submitAttempted && invalidFields.length > 0 && (
            <div
              role="alert"
              className="mb-6 flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-500/30 dark:bg-red-500/10"
            >
              <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-red-600 dark:text-red-400" aria-hidden="true" />
              <div className="min-w-0 text-sm">
                <p className="font-semibold text-red-800 dark:text-red-300">
                  {t('deliveries.formErrorTitle', { count: invalidFields.length })}
                </p>
                <ul className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-red-700 dark:text-red-300/90">
                  {invalidFields.map((field) => (
                    <li key={field}>
                      <button
                        type="button"
                        onClick={() => focusField(field)}
                        className="underline underline-offset-2 hover:no-underline"
                      >
                        {fieldLabels[field]}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          )}

          {/* Ordre : la commande, l'adresse, le contact, puis le suivi. */}
          <div className="divide-y divide-gray-200 dark:divide-gray-700">
            <FormSection
              icon={ShoppingCart}
              title={t('deliveries.sectionOrder')}
              description={t('deliveries.sectionOrderHint')}
            >
              {editingDelivery ? (
                /* On ne peut pas changer la commande d'une livraison : `DeliveryUpdateRequest`
                   ne la contient pas, une livraison reste liée à sa commande. */
                <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-700 dark:bg-gray-900/40">
                  <p className="text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">
                    {t('deliveries.orderLabel')}
                  </p>
                  <p className="mt-1 font-semibold text-gray-900 dark:text-gray-100">
                    {editingDelivery.order?.orderNumber}
                  </p>
                  <p className="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
                    {editingDelivery.order?.client
                      ? `${editingDelivery.order.client.firstName || ''} ${editingDelivery.order.client.lastName || ''}`.trim()
                      : t('deliveries.guestClient')}
                  </p>
                </div>
              ) : (
                <FormSelect
                  label={t('deliveries.orderLabel')}
                  name="orderId"
                  value={formData.orderId}
                  onChange={handleInputChange}
                  error={visibleErrors.orderId}
                  required
                  options={orders.map(order => ({
                    value: order.id.toString(),
                    label: `${order.orderNumber} · ${order.client ? `${order.client.firstName} ${order.client.lastName}` : t('deliveries.guestClient')} (${order.totalAmount.toFixed(2)} €)`,
                  }))}
                  placeholder={t('deliveries.orderPlaceholder')}
                />
              )}
            </FormSection>

            <FormSection
              icon={MapPin}
              title={t('deliveries.addressSectionTitle')}
              description={t('deliveries.sectionAddressHint')}
            >
              <FormInput
                label={t('deliveries.addressLabel')}
                name="deliveryAddress"
                value={formData.deliveryAddress}
                onChange={handleInputChange}
                onBlur={handleBlur}
                placeholder={t('deliveries.addressPlaceholder')}
                error={visibleErrors.deliveryAddress}
                maxLength={MAX_LENGTHS.deliveryAddress}
                autoComplete="street-address"
                required
                icon={MapPin}
              />
              {/* Code postal puis ville, comme sur une adresse classique. */}
              <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
                <FormInput
                  label={t('deliveries.postalCodeLabel')}
                  name="deliveryPostalCode"
                  value={formData.deliveryPostalCode}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  placeholder={t('deliveries.postalCodePlaceholder')}
                  error={visibleErrors.deliveryPostalCode}
                  maxLength={MAX_LENGTHS.deliveryPostalCode}
                  autoComplete="postal-code"
                  required
                />
                <div className="sm:col-span-2">
                  <FormInput
                    label={t('deliveries.cityLabel')}
                    name="deliveryCity"
                    value={formData.deliveryCity}
                    onChange={handleInputChange}
                    onBlur={handleBlur}
                    placeholder={t('deliveries.cityPlaceholder')}
                    error={visibleErrors.deliveryCity}
                    maxLength={MAX_LENGTHS.deliveryCity}
                    autoComplete="address-level2"
                    required
                  />
                </div>
              </div>
              <FormInput
                label={t('deliveries.countryLabel')}
                name="deliveryCountry"
                value={formData.deliveryCountry}
                onChange={handleInputChange}
                onBlur={handleBlur}
                placeholder={t('deliveries.countryPlaceholder')}
                error={visibleErrors.deliveryCountry}
                maxLength={MAX_LENGTHS.deliveryCountry}
                autoComplete="country-name"
                required
                icon={Globe}
              />
            </FormSection>

            <FormSection
              icon={Calendar}
              title={t('deliveries.sectionSchedule')}
              description={t('deliveries.sectionScheduleHint')}
            >
              <FormInput
                label={t('deliveries.scheduledDateLabel')}
                name="scheduledDate"
                type="date"
                value={formData.scheduledDate}
                onChange={handleInputChange}
                onBlur={handleBlur}
                error={visibleErrors.scheduledDate}
                // Pas de date passée pour une nouvelle livraison ; une livraison déjà
                // enregistrée peut par contre avoir une date passée.
                min={editingDelivery ? undefined : today}
                required
                icon={Calendar}
              />
              {/* Statut seulement en modification : à la création le backend met toujours
                  « En attente », peu importe ce qu'on envoie. */}
              {editingDelivery && (
                <FormSelect
                  label={t('deliveries.statusLabel')}
                  name="status"
                  value={formData.status}
                  onChange={handleInputChange}
                  required
                  options={statusOptions}
                />
              )}
            </FormSection>

            <FormSection
              icon={User}
              title={t('deliveries.contactSectionTitle')}
              description={t('deliveries.contactHint')}
            >
              <div className="flex justify-end">
                <button
                  type="button"
                  onClick={handleCopyFromClient}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-primary-700 bg-primary-50 hover:bg-primary-100 border border-primary-200 rounded-lg transition-colors dark:text-primary-300 dark:bg-primary-500/10 dark:border-primary-500/30 dark:hover:bg-primary-500/20"
                  title={t('deliveries.copyFromClientTooltip')}
                >
                  <Copy className="w-3.5 h-3.5" aria-hidden="true" />
                  {t('deliveries.copyFromClient')}
                </button>
              </div>
              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
                <FormInput
                  label={t('deliveries.contactNameLabel')}
                  name="contactName"
                  value={formData.contactName}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  placeholder={t('deliveries.contactNamePlaceholder')}
                  error={visibleErrors.contactName}
                  maxLength={MAX_LENGTHS.contactName}
                  autoComplete="name"
                  required
                  icon={User}
                />
                <FormInput
                  label={t('deliveries.contactPhoneLabel')}
                  name="contactPhone"
                  type="tel"
                  value={formData.contactPhone}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  placeholder={t('deliveries.contactPhonePlaceholder')}
                  error={visibleErrors.contactPhone}
                  hint={t('deliveries.contactPhoneHint')}
                  autoComplete="tel"
                  required
                  icon={Phone}
                />
              </div>
            </FormSection>

            <FormSection
              icon={StickyNote}
              title={t('deliveries.notesLabel')}
              description={t('deliveries.sectionNotesHint')}
            >
              <FormInput
                label={t('deliveries.notesLabel')}
                name="notes"
                type="textarea"
                rows={3}
                value={formData.notes}
                onChange={handleInputChange}
                onBlur={handleBlur}
                placeholder={t('deliveries.notesPlaceholder')}
                error={visibleErrors.notes}
                maxLength={MAX_LENGTHS.notes}
              />
            </FormSection>
          </div>

          {/* Boutons collés en bas du modal : sur un petit écran le formulaire scrolle mais
              le bouton enregistrer reste visible. */}
          <div className="sticky -bottom-6 -mx-6 -mb-6 mt-6 flex flex-col-reverse gap-3 border-t border-gray-200 bg-white/95 px-6 py-4 backdrop-blur sm:flex-row sm:items-center sm:justify-between dark:border-gray-700 dark:bg-gray-800/95">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              {editingDelivery && !isDirty ? t('deliveries.noChanges') : t('clients.requiredHint')}
            </p>
            <div className="flex items-center justify-end gap-3">
              <Button variant="secondary" onClick={requestCloseForm} type="button">
                {t('common.cancel')}
              </Button>
              <Button
                variant="primary"
                type="submit"
                loading={saving}
                disabled={!canSubmit}
                icon={editingDelivery ? Edit : Plus}
              >
                {editingDelivery ? t('common.saveChanges') : t('common.create')}
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
        title={editingDelivery ? t('deliveries.confirmEditTitle') : t('deliveries.confirmCreateTitle')}
        message={editingDelivery ? t('deliveries.confirmEditMessage') : t('deliveries.confirmCreateMessage')}
        type="info"
      />

      <ConfirmModal
        isOpen={!!deliveryToMark}
        onClose={() => setDeliveryToMark(null)}
        onConfirm={confirmMarkDelivered}
        title={t('deliveries.confirmMarkTitle')}
        message={t('deliveries.confirmMarkNamed', { number: deliveryToMark?.deliveryNumber || '' })}
        type="success"
        confirmLabel={t('deliveries.markDeliveredShort')}
        cancelLabel={t('common.cancel')}
      />

      <ConfirmModal
        isOpen={!!deliveryToDelete}
        onClose={() => setDeliveryToDelete(null)}
        onConfirm={confirmDelete}
        title={t('deliveries.confirmDeleteTitle')}
        message={t('deliveries.confirmDeleteNamed', { number: deliveryToDelete?.deliveryNumber || '' })}
        type="danger"
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
      />
    </div>
  );
};

export default Deliveries;
