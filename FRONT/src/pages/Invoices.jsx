import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Plus, FileText, Euro, Download, Eye, X, CreditCard, User, MapPin, Phone, Mail, Calendar,
  Package, RefreshCw, AlertTriangle, Hash, CheckCircle, StickyNote, ShoppingCart,
} from 'lucide-react';
import { toast } from 'react-hot-toast';
import api from '../services/api';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import StripeTerminalModal from '../components/StripeTerminalModal';
import Pagination from '../components/Pagination';
import Button from '../components/Button';
import Table from '../components/Table';
import SearchBox from '../components/SearchBox';
import StatCard from '../components/StatCard';
import SegmentedFilter from '../components/SegmentedFilter';
import InfoRow from '../components/InfoRow';
import AdvancedFilters from '../components/AdvancedFilters';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import useSettings from '../hooks/useSettings';
import { rankSuggestions } from '../utils/searchSuggestions';
import { formatCurrency, formatDate, safeRatio } from '../utils/format';
import { generateInvoicePDF } from '../utils/pdfGenerator';
import { INVOICE_STATUS_TONE, badgeClass, metricBarClass } from '../constants/statusBadges';

/** Mémorise le mode d'affichage entre deux visites, comme les autres tableaux de bord. */
const VIEW_MODE_KEY = 'invoicesViewMode';

/** Nombre de factures mises en avant dans la vue d'aperçu (les dernières émises). */
const RECENT_COUNT = 6;

const PAYMENT_METHOD_KEYS = {
  CASH: 'invoices.cash',
  CREDIT_CARD: 'invoices.creditCard',
  DEBIT_CARD: 'invoices.creditCard',
  BANK_TRANSFER: 'invoices.bankTransfer',
  CHECK: 'invoices.check',
  MOBILE_PAYMENT: 'invoices.paymentMethod',
};

const STATUS_LABEL_KEYS = {
  PAID: 'invoices.paid',
  UNPAID: 'invoices.unpaid',
  PARTIALLY_PAID: 'invoices.partiallyPaid',
  CANCELED: 'invoices.canceled',
};

/**
 * Critères de filtrage, à l'état neutre. Sert de valeur initiale, de cible du bouton
 * « Réinitialiser » et de référence pour savoir quels critères sont actifs.
 * Le statut y figure au même titre que les autres : il était porté par un bandeau segmenté
 * au-dessus de la liste, qui doublonnait avec les tuiles d'indicateurs.
 */
const EMPTY_ADVANCED = {
  status: 'ALL',
  clientId: '',
  paymentMethod: '',
  issuedFrom: '',
  issuedTo: '',
  amountMin: '',
  amountMax: '',
  onlyRemaining: false,
};

/**
 * Formulaire de facturation vierge. Le taux de TVA et l'échéance viennent des réglages de
 * l'entreprise (cf. `useSettings`) et non de constantes : c'est l'écran Réglages qui fait foi.
 */
const emptyCreateForm = ({ taxRate, dueDate }) => ({
  orderId: '',
  invoiceDate: new Date().toISOString().split('T')[0],
  dueDate,
  paymentMethod: 'CASH',
  taxRate,
  notes: '',
});

const num = (value) => Number(value) || 0;

const clientNameOf = (invoice) =>
  invoice?.order?.client?.name
  || `${invoice?.order?.client?.firstName || ''} ${invoice?.order?.client?.lastName || ''}`.trim();

const Invoices = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();

  const [invoices, setInvoices] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState(null);

  const [searchTerm, setSearchTerm] = useState('');
  const [advanced, setAdvanced] = useState(EMPTY_ADVANCED);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const [sortConfig, setSortConfig] = useState({ key: 'invoiceDate', direction: 'desc' });
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [viewMode, setViewMode] = useState(() => localStorage.getItem(VIEW_MODE_KEY) || 'recent');

  const { settings, defaultTaxRate, defaultDueDate } = useSettings();

  /** Formulaire vierge aux valeurs configurées, réévaluées à chaque ouverture du modal. */
  const blankCreateForm = () =>
    emptyCreateForm({ taxRate: defaultTaxRate(), dueDate: defaultDueDate() });

  const [createForm, setCreateForm] = useState(blankCreateForm);
  const [createLoading, setCreateLoading] = useState(false);
  const [paymentData, setPaymentData] = useState({
    amount: '',
    paymentMethod: 'CASH',
    paymentDate: new Date().toISOString().split('T')[0],
  });
  const [paymentLoading, setPaymentLoading] = useState(false);
  // Terminal carte : le montant est figé à l'ouverture, l'encaissement se joue côté serveur.
  const [showTerminal, setShowTerminal] = useState(false);
  const [terminalAmount, setTerminalAmount] = useState(0);

  const today = new Date().toISOString().slice(0, 10);

  // Reste à payer d'une facture (même règle que le tableau de bord des commandes).
  const remainingOf = (invoice) =>
    num(invoice?.remainingAmount ?? (num(invoice?.totalAmount) - num(invoice?.paidAmount)));

  /** Facture échue : date d'échéance passée alors qu'il reste à encaisser. */
  const isOverdue = (invoice) =>
    !!invoice.dueDate
    && invoice.dueDate < today
    && invoice.status !== 'PAID'
    && invoice.status !== 'CANCELED';

  useEffect(() => {
    fetchInvoices();
  }, []);

  useEffect(() => {
    // Arrivée depuis une livraison : ouvrir directement le détail de la facture concernée.
    if (location.state?.invoiceId) {
      const invoice = invoices.find(inv => inv.id === location.state.invoiceId);
      if (invoice) {
        handleViewDetails(invoice);
        navigate(location.pathname, { replace: true, state: {} });
      }
    }
  }, [location.state, invoices]);

  useEffect(() => {
    // Arrivée depuis la page Commandes : ouvrir le formulaire de facturation avec la commande
    // pré-sélectionnée (flux ordonné CONFIRMED → facturer).
    if (location.state?.createForOrderId) {
      const orderId = location.state.createForOrderId;
      handleOpenCreateModal();
      handleOrderSelect(String(orderId));
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [location.state]);

  const fetchInvoices = async () => {
    try {
      setLoading(true);
      const response = await api.get('/invoices');
      setInvoices(response.data);
    } catch (error) {
      console.error('Error fetching invoices:', error);
      toast.error(t('invoices.loadError'));
    } finally {
      setLoading(false);
    }
  };

  const fetchOrders = async () => {
    try {
      const response = await api.get('/orders');
      // La livraison ne peut être créée qu'après facturation : seules les commandes
      // CONFIRMED (non encore facturées) sont éligibles à la facturation.
      const availableOrders = response.data.filter(order =>
        order.status === 'CONFIRMED' && !invoices.some(inv => inv.order?.id === order.id)
      );
      setOrders(availableOrders);
    } catch (error) {
      console.error('Error fetching orders:', error);
    }
  };

  const handleOpenCreateModal = () => {
    fetchOrders();
    // Les réglages arrivent après le premier rendu : on rebâtit le formulaire à l'ouverture
    // pour que le taux proposé soit bien celui configuré, pas le repli.
    setCreateForm(blankCreateForm());
    setShowCreateModal(true);
  };

  const handleCloseCreateModal = () => {
    setShowCreateModal(false);
    setSelectedOrder(null);
    setCreateForm(blankCreateForm());
  };

  const handleOrderSelect = async (orderId) => {
    if (!orderId) {
      setSelectedOrder(null);
      setCreateForm((prev) => ({ ...prev, orderId: '' }));
      return;
    }

    try {
      // Détail complet de la commande (client et lignes) pour l'aperçu de facturation.
      const response = await api.get(`/orders/${orderId}`);
      setSelectedOrder(response.data);
    } catch (error) {
      console.error('Error fetching order details:', error);
      setSelectedOrder(orders.find(o => o.id === parseInt(orderId)) || null);
    } finally {
      setCreateForm((prev) => ({ ...prev, orderId }));
    }
  };

  const handleCreateInvoice = () => {
    if (!createForm.orderId) {
      toast.error(t('invoices.selectOrder'));
      return;
    }
    // L'échéance ne peut pas précéder l'émission (même garde que la page Commandes).
    if (createForm.dueDate < createForm.invoiceDate) {
      toast.error("L'échéance ne peut pas être antérieure à la date de facture");
      return;
    }
    setShowConfirmModal(true);
  };

  const confirmCreateInvoice = async () => {
    const toastId = 'invoice-create';
    setCreateLoading(true);
    toast.loading(t('invoices.creating'), { id: toastId });

    try {
      await api.post('/invoices', {
        orderId: parseInt(createForm.orderId),
        invoiceDate: createForm.invoiceDate,
        dueDate: createForm.dueDate,
        paymentMethod: createForm.paymentMethod,
        taxRate: parseFloat(createForm.taxRate),
        notes: createForm.notes?.trim() || null,
      });
      toast.success(t('invoices.createdSuccess'), { id: toastId });
      handleCloseCreateModal();
      fetchInvoices();
    } catch (error) {
      console.error('Error creating invoice:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string' ? raw : (raw?.message || raw?.error || t('invoices.createError'));
      toast.error(`${t('common.errorPrefix')}${message}`, { id: toastId, duration: 6000 });
    } finally {
      setCreateLoading(false);
    }
  };

  const handleViewDetails = async (invoice) => {
    try {
      const response = await api.get(`/invoices/${invoice.id}`);
      setSelectedInvoice(response.data);
      setShowDetailsModal(true);
    } catch (error) {
      console.error('Error fetching invoice details:', error);
      toast.error(t('invoices.loadDetailsError'));
    }
  };

  const handleOpenPaymentModal = (invoice) => {
    // Gardes de statut (cohérentes avec le backend) : on n'encaisse ni une facture déjà soldée
    // ni une facture annulée.
    if (invoice.status === 'PAID') {
      toast('Cette facture est déjà entièrement réglée.', { icon: 'ℹ️' });
      return;
    }
    if (invoice.status === 'CANCELED') {
      toast.error(t('orders.page.linkedInvoiceCanceled'));
      return;
    }
    setSelectedInvoice(invoice);
    setPaymentData({
      amount: remainingOf(invoice).toFixed(2),
      paymentMethod: invoice.paymentMethod || 'CASH',
      paymentDate: new Date().toISOString().split('T')[0],
    });
    setShowPaymentModal(true);
  };

  /**
   * Montant saisi, validé contre le reste dû. Renvoie null (et signale l'erreur) si la saisie
   * ne convient pas. Partagé par l'encaissement manuel et le terminal carte : les deux
   * engagent la même somme, ils doivent la contrôler pareil.
   */
  const validatedPaymentAmount = () => {
    const amount = parseFloat(paymentData.amount);
    const remaining = remainingOf(selectedInvoice);
    if (!amount || amount <= 0) {
      toast.error(t('orders.page.enterValidAmount'));
      return null;
    }
    // Petite tolérance flottante pour autoriser le solde exact.
    if (amount > remaining + 0.001) {
      toast.error(t('orders.steps.amountExceeds', { amount: formatCurrency(remaining) }));
      return null;
    }
    return amount;
  };

  /** Bascule vers le terminal carte : le paiement part alors chez Stripe (mode test). */
  const handleOpenTerminal = () => {
    const amount = validatedPaymentAmount();
    if (amount === null) return;
    setTerminalAmount(amount);
    setShowPaymentModal(false);
    setShowTerminal(true);
  };

  const handlePayment = async () => {
    const amount = validatedPaymentAmount();
    if (amount === null) return;
    const toastId = 'invoice-payment';
    try {
      setPaymentLoading(true);
      toast.loading(t('invoices.paying'), { id: toastId });
      await api.patch(`/invoices/${selectedInvoice.id}/payment`, {
        amount,
        paymentMethod: paymentData.paymentMethod,
        paymentDate: paymentData.paymentDate,
      });
      toast.success(t('invoices.paymentRecordedSuccess'), { id: toastId });
      setShowPaymentModal(false);
      fetchInvoices();
    } catch (error) {
      console.error('Error processing payment:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string' ? raw : (raw?.message || raw?.error || t('invoices.paymentError'));
      toast.error(`${t('common.errorPrefix')}${message}`, { id: toastId, duration: 6000 });
    } finally {
      setPaymentLoading(false);
    }
  };

  const handleDownloadPDF = async (invoice) => {
    try {
      const response = await api.get(`/invoices/${invoice.id}`);
      // Coordonnées de l'entreprise (en-tête + mentions légales belges), déjà chargées par
      // `useSettings`. Leur indisponibilité ne bloque pas l'édition : le générateur applique
      // ses propres valeurs par défaut.
      generateInvoicePDF(response.data, settings || {});
    } catch (error) {
      console.error('Error generating PDF:', error);
      toast.error(t('invoices.pdfGenerationError'));
    }
  };

  const statusBadge = (invoice) => {
    const key = STATUS_LABEL_KEYS[invoice.status] ? invoice.status : 'UNPAID';
    return (
      <span className={badgeClass(INVOICE_STATUS_TONE[key])}>
        {t(STATUS_LABEL_KEYS[key])}
      </span>
    );
  };

  const paymentMethodText = (method) =>
    PAYMENT_METHOD_KEYS[method] ? t(PAYMENT_METHOD_KEYS[method]) : (method || '—');

  // Mêmes montants que le tableau de bord (`/dashboard/overview`), donc même périmètre : les
  // factures annulées sortent des livres. Sans ce filtre, « Revenus encaissés » et « En attente »
  // afficheraient ici des chiffres différents de ceux du tableau de bord pour les mêmes données,
  // le reliquat d'une facture annulée étant compté comme une créance vivante.
  const stats = useMemo(() => {
    const live = invoices.filter((inv) => inv.status !== 'CANCELED');
    return {
      total: invoices.length,
      collected: live.reduce((sum, inv) => sum + num(inv.paidAmount), 0),
      pending: live.reduce((sum, inv) => sum + (num(inv.totalAmount) - num(inv.paidAmount)), 0),
      overdue: invoices.filter(isOverdue).length,
      unpaid: invoices.filter((inv) => inv.status === 'UNPAID').length,
      partial: invoices.filter((inv) => inv.status === 'PARTIALLY_PAID').length,
      paid: invoices.filter((inv) => inv.status === 'PAID').length,
      canceled: invoices.filter((inv) => inv.status === 'CANCELED').length,
    };
  }, [invoices, today]);

  // Clients déduits des factures elles-mêmes : n'afficher que ceux qui en ont réellement une
  // évite un critère qui ne rendrait aucun résultat.
  const clientOptions = useMemo(() => {
    const byId = new Map();
    invoices.forEach((inv) => {
      const client = inv.order?.client;
      if (client?.id) byId.set(client.id, { id: client.id, label: clientNameOf(inv) || `#${client.id}` });
    });
    return [...byId.values()].sort((a, b) => a.label.localeCompare(b.label));
  }, [invoices]);

  const advancedFields = useMemo(() => [
    {
      key: 'status',
      label: t('deliveries.status'),
      type: 'select',
      options: [
        { value: 'ALL', label: t('invoices.filterAll') },
        { value: 'UNPAID', label: t('invoices.unpaid') },
        { value: 'PARTIALLY_PAID', label: t('invoices.partiallyPaid') },
        { value: 'OVERDUE', label: t('invoices.overdue') },
        { value: 'PAID', label: t('invoices.paid') },
        { value: 'CANCELED', label: t('invoices.canceled') },
      ],
    },
    {
      key: 'clientId',
      label: t('invoices.clientFilterLabel'),
      type: 'select',
      options: [
        { value: '', label: t('invoices.filterAll') },
        ...clientOptions.map((c) => ({ value: String(c.id), label: c.label })),
      ],
    },
    {
      key: 'paymentMethod',
      label: t('invoices.paymentMethodLabel'),
      type: 'select',
      options: [
        { value: '', label: t('invoices.filterAll') },
        ...Object.keys(PAYMENT_METHOD_KEYS).map((value) => ({ value, label: t(PAYMENT_METHOD_KEYS[value]) })),
      ],
    },
    { key: 'issuedFrom', label: t('invoices.issuedFromLabel'), type: 'date' },
    { key: 'issuedTo', label: t('invoices.issuedToLabel'), type: 'date' },
    { key: 'amountMin', label: t('invoices.amountMinLabel'), type: 'number', min: '0', step: '0.01', placeholder: '0,00' },
    { key: 'amountMax', label: t('invoices.amountMaxLabel'), type: 'number', min: '0', step: '0.01', placeholder: '—' },
    { key: 'onlyRemaining', label: t('invoices.onlyRemaining'), type: 'checkbox' },
  ], [t, clientOptions]);

  const filteredInvoices = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    const min = advanced.amountMin === '' ? null : parseFloat(advanced.amountMin);
    const max = advanced.amountMax === '' ? null : parseFloat(advanced.amountMax);

    return invoices.filter((invoice) => {
      if (advanced.status === 'OVERDUE') {
        if (!isOverdue(invoice)) return false;
      } else if (advanced.status !== 'ALL' && invoice.status !== advanced.status) {
        return false;
      }

      if (advanced.clientId && String(invoice.order?.client?.id) !== String(advanced.clientId)) return false;
      if (advanced.paymentMethod && invoice.paymentMethod !== advanced.paymentMethod) return false;

      // Bornes inclusives sur la date d'émission (déjà au format `yyyy-MM-dd`).
      if (advanced.issuedFrom && (invoice.invoiceDate || '') < advanced.issuedFrom) return false;
      if (advanced.issuedTo && (invoice.invoiceDate || '') > advanced.issuedTo) return false;

      const amount = num(invoice.totalAmount);
      if (min !== null && !Number.isNaN(min) && amount < min) return false;
      if (max !== null && !Number.isNaN(max) && amount > max) return false;

      // Reliquat : même tolérance au centime que le reste de l'écran.
      if (advanced.onlyRemaining && remainingOf(invoice) <= 0.001) return false;

      if (!term) return true;
      return [invoice.invoiceNumber, invoice.order?.orderNumber, clientNameOf(invoice), String(invoice.id)]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(term);
    });
  }, [invoices, searchTerm, advanced, today]);

  const sortedInvoices = useMemo(() => {
    const direction = sortConfig.direction === 'asc' ? 1 : -1;
    const valueOf = (invoice) => {
      switch (sortConfig.key) {
        case 'invoiceNumber': return (invoice.invoiceNumber || '').toLowerCase();
        case 'client': return (clientNameOf(invoice) || '').toLowerCase();
        case 'dueDate': return invoice.dueDate || '';
        case 'totalAmount': return num(invoice.totalAmount);
        case 'settlement': return safeRatio(num(invoice.paidAmount), num(invoice.totalAmount));
        case 'status': return invoice.status || '';
        case 'invoiceDate':
        default: return invoice.invoiceDate || '';
      }
    };
    return [...filteredInvoices].sort((a, b) => {
      const left = valueOf(a);
      const right = valueOf(b);
      if (left < right) return -direction;
      if (left > right) return direction;
      return b.id - a.id;
    });
  }, [filteredInvoices, sortConfig]);

  const invoiceSuggestions = rankSuggestions(
    invoices,
    searchTerm,
    (inv) => [inv.invoiceNumber, inv.order?.orderNumber, clientNameOf(inv)],
    8
  );

  const hasAdvancedFilters = Object.keys(EMPTY_ADVANCED)
    .some((key) => advanced[key] !== EMPTY_ADVANCED[key]);
  const hasActiveFilters = searchTerm.trim() !== '' || hasAdvancedFilters;
  const showFullList = hasActiveFilters || viewMode === 'all';

  const totalPages = Math.max(1, Math.ceil(sortedInvoices.length / itemsPerPage));
  const safePage = Math.min(currentPage, totalPages);
  const paginatedInvoices = sortedInvoices.slice((safePage - 1) * itemsPerPage, safePage * itemsPerPage);

  // Les dernières factures émises (la liste arrive déjà triée « plus récente d'abord » côté
  // backend ; on le réaffirme ici pour ne pas dépendre de cet ordre).
  const recentInvoices = useMemo(() => (
    [...invoices]
      .sort((a, b) => (b.invoiceDate || '').localeCompare(a.invoiceDate || '') || (b.id - a.id))
      .slice(0, RECENT_COUNT)
  ), [invoices]);

  const displayedInvoices = showFullList ? paginatedInvoices : recentInvoices;

  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, advanced, sortConfig, itemsPerPage, viewMode]);

  const handleSort = (key) => {
    setSortConfig((prev) => {
      if (prev.key === key) {
        return { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' };
      }
      // Dates et montants s'ouvrent du plus grand au plus petit, les textes de A à Z.
      const descFirst = ['invoiceDate', 'dueDate', 'totalAmount'].includes(key);
      return { key, direction: descFirst ? 'desc' : 'asc' };
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

  /* Colonnes : identité de la facture, à qui, combien, où en est le règlement, quand elle est due.
   * La progression du règlement est encodée deux fois — barre ET montants chiffrés — afin que
   * l'information ne repose jamais sur la seule longueur d'une barre. */
  const columns = [
    {
      key: 'invoiceNumber',
      label: t('invoices.invoiceNumber'),
      sortable: true,
      render: (invoice) => (
        <div className="flex items-center gap-3">
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${
            invoice.status === 'PAID'
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-200'
              : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-300'
          }`}>
            <FileText className="w-5 h-5" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <div className="font-semibold text-gray-900 dark:text-gray-100">{invoice.invoiceNumber}</div>
            <div className="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
              <Hash className="w-3 h-3" aria-hidden="true" />
              {invoice.order?.orderNumber || '—'}
            </div>
          </div>
        </div>
      ),
    },
    {
      key: 'client',
      label: t('invoices.columnClient'),
      sortable: true,
      render: (invoice) => (
        <div className="min-w-0">
          <div className="font-medium text-gray-900 dark:text-gray-100 truncate">
            {clientNameOf(invoice) || t('deliveries.guestClient')}
          </div>
          <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
            {paymentMethodText(invoice.paymentMethod)}
          </div>
        </div>
      ),
    },
    {
      key: 'totalAmount',
      label: t('invoices.columnAmount'),
      sortable: true,
      render: (invoice) => (
        <span className="font-semibold text-gray-900 dark:text-gray-100 tabular-nums">
          {formatCurrency(invoice.totalAmount)}
        </span>
      ),
    },
    {
      key: 'settlement',
      label: t('invoices.columnSettlement'),
      sortable: true,
      nowrap: false,
      className: 'hidden lg:table-cell',
      render: (invoice) => {
        const ratio = safeRatio(num(invoice.paidAmount), num(invoice.totalAmount));
        const remaining = remainingOf(invoice);
        const settled = remaining <= 0.001;
        return (
          <div className="w-40">
            <div className="metric-track" role="img" aria-label={`${Math.round(ratio * 100)} %`}>
              <div
                className={metricBarClass(settled ? 'success' : ratio > 0 ? 'warning' : 'neutral')}
                style={{ width: `${Math.max(ratio * 100, ratio > 0 ? 4 : 0)}%` }}
              />
            </div>
            <div className="mt-1.5 text-xs tabular-nums text-gray-600 dark:text-gray-400">
              {settled
                ? t('invoices.settledLabel')
                : `${formatCurrency(invoice.paidAmount)} · ${t('invoices.remainingLabel')} ${formatCurrency(remaining)}`}
            </div>
          </div>
        );
      },
    },
    {
      key: 'dueDate',
      label: t('invoices.dueDate'),
      sortable: true,
      className: 'hidden md:table-cell',
      render: (invoice) => {
        const overdue = isOverdue(invoice);
        return (
          <div className="flex items-center gap-2">
            <Calendar className={`w-4 h-4 ${overdue ? 'text-red-500' : 'text-gray-400'}`} aria-hidden="true" />
            <div>
              <div className={`tabular-nums ${overdue ? 'font-semibold text-red-600 dark:text-red-400' : 'text-gray-700 dark:text-gray-300'}`}>
                {formatDate(invoice.dueDate)}
              </div>
              {/* Le retard est écrit, pas seulement coloré. */}
              {overdue && (
                <div className="text-xs font-medium text-red-600 dark:text-red-400">{t('invoices.overdue')}</div>
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
      <FileText className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('invoices.noResultsTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('invoices.noResultsHint')}</p>
      </div>
      <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
        {t('invoices.resetFilters')}
      </Button>
    </div>
  ) : (
    <div className="flex flex-col items-center gap-3">
      <FileText className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('invoices.emptyTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('invoices.emptyHint')}</p>
      </div>
      <Button variant="primary" size="sm" icon={Plus} onClick={handleOpenCreateModal}>
        {t('invoices.addInvoice')}
      </Button>
    </div>
  );

  // Aperçu chiffré du formulaire de création : la TVA s'applique au total de la commande.
  const previewSubtotal = num(selectedOrder?.totalAmount);
  const previewTax = previewSubtotal * (parseFloat(createForm.taxRate) || 0) / 100;

  return (
    <div className="space-y-6">
      {/* ---- En-tête ---- */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <FileText aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('invoices.title')}</h1>
            <p className="page-subtitle">{t('invoices.subtitle')}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" icon={RefreshCw} onClick={fetchInvoices} loading={loading}>
            {t('common.refresh')}
          </Button>
          <Button variant="primary" icon={Plus} onClick={handleOpenCreateModal}>
            {t('invoices.addInvoice')}
          </Button>
        </div>
      </div>

      {/* ---- Indicateurs ----
       * Deux montants (encaissé, reste dû) puis deux compteurs (volume, échues) : les factures
       * échues sont le seul chiffre qui appelle une action, elles ont donc leur propre tuile. */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 sm:gap-6">
        <StatCard
          title={t('invoices.collectedRevenue')}
          value={formatCurrency(stats.collected)}
          subtitle={t('invoices.collectedHint')}
          icon={Euro}
          tone="success"
          loading={loading}
        />
        <StatCard
          title={t('invoices.pendingLabel')}
          value={formatCurrency(stats.pending)}
          subtitle={t('invoices.pendingHint')}
          icon={CreditCard}
          tone="warning"
          loading={loading}
        />
        <StatCard
          title={t('invoices.countOverdue')}
          value={stats.overdue}
          subtitle={t('invoices.overdueHint')}
          icon={AlertTriangle}
          tone="danger"
          loading={loading}
        />
        <StatCard
          title={t('invoices.totalCount')}
          value={stats.total}
          subtitle={t('invoices.totalHint')}
          icon={FileText}
          tone="info"
          loading={loading}
        />
      </div>

      {/* ---- Recherche et filtres ---- */}
      <div className="card space-y-4">
        <AdvancedFilters
          id="invoices"
          fields={advancedFields}
          values={advanced}
          defaults={EMPTY_ADVANCED}
          onChange={handleAdvancedChange}
          onReset={resetFilters}
          resettable={hasActiveFilters}
          expanded={filtersExpanded}
          onToggleExpanded={() => setFiltersExpanded((v) => !v)}
          dateRange={{ fromKey: 'issuedFrom', toKey: 'issuedTo' }}
          search={(
            <SearchBox
              value={searchTerm}
              onChange={setSearchTerm}
              placeholder={t('invoices.searchPlaceholder')}
              suggestions={invoiceSuggestions}
              getKey={(inv) => inv.id}
              onSelectSuggestion={(inv) => setSearchTerm(inv.invoiceNumber)}
              renderSuggestion={(inv) => (
                <span className="flex items-center justify-between gap-2">
                  <span className="flex flex-col min-w-0">
                    <span className="font-medium truncate">{inv.invoiceNumber}</span>
                    <span className="text-xs text-gray-400 truncate">
                      {clientNameOf(inv) || t('deliveries.guestClient')}
                    </span>
                  </span>
                  <span className="text-xs text-gray-500 shrink-0 tabular-nums">
                    {formatCurrency(inv.totalAmount)}
                  </span>
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
            <h2 className="section-title">{t('invoices.directory')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
              {showFullList ? t('invoices.directoryHint') : t('invoices.recentHint', { n: RECENT_COUNT })}
            </p>
          </div>
          {!hasActiveFilters && invoices.length > 0 && (
            <SegmentedFilter
              label={t('invoices.displayLabel')}
              value={viewMode}
              onChange={handleViewModeChange}
              options={[
                { value: 'recent', label: t('invoices.viewRecent', { n: RECENT_COUNT }) },
                { value: 'all', label: t('invoices.viewAll'), count: stats.total },
              ]}
            />
          )}
        </div>

        <Table
          columns={columns}
          data={displayedInvoices}
          loading={loading}
          emptyState={emptyState}
          sortKey={sortConfig.key}
          sortDirection={sortConfig.direction}
          onSort={handleSort}
          onRowClick={handleViewDetails}
          actions={(invoice) => (
            <>
              {invoice.status !== 'PAID' && invoice.status !== 'CANCELED' && (
                <button
                  onClick={(e) => { e.stopPropagation(); handleOpenPaymentModal(invoice); }}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-600 hover:bg-green-700 text-white text-xs font-semibold rounded-lg transition-all shadow-sm hover:shadow"
                  title={t('invoices.recordPayment')}
                >
                  <CreditCard className="w-4 h-4" aria-hidden="true" />
                  {t('invoices.paymentButton')}
                </button>
              )}
              <button
                onClick={(e) => { e.stopPropagation(); handleViewDetails(invoice); }}
                className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                title={t('invoices.viewDetails')}
                aria-label={`${t('invoices.viewDetails')} — ${invoice.invoiceNumber}`}
              >
                <Eye className="w-4 h-4" aria-hidden="true" />
              </button>
              <button
                onClick={(e) => { e.stopPropagation(); handleDownloadPDF(invoice); }}
                className="text-primary-600 hover:text-primary-900 dark:hover:text-primary-300 p-2 hover:bg-primary-50 dark:hover:bg-primary-500/10 rounded-lg transition-colors"
                title={t('invoices.downloadPdfTooltip')}
                aria-label={`${t('invoices.downloadPdfTooltip')} — ${invoice.invoiceNumber}`}
              >
                <Download className="w-4 h-4" aria-hidden="true" />
              </button>
            </>
          )}
        />

        {showFullList && !loading && sortedInvoices.length > 0 && (
          <Pagination
            currentPage={safePage}
            totalPages={totalPages}
            totalItems={sortedInvoices.length}
            itemsPerPage={itemsPerPage}
            onPageChange={setCurrentPage}
            onItemsPerPageChange={setItemsPerPage}
          />
        )}
      </div>

      {/* ---- Détail de la facture ---- */}
      <Modal
        isOpen={showDetailsModal && !!selectedInvoice}
        onClose={() => setShowDetailsModal(false)}
        title={t('invoices.detailsTitle')}
        size="lg"
      >
        {selectedInvoice && (
          <div className="space-y-6">
            <div className="flex flex-wrap items-start justify-between gap-3 pb-5 border-b border-gray-200 dark:border-gray-700">
              <div className="min-w-0">
                <h3 className="text-lg font-bold text-gray-900 dark:text-gray-100">
                  {selectedInvoice.invoiceNumber}
                </h3>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {selectedInvoice.order?.orderNumber} · {clientNameOf(selectedInvoice) || t('deliveries.guestClient')}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {statusBadge(selectedInvoice)}
                {isOverdue(selectedInvoice) && (
                  <span className="badge-danger">
                    <AlertTriangle className="w-3 h-3" aria-hidden="true" />
                    {t('invoices.overdue')}
                  </span>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              <section className="space-y-3">
                <h4 className="subsection-title">{t('invoices.sectionInvoice')}</h4>
                <dl className="space-y-3">
                  <InfoRow icon={Calendar} label={t('invoices.invoiceDateLabel')} value={formatDate(selectedInvoice.invoiceDate)} />
                  <InfoRow icon={Calendar} label={t('invoices.dueDateLabel')} value={formatDate(selectedInvoice.dueDate)} />
                  <InfoRow icon={CreditCard} label={t('invoices.paymentMethodLabel')} value={paymentMethodText(selectedInvoice.paymentMethod)} />
                  <InfoRow
                    icon={CheckCircle}
                    label={t('invoices.paymentDateLabel')}
                    value={selectedInvoice.paymentDate ? formatDate(selectedInvoice.paymentDate) : null}
                  />
                </dl>
              </section>

              {selectedInvoice.order?.client && (
                <section className="space-y-3">
                  <h4 className="subsection-title">{t('invoices.clientInfoTitle')}</h4>
                  <dl className="space-y-3">
                    <InfoRow icon={User} label={t('invoices.nameLabel')} value={clientNameOf(selectedInvoice)} />
                    <InfoRow
                      icon={Mail}
                      label={t('common.email')}
                      value={selectedInvoice.order.client.email}
                      href={selectedInvoice.order.client.email ? `mailto:${selectedInvoice.order.client.email}` : undefined}
                    />
                    <InfoRow
                      icon={Phone}
                      label={t('common.phone')}
                      value={selectedInvoice.order.client.phone}
                      href={selectedInvoice.order.client.phone ? `tel:${selectedInvoice.order.client.phone}` : undefined}
                    />
                    <InfoRow icon={MapPin} label={t('common.address')} value={selectedInvoice.order.client.address} />
                  </dl>
                </section>
              )}
            </div>

            {/* Articles : le total de ligne est celui calculé par le backend (`totalPrice`), net de
                la remise de ligne — le recalculer en prix × quantité afficherait un brut qui ne
                s'additionne pas au sous-total. */}
            {selectedInvoice.order?.items?.length > 0 && (
              <section className="space-y-3">
                <h4 className="subsection-title flex items-center gap-2">
                  <Package className="w-4 h-4 text-gray-400" aria-hidden="true" />
                  {t('invoices.itemsTitle')}
                </h4>
                <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
                  <table className="w-full">
                    <thead className="bg-gray-50 dark:bg-gray-900/40">
                      <tr>
                        <th scope="col" className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{t('common.product')}</th>
                        <th scope="col" className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{t('products.sellingPrice')}</th>
                        <th scope="col" className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{t('common.discount')}</th>
                        <th scope="col" className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{t('common.quantity')}</th>
                        <th scope="col" className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{t('common.total')}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100 dark:divide-gray-700/60">
                      {selectedInvoice.order.items.map((item, index) => (
                        <tr key={index} className="text-sm text-gray-700 dark:text-gray-300">
                          <td className="px-4 py-3">{item.product?.name || t('common.product')}</td>
                          <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(item.unitPrice)}</td>
                          <td className="px-4 py-3 text-right tabular-nums">
                            {num(item.discount) > 0 ? `${num(item.discount).toFixed(2)} %` : '—'}
                          </td>
                          <td className="px-4 py-3 text-right tabular-nums">{item.quantity}</td>
                          <td className="px-4 py-3 text-right font-semibold tabular-nums text-gray-900 dark:text-gray-100">
                            {formatCurrency(item.totalPrice ?? num(item.unitPrice) * num(item.quantity))}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            )}

            {/* Récapitulatif : la hiérarchie va du détail (sous-total, TVA) au chiffre qui décide
                de l'action à mener (reste à payer), mis en avant en dernier. */}
            <section className="space-y-3">
              <h4 className="subsection-title">{t('invoices.sectionSummary')}</h4>
              <div className="rounded-xl border border-gray-200 dark:border-gray-700 divide-y divide-gray-100 dark:divide-gray-700/60">
                <div className="flex justify-between px-4 py-2.5 text-sm">
                  <span className="text-gray-600 dark:text-gray-400">{t('invoices.subtotal')}</span>
                  <span className="font-medium tabular-nums">{formatCurrency(selectedInvoice.subtotal)}</span>
                </div>
                {num(selectedInvoice.discount) > 0 && (
                  <div className="flex justify-between px-4 py-2.5 text-sm">
                    <span className="text-gray-600 dark:text-gray-400">{t('common.discount')}</span>
                    <span className="font-medium tabular-nums text-red-600 dark:text-red-400">
                      −{formatCurrency(selectedInvoice.discount)}
                    </span>
                  </div>
                )}
                <div className="flex justify-between px-4 py-2.5 text-sm">
                  <span className="text-gray-600 dark:text-gray-400">
                    {t('invoices.taxLabel')} ({num(selectedInvoice.taxRate)} %)
                  </span>
                  <span className="font-medium tabular-nums">{formatCurrency(selectedInvoice.taxAmount)}</span>
                </div>
                <div className="flex justify-between px-4 py-3 bg-gray-50 dark:bg-gray-900/40">
                  <span className="font-semibold text-gray-900 dark:text-gray-100">{t('invoices.totalWithTax')}</span>
                  <span className="font-bold tabular-nums text-gray-900 dark:text-gray-100">
                    {formatCurrency(selectedInvoice.totalAmount)}
                  </span>
                </div>
                <div className="flex justify-between px-4 py-2.5 text-sm">
                  <span className="text-gray-600 dark:text-gray-400">{t('invoices.paidAmount')}</span>
                  <span className="font-medium tabular-nums text-green-600 dark:text-green-400">
                    {formatCurrency(selectedInvoice.paidAmount)}
                  </span>
                </div>
                <div className="flex justify-between px-4 py-3">
                  <span className="font-semibold text-gray-900 dark:text-gray-100">{t('invoices.remainingAmount')}</span>
                  <span className="font-bold tabular-nums text-gray-900 dark:text-gray-100">
                    {formatCurrency(remainingOf(selectedInvoice))}
                  </span>
                </div>
              </div>
            </section>

            {selectedInvoice.notes && (
              <section className="space-y-2">
                <h4 className="subsection-title flex items-center gap-2">
                  <StickyNote className="w-4 h-4 text-gray-400" aria-hidden="true" />
                  {t('invoices.notesLabel')}
                </h4>
                <p className="text-sm text-gray-600 dark:text-gray-300 bg-gray-50 dark:bg-gray-900/40 p-3 rounded-lg">
                  {selectedInvoice.notes}
                </p>
              </section>
            )}

            <div className="flex flex-wrap items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
              <Button variant="secondary" onClick={() => setShowDetailsModal(false)}>
                {t('common.cancel')}
              </Button>
              <Button variant="secondary" icon={Download} onClick={() => handleDownloadPDF(selectedInvoice)}>
                {t('invoices.downloadPdfButton')}
              </Button>
              {selectedInvoice.status !== 'PAID' && selectedInvoice.status !== 'CANCELED' && (
                <Button
                  variant="primary"
                  icon={CreditCard}
                  onClick={() => { setShowDetailsModal(false); handleOpenPaymentModal(selectedInvoice); }}
                >
                  {t('invoices.recordPayment')}
                </Button>
              )}
            </div>
          </div>
        )}
      </Modal>

      {/* ---- Création ---- */}
      <Modal
        isOpen={showCreateModal}
        onClose={handleCloseCreateModal}
        title={t('invoices.createNewTitle')}
        size="lg"
      >
        <div className="space-y-8">
          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <ShoppingCart className="w-5 h-5 text-primary-600" aria-hidden="true" />
              <h3 className="subsection-title">{t('invoices.sectionOrder')}</h3>
            </div>
            <FormSelect
              label={t('invoices.orderLabel')}
              name="orderId"
              value={createForm.orderId}
              onChange={(e) => handleOrderSelect(e.target.value)}
              required
              placeholder={orders.length ? t('invoices.selectOrderPlaceholder') : t('invoices.noOrderAvailable')}
              options={orders.map((order) => ({
                value: String(order.id),
                label: `${order.orderNumber} — ${order.client?.name || t('deliveries.guestClient')} (${formatCurrency(order.totalAmount)})`,
              }))}
            />

            {selectedOrder && (
              <dl className="grid grid-cols-1 sm:grid-cols-3 gap-4 p-4 rounded-xl bg-gray-50 dark:bg-gray-900/40 border border-gray-200 dark:border-gray-700">
                <InfoRow icon={User} label={t('invoices.nameLabel')} value={selectedOrder.client?.name} />
                <InfoRow
                  icon={Package}
                  label={t('invoices.itemsTitle')}
                  value={t('invoices.itemsCount', { count: selectedOrder.items?.length || 0 })}
                />
                <InfoRow icon={Euro} label={t('invoices.totalWithoutTax')} value={formatCurrency(selectedOrder.totalAmount)} />
              </dl>
            )}
          </section>

          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <CreditCard className="w-5 h-5 text-primary-600" aria-hidden="true" />
              <h3 className="subsection-title">{t('invoices.billingInfoTitle')}</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <FormInput
                label={t('invoices.invoiceDateLabel')}
                name="invoiceDate"
                type="date"
                value={createForm.invoiceDate}
                onChange={(e) => setCreateForm({ ...createForm, invoiceDate: e.target.value })}
                required
                icon={Calendar}
              />
              <FormInput
                label={t('invoices.dueDateLabel')}
                name="dueDate"
                type="date"
                value={createForm.dueDate}
                onChange={(e) => setCreateForm({ ...createForm, dueDate: e.target.value })}
                required
                icon={Calendar}
              />
              <FormSelect
                label={t('invoices.paymentMethodLabel')}
                name="paymentMethod"
                value={createForm.paymentMethod}
                onChange={(e) => setCreateForm({ ...createForm, paymentMethod: e.target.value })}
                required
                options={Object.keys(PAYMENT_METHOD_KEYS).map((value) => ({
                  value,
                  label: paymentMethodText(value),
                }))}
              />
              <FormInput
                label={t('invoices.taxRateLabel')}
                name="taxRate"
                type="number"
                step="0.01"
                min="0"
                max="100"
                value={createForm.taxRate}
                onChange={(e) => setCreateForm({ ...createForm, taxRate: e.target.value })}
                required
              />
              <div className="md:col-span-2">
                <FormInput
                  label={t('invoices.additionalNotesLabel')}
                  name="notes"
                  type="textarea"
                  value={createForm.notes}
                  onChange={(e) => setCreateForm({ ...createForm, notes: e.target.value })}
                  placeholder={t('invoices.notesPlaceholder')}
                />
              </div>
            </div>
          </section>

          {selectedOrder && (
            <section className="space-y-3">
              <h3 className="subsection-title">{t('invoices.previewTitle')}</h3>
              <div className="rounded-xl border border-gray-200 dark:border-gray-700 divide-y divide-gray-100 dark:divide-gray-700/60">
                <div className="flex justify-between px-4 py-2.5 text-sm">
                  <span className="text-gray-600 dark:text-gray-400">{t('invoices.totalWithoutTax')}</span>
                  <span className="font-medium tabular-nums">{formatCurrency(previewSubtotal)}</span>
                </div>
                <div className="flex justify-between px-4 py-2.5 text-sm">
                  <span className="text-gray-600 dark:text-gray-400">
                    {t('invoices.taxLabel')} ({createForm.taxRate || 0} %)
                  </span>
                  <span className="font-medium tabular-nums">{formatCurrency(previewTax)}</span>
                </div>
                <div className="flex justify-between px-4 py-3 bg-gray-50 dark:bg-gray-900/40">
                  <span className="font-semibold text-gray-900 dark:text-gray-100">{t('invoices.totalWithTax')}</span>
                  <span className="font-bold tabular-nums text-gray-900 dark:text-gray-100">
                    {formatCurrency(previewSubtotal + previewTax)}
                  </span>
                </div>
              </div>
            </section>
          )}

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
            <Button variant="secondary" onClick={handleCloseCreateModal}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="primary"
              icon={Plus}
              onClick={handleCreateInvoice}
              disabled={!createForm.orderId}
              loading={createLoading}
            >
              {t('invoices.createButton')}
            </Button>
          </div>
        </div>
      </Modal>

      {/* ---- Encaissement ---- */}
      <Modal
        isOpen={showPaymentModal && !!selectedInvoice}
        onClose={() => setShowPaymentModal(false)}
        title={t('invoices.recordPaymentTitle')}
        size="sm"
      >
        {selectedInvoice && (
          <div className="space-y-6">
            {/* Le reste à payer est le chiffre qui décide du montant saisi : il est posé en tête,
                pas en note sous le champ. */}
            <div className="rounded-xl border border-gray-200 dark:border-gray-700 divide-y divide-gray-100 dark:divide-gray-700/60">
              <div className="flex justify-between px-4 py-2.5 text-sm">
                <span className="text-gray-600 dark:text-gray-400">{t('invoices.totalWithTax')}</span>
                <span className="font-medium tabular-nums">{formatCurrency(selectedInvoice.totalAmount)}</span>
              </div>
              <div className="flex justify-between px-4 py-2.5 text-sm">
                <span className="text-gray-600 dark:text-gray-400">{t('invoices.alreadyPaid')}</span>
                <span className="font-medium tabular-nums text-green-600 dark:text-green-400">
                  {formatCurrency(selectedInvoice.paidAmount)}
                </span>
              </div>
              <div className="flex justify-between px-4 py-3 bg-gray-50 dark:bg-gray-900/40">
                <span className="font-semibold text-gray-900 dark:text-gray-100">{t('invoices.remainingAmount')}</span>
                <span className="font-bold tabular-nums text-gray-900 dark:text-gray-100">
                  {formatCurrency(remainingOf(selectedInvoice))}
                </span>
              </div>
            </div>

            <div className="space-y-4">
              <FormInput
                label={t('invoices.amountToPayLabel')}
                name="amount"
                type="number"
                step="0.01"
                min="0"
                value={paymentData.amount}
                onChange={(e) => setPaymentData({ ...paymentData, amount: e.target.value })}
                icon={Euro}
                required
              />
              <FormSelect
                label={t('invoices.paymentMethodLabel')}
                name="paymentMethod"
                value={paymentData.paymentMethod}
                onChange={(e) => setPaymentData({ ...paymentData, paymentMethod: e.target.value })}
                options={Object.keys(PAYMENT_METHOD_KEYS).map((value) => ({
                  value,
                  label: paymentMethodText(value),
                }))}
              />
              <FormInput
                label={t('invoices.paymentDateLabel')}
                name="paymentDate"
                type="date"
                value={paymentData.paymentDate}
                onChange={(e) => setPaymentData({ ...paymentData, paymentDate: e.target.value })}
                icon={Calendar}
              />
            </div>

            {/* Paiement carte : le montant et la facture sont les mêmes, seul l'encaissement
                passe par le prestataire au lieu d'être saisi de la main du caissier. */}
            <button
              type="button"
              onClick={handleOpenTerminal}
              disabled={paymentLoading}
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50 transition-colors disabled:opacity-60 disabled:cursor-not-allowed dark:border-primary-500/40 dark:text-primary-300 dark:hover:bg-primary-500/10"
            >
              <CreditCard className="w-4 h-4" aria-hidden="true" />
              {t('invoices.payByCard')}
            </button>

            <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
              <Button variant="secondary" onClick={() => setShowPaymentModal(false)}>
                {t('common.cancel')}
              </Button>
              <Button variant="primary" onClick={handlePayment} loading={paymentLoading}>
                {t('invoices.recordPaymentButton')}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* Terminal de paiement carte (Stripe, mode test) */}
      {showTerminal && selectedInvoice && (
        <StripeTerminalModal
          isOpen={showTerminal}
          onClose={() => setShowTerminal(false)}
          invoice={selectedInvoice}
          amount={terminalAmount}
          // Appelé aussi bien après un encaissement qu'après une session close par le serveur :
          // dans les deux cas la liste affichée n'est plus à jour.
          onPaid={() => fetchInvoices()}
        />
      )}

      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmCreateInvoice}
        title={t('invoices.createNewTitle')}
        message={`Voulez-vous vraiment créer cette facture${selectedOrder ? ` pour la commande ${selectedOrder.orderNumber}` : ''} ?`}
        type="info"
        confirmLabel={t('invoices.createButton')}
        cancelLabel={t('common.cancel')}
      />
    </div>
  );
};

export default Invoices;
