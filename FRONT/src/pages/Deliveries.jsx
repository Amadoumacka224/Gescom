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
import AdvancedFilters from '../components/AdvancedFilters';
import { rankSuggestions } from '../utils/searchSuggestions';
import { formatPercent, safeRatio } from '../utils/format';
import { DELIVERY_STATUS_TONE, badgeClass } from '../constants/statusBadges';

// Une livraison ne connaît que deux états : planifiée puis livrée. DELIVERED est terminal —
// reprendre une marchandise livrée serait un retour, hors de ce flux.
const TERMINAL_STATUSES = ['DELIVERED'];

/** Mémorise le mode d'affichage entre deux visites, comme les autres tableaux de bord. */
const VIEW_MODE_KEY = 'deliveriesViewMode';

/** Nombre de livraisons mises en avant dans la vue d'aperçu (les dernières créées). */
const RECENT_COUNT = 6;

/**
 * Critères de filtrage, à l'état neutre. Sert de valeur initiale, de cible du bouton
 * « Réinitialiser » et de référence pour savoir quels critères sont actifs.
 * Le statut y figure au même titre que les autres : il était porté par un bandeau segmenté
 * au-dessus de la liste, qui doublonnait avec les tuiles d'indicateurs.
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

// Le backend (LocalDateTime) renvoie "2025-12-01T10:00:00" ; l'input HTML type="date"
// n'accepte que "YYYY-MM-DD". Ces deux helpers font le pont sans perdre la valeur.
const toDateInputValue = (isoDateTime) => {
  if (!isoDateTime) return '';
  return String(isoDateTime).split('T')[0];
};

const toLocalDateTime = (dateInputValue) => {
  if (!dateInputValue) return null;
  // Pas de suffixe 'Z' : LocalDateTime côté backend rejette les offsets de timezone.
  return `${dateInputValue}T00:00:00`;
};

/** Jour seul d'un horodatage backend, comparable en chaîne (« 2026-07-28 »). */
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

/** Livraisons de la plus récemment créée à la plus ancienne (repli sur l'id si la date manque). */
const sortedByRecency = (list) =>
  [...list].sort((a, b) => (new Date(b.createdAt || 0) - new Date(a.createdAt || 0)) || (b.id - a.id));

/** Une livraison est en retard tant qu'elle n'est pas effectuée et que sa date prévue est passée. */
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
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [viewMode, setViewMode] = useState(() => localStorage.getItem(VIEW_MODE_KEY) || 'recent');
  const [formData, setFormData] = useState(EMPTY_FORM);

  // Jour courant, recalculé à chaque rendu de la page : sert de référence aux retards.
  const today = new Date().toISOString().slice(0, 10);

  useEffect(() => {
    refresh();
  }, []);

  const fetchDeliveries = async () => {
    try {
      const response = await api.get('/deliveries');
      setDeliveries(response.data);
      return response.data;
    } catch (error) {
      console.error('Error fetching deliveries:', error);
      toast.error(t('deliveries.loadError'));
      setDeliveries([]);
      return [];
    }
  };

  const fetchOrders = async (currentDeliveries) => {
    try {
      const response = await api.get('/orders');
      // Une livraison ne peut être créée qu'après facturation : on n'affiche que les
      // commandes INVOICED, et on exclut celles qui ont déjà une livraison.
      const deliveredOrderIds = new Set((currentDeliveries || []).map(d => d.order?.id).filter(Boolean));
      const availableOrders = response.data.filter(
        order => order.status === 'INVOICED' && !deliveredOrderIds.has(order.id)
      );
      setOrders(availableOrders);
    } catch (error) {
      console.error('Error fetching orders:', error);
      setOrders([]);
    }
  };

  const refresh = async () => {
    setLoading(true);
    try {
      const list = await fetchDeliveries();
      await fetchOrders(list);
    } finally {
      setLoading(false);
    }
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
    // Arrivée depuis la page Commandes : ouvrir le formulaire de livraison avec la commande
    // pré-sélectionnée (flux ordonné INVOICED → livrer). On attend que la liste des commandes
    // livrables soit chargée pour pré-remplir les coordonnées du client.
    if (location.state?.createForOrderId && orders.length > 0) {
      const id = location.state.createForOrderId;
      const selectedOrder = orders.find(o => o.id === parseInt(id));
      if (selectedOrder) {
        const defaults = buildClientDefaults(selectedOrder.client);
        setFormData(prev => ({ ...prev, orderId: String(id), ...defaults }));
        setShowModal(true);
      }
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [location.state, orders]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;

    if (name === 'orderId') {
      // Pré-remplit les coordonnées de livraison à partir du client de la commande,
      // mais uniquement pour les champs encore vides — l'édition utilisateur prime.
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
    // En création, la commande est trouvée via le filtre `orders` ; en édition,
    // la commande de la livraison n'est plus dans `orders` (filtre INVOICED) donc
    // on retombe sur la commande attachée à la livraison en cours d'édition.
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

  const handleSubmit = (e) => {
    e.preventDefault();

    if (!formData.orderId) {
      toast.error(t('deliveries.selectOrder'));
      return;
    }
    if (!formData.deliveryAddress || !formData.contactName || !formData.contactPhone || !formData.scheduledDate) {
      toast.error(t('deliveries.fillRequiredFields'));
      return;
    }

    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    const toastId = 'delivery-save';
    setSaving(true);
    toast.loading(t('deliveries.saving'), { id: toastId });

    try {
      const deliveryData = {
        orderId: parseInt(formData.orderId),
        deliveryAddress: formData.deliveryAddress,
        deliveryCity: formData.deliveryCity,
        deliveryPostalCode: formData.deliveryPostalCode,
        deliveryCountry: formData.deliveryCountry,
        contactName: formData.contactName,
        contactPhone: formData.contactPhone,
        scheduledDate: toLocalDateTime(formData.scheduledDate),
        status: formData.status,
        notes: formData.notes,
      };

      if (editingDelivery) {
        await api.put(`/deliveries/${editingDelivery.id}`, deliveryData);
        toast.success(t('deliveries.updatedSuccess'), { id: toastId });
      } else {
        await api.post('/deliveries', deliveryData);
        toast.success(t('deliveries.createdSuccess'), { id: toastId });
      }

      handleCloseModal();
      refresh();
    } catch (error) {
      console.error('Error saving delivery:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string' ? raw : (raw?.message || raw?.error || t('deliveries.saveError'));
      toast.error(`${t('common.errorPrefix')}${message}`, { id: toastId, duration: 6000 });
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (delivery) => {
    setEditingDelivery(delivery);
    setFormData({
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
    });
    setSelectedDelivery(null);
    setShowModal(true);
  };

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

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingDelivery(null);
    setFormData(EMPTY_FORM);
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

  const stats = useMemo(() => ({
    total: deliveries.length,
    pending: deliveries.filter(d => d.status === 'PENDING').length,
    delivered: deliveries.filter(d => d.status === 'DELIVERED').length,
    late: deliveries.filter(d => isLate(d, today)).length,
  }), [deliveries, today]);

  // Listes déduites des livraisons elles-mêmes : n'afficher que les valeurs réellement
  // présentes évite les critères qui ne rendent aucun résultat.
  const clientOptions = useMemo(() => {
    const byId = new Map();
    deliveries.forEach((d) => {
      const client = d.order?.client;
      if (client?.id) byId.set(client.id, { id: client.id, label: clientNameOf(d) || `#${client.id}` });
    });
    return [...byId.values()].sort((a, b) => a.label.localeCompare(b.label));
  }, [deliveries]);

  const cityOptions = useMemo(() => {
    const set = new Set(deliveries.map((d) => d.deliveryCity).filter(Boolean));
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [deliveries]);

  const countryOptions = useMemo(() => {
    const set = new Set(deliveries.map((d) => d.deliveryCountry).filter(Boolean));
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [deliveries]);

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

  const filteredDeliveries = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return deliveries.filter((delivery) => {
      if (advanced.status === 'LATE') {
        if (!isLate(delivery, today)) return false;
      } else if (advanced.status !== 'ALL' && delivery.status !== advanced.status) {
        return false;
      }

      if (advanced.clientId && String(delivery.order?.client?.id) !== String(advanced.clientId)) return false;
      if (advanced.city && delivery.deliveryCity !== advanced.city) return false;
      if (advanced.country && delivery.deliveryCountry !== advanced.country) return false;
      if (advanced.contact
        && !(delivery.contactName || '').toLowerCase().includes(advanced.contact.trim().toLowerCase())) {
        return false;
      }

      // Bornes inclusives sur le jour de la date prévue.
      if (advanced.scheduledFrom || advanced.scheduledTo) {
        const day = dayOf(delivery.scheduledDate);
        if (!day) return false;
        if (advanced.scheduledFrom && day < advanced.scheduledFrom) return false;
        if (advanced.scheduledTo && day > advanced.scheduledTo) return false;
      }

      if (!term) return true;
      return [
        delivery.deliveryNumber,
        delivery.order?.orderNumber,
        clientNameOf(delivery),
        delivery.contactName,
        delivery.deliveryCity,
      ].filter(Boolean).join(' ').toLowerCase().includes(term);
    });
  }, [deliveries, searchTerm, advanced, today]);

  const sortedDeliveries = useMemo(() => {
    const direction = sortConfig.direction === 'asc' ? 1 : -1;
    const valueOf = (delivery) => {
      switch (sortConfig.key) {
        case 'deliveryNumber': return (delivery.deliveryNumber || '').toLowerCase();
        case 'order': return (clientNameOf(delivery) || delivery.order?.orderNumber || '').toLowerCase();
        case 'destination': return (delivery.deliveryCity || '').toLowerCase();
        case 'status': return delivery.status === 'DELIVERED' ? 1 : 0;
        case 'scheduledDate':
        default: return dayOf(delivery.scheduledDate);
      }
    };
    return [...filteredDeliveries].sort((a, b) => {
      const left = valueOf(a);
      const right = valueOf(b);
      if (left < right) return -direction;
      if (left > right) return direction;
      return b.id - a.id;
    });
  }, [filteredDeliveries, sortConfig]);

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

  const totalPages = Math.max(1, Math.ceil(sortedDeliveries.length / itemsPerPage));
  const safePage = Math.min(currentPage, totalPages);
  const paginatedDeliveries = sortedDeliveries.slice((safePage - 1) * itemsPerPage, safePage * itemsPerPage);

  // Les dernières livraisons créées. À défaut de date de création exploitable, on retombe
  // sur l'id décroissant.
  const recentDeliveries = useMemo(
    () => sortedByRecency(deliveries).slice(0, RECENT_COUNT),
    [deliveries]
  );

  const displayedDeliveries = showFullList ? paginatedDeliveries : recentDeliveries;

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

  // Doit refléter Delivery.DeliveryStatus côté backend : toute autre valeur est rejetée à la
  // désérialisation, avec un message que le caissier ne peut pas interpréter.
  const statusOptions = [
    { value: 'PENDING', label: t('deliveries.statusPending') },
    { value: 'DELIVERED', label: t('deliveries.statusDelivered') },
  ];

  /* Colonnes ordonnées par importance : identité de la livraison, à qui elle va, où, quand.
   * Le contact et la ville se retirent sur écran étroit plutôt que d'imposer un défilement
   * horizontal qui rendrait la colonne d'actions inatteignable. */
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
              {/* Le retard n'est pas porté par la seule couleur : il est aussi écrit. */}
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
        <Button variant="primary" size="sm" icon={Plus} onClick={() => setShowModal(true)}>
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
          <Button variant="primary" icon={Plus} onClick={() => setShowModal(true)}>
            {t('deliveries.addDelivery')}
          </Button>
        </div>
      </div>

      {/* ---- Indicateurs ----
       * Trois compteurs de volume. Le retard n'a pas sa tuile : il reste accessible en un clic
       * depuis le filtre de la barre d'outils, et se repère ligne par ligne sur la date prévue. */}
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
                  aria-label={`${t('deliveries.viewDetails')} — ${delivery.deliveryNumber}`}
                >
                  <Eye className="w-4 h-4" aria-hidden="true" />
                </button>
                {/* Une livraison effectuée est figée : les actions restent visibles mais inertes,
                    avec l'explication en infobulle — les faire disparaître ferait croire à un bug. */}
                <button
                  onClick={(e) => { e.stopPropagation(); handleEdit(delivery); }}
                  className="text-primary-600 hover:text-primary-900 dark:hover:text-primary-300 p-2 hover:bg-primary-50 dark:hover:bg-primary-500/10 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                  title={terminal ? t('deliveries.terminalHint') : t('common.edit')}
                  aria-label={`${t('common.edit')} — ${delivery.deliveryNumber}`}
                  disabled={terminal}
                >
                  <Edit className="w-4 h-4" aria-hidden="true" />
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); setDeliveryToDelete(delivery); }}
                  className="text-red-600 hover:text-red-900 dark:hover:text-red-300 p-2 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                  title={terminal ? t('deliveries.terminalHint') : t('common.delete')}
                  aria-label={`${t('common.delete')} — ${delivery.deliveryNumber}`}
                  disabled={terminal}
                >
                  <Trash2 className="w-4 h-4" aria-hidden="true" />
                </button>
              </>
            );
          }}
        />

        {showFullList && !loading && sortedDeliveries.length > 0 && (
          <Pagination
            currentPage={safePage}
            totalPages={totalPages}
            totalItems={sortedDeliveries.length}
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
        onClose={handleCloseModal}
        title={editingDelivery ? t('deliveries.editTitle') : t('deliveries.createTitle')}
        size="lg"
      >
        <form onSubmit={handleSubmit} className="space-y-8">
          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <ShoppingCart className="w-5 h-5 text-primary-600" aria-hidden="true" />
              <h3 className="subsection-title">{t('deliveries.sectionOrder')}</h3>
            </div>
            <FormSelect
              label={t('deliveries.orderLabel')}
              name="orderId"
              value={formData.orderId}
              onChange={handleInputChange}
              required
              options={orders.map(order => ({
                value: order.id.toString(),
                label: `${order.orderNumber} — ${order.client ? `${order.client.firstName} ${order.client.lastName}` : t('deliveries.guestClient')} (${order.totalAmount.toFixed(2)} €)`,
              }))}
              placeholder={t('deliveries.orderPlaceholder')}
            />
          </section>

          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <MapPin className="w-5 h-5 text-primary-600" aria-hidden="true" />
              <h3 className="subsection-title">{t('deliveries.addressSectionTitle')}</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="md:col-span-2">
                <FormInput
                  label={t('deliveries.addressLabel')}
                  name="deliveryAddress"
                  value={formData.deliveryAddress}
                  onChange={handleInputChange}
                  placeholder={t('deliveries.addressPlaceholder')}
                  required
                  icon={MapPin}
                />
              </div>
              <FormInput
                label={t('deliveries.cityLabel')}
                name="deliveryCity"
                value={formData.deliveryCity}
                onChange={handleInputChange}
                placeholder={t('deliveries.cityPlaceholder')}
                required
                icon={MapPin}
              />
              <FormInput
                label={t('deliveries.postalCodeLabel')}
                name="deliveryPostalCode"
                value={formData.deliveryPostalCode}
                onChange={handleInputChange}
                placeholder={t('deliveries.postalCodePlaceholder')}
                required
              />
              <FormInput
                label={t('deliveries.countryLabel')}
                name="deliveryCountry"
                value={formData.deliveryCountry}
                onChange={handleInputChange}
                placeholder={t('deliveries.countryPlaceholder')}
                required
                icon={Globe}
              />
              <FormInput
                label={t('deliveries.scheduledDateLabel')}
                name="scheduledDate"
                type="date"
                value={formData.scheduledDate}
                onChange={handleInputChange}
                required
                icon={Calendar}
              />
            </div>
          </section>

          <section className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <div className="flex items-center gap-2">
                <User className="w-5 h-5 text-primary-600" aria-hidden="true" />
                <h3 className="subsection-title">{t('deliveries.contactSectionTitle')}</h3>
              </div>
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
            <p className="text-xs text-gray-500 dark:text-gray-400">{t('deliveries.contactHint')}</p>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <FormInput
                label={t('deliveries.contactNameLabel')}
                name="contactName"
                value={formData.contactName}
                onChange={handleInputChange}
                placeholder={t('deliveries.contactNamePlaceholder')}
                required
                icon={User}
              />
              <FormInput
                label={t('deliveries.contactPhoneLabel')}
                name="contactPhone"
                type="tel"
                value={formData.contactPhone}
                onChange={handleInputChange}
                placeholder={t('deliveries.contactPhonePlaceholder')}
                required
                icon={Phone}
              />
            </div>
          </section>

          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <StickyNote className="w-5 h-5 text-primary-600" aria-hidden="true" />
              <h3 className="subsection-title">{t('deliveries.sectionTracking')}</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <FormSelect
                label={t('deliveries.statusLabel')}
                name="status"
                value={formData.status}
                onChange={handleInputChange}
                required
                options={statusOptions}
              />
            </div>
            <FormInput
              label={t('deliveries.notesLabel')}
              name="notes"
              type="textarea"
              value={formData.notes}
              onChange={handleInputChange}
              placeholder={t('deliveries.notesPlaceholder')}
            />
          </section>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
            <Button variant="secondary" onClick={handleCloseModal} type="button">
              {t('common.cancel')}
            </Button>
            <Button variant="primary" type="submit" loading={saving} icon={editingDelivery ? Edit : Plus}>
              {editingDelivery ? t('common.saveChanges') : t('common.create')}
            </Button>
          </div>
        </form>
      </Modal>

      {/* ---- Confirmations ---- */}
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
