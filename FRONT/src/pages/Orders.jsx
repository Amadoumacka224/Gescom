import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Plus, Eye, CheckCircle, XCircle, Clock, User, Mail, Phone,
  MapPin, Package, Calendar, Euro, Truck, ShoppingCart, ClipboardList,
  ChevronUp, ChevronDown, ChevronsUpDown, Edit, Trash2,
  AlertCircle, CreditCard, FileText, Download, RotateCcw, RefreshCw
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import api from '../services/api';
import clientService from '../services/clientService';
import Modal from '../components/Modal';
import Button from '../components/Button';
import AmountRow from '../components/AmountRow';
import SearchableSelect from '../components/SearchableSelect';
import OrderWorkspace from '../components/OrderWorkspace';
import Pagination from '../components/Pagination';
import OrderFilters from '../components/OrderFilters';
import SegmentedFilter from '../components/SegmentedFilter';
import useSettings from '../hooks/useSettings';
import { EMPTY_ORDER_FILTERS } from '../constants/orderFilters';
import { ORDER_STATUS_TONE, badgeClass, resolveOrderStatusKey } from '../constants/statusBadges';
import { PAYMENT_METHODS } from '../constants/paymentMethods';
import { generateInvoicePDF } from '../utils/pdfGenerator';
import { computeItemsTotal, computeLineTotal, orderNetAmount, orderPayableAmount } from '../utils/orderTotals';
import { extractErrorMessage } from '../utils/apiError';
import { formatCurrency } from '../utils/format';
import { toast } from 'react-hot-toast';

// Les étapes gérées depuis cet écran, dans l'ordre (CANCELED n'en fait pas partie, c'est une
// sortie). La livraison est gérée ailleurs, mais le paiement fait partie du processus et c'est
// la dernière étape (« Payée »). Le paiement n'est pas sur la commande mais sur sa facture,
// donc on le déduit de la facture (voir lifecycleIndexFor).
const LIFECYCLE_STEPS = [
  { key: 'PENDING', labelKey: 'status.order.PENDING' },
  { key: 'CONFIRMED', labelKey: 'status.order.CONFIRMED' },
  { key: 'INVOICED', labelKey: 'status.order.INVOICED' },
  { key: 'PAID', labelKey: 'status.order.PAID' }
];

// Position de la commande dans [En attente, Confirmée, Facturée, Payée], calculée à partir du
// statut de la commande et de celui de sa facture (c'est la facture qui dit si c'est payé) :
//  - l'index donne l'étape en cours ; les étapes avant sont terminées ;
//  - un index >= LIFECYCLE_STEPS.length veut dire que tout est terminé.
// Règles :
//  - facturée + payée en entier → tout est terminé ;
//  - facturée + payée en partie → étape « Payée » en cours ;
//  - facturée + pas payée → étape « Facturée » en cours, « Payée » à venir ;
//  - livrée → terminé (payé), sauf si on sait que la facture n'est pas payée.
const lifecycleIndexFor = (status, invoice) => {
  const fullyPaid = invoice?.status === 'PAID';
  const partiallyPaid = invoice?.status === 'PARTIALLY_PAID';
  switch (status) {
    case 'PENDING':
      return 0;
    case 'CONFIRMED':
      return 1;
    case 'INVOICED':
      if (fullyPaid) return LIFECYCLE_STEPS.length;
      if (partiallyPaid) return 3;
      return 2;
    case 'DELIVERED':
      return fullyPaid || !invoice ? LIFECYCLE_STEPS.length : 3;
    default:
      return -1; // CANCELED / inconnu : aucune étape mise en avant
  }
};

// Le statut affiché vient de `constants/statusBadges.resolveOrderStatusKey`, utilisé aussi par
// la caisse et le tableau de bord. Avant, cet écran avait sa propre version qui ne gérait que
// le paiement complet : une commande payée en partie affichait « Facturée » ici et « Acompte
// versé » à la caisse.

/**
 * Avancement d'une commande, sous forme de barre en segments.
 *
 * Quatre segments côte à côte, un par étape [En attente → Confirmée → Facturée → Payée].
 * C'est ce qui marche le mieux dans une cellule de tableau répétée sur beaucoup de lignes :
 *   - un anneau (`components/ProgressRing`) fait ~40 px de haut et doublerait la hauteur des
 *     lignes sans rien apporter de plus ;
 *   - une barre continue afficherait « 75 % » sans dire de quoi, alors que ce sont des étapes ;
 *   - un graphique n'a pas de sens, ce n'est pas une évolution dans le temps.
 * Avec quatre segments alignés, on compare les lignes d'un coup d'œil.
 *
 * La couleur n'est jamais la seule info : le texte sous la barre donne l'étape en cours et
 * son numéro (« 3/4 · Facturée »), et `aria-label` le reprend pour les lecteurs d'écran.
 * Les couleurs suivent les nuances de bleu de la charte (voir src/index.css).
 *
 * L'avancement est calculé par `lifecycleIndexFor`, à partir du statut de la commande et de
 * sa facture (le paiement n'est pas sur la commande). Dans la liste, la facture est résumée
 * par `order.invoiceStatus`.
 */
// `compact` : la même barre en 96 px au lieu de 128, pour passer sous le badge de statut quand
// la colonne « Progression » est cachée.
const OrderProgress = ({ order, compact = false }) => {
  const { t } = useTranslation();
  const index = lifecycleIndexFor(order.status, { status: order.invoiceStatus });
  const canceled = index < 0;
  const done = index >= LIFECYCLE_STEPS.length;

  // La dernière étape s'appelle « Payée », c'est le but, pas forcément l'état actuel. Avec un
  // paiement partiel, on affichait « 4/4 · Payée » pour une facture à moitié payée. On affiche
  // donc le vrai état, avec les mêmes mots que le badge à côté.
  const stepLabel = order.invoiceStatus === 'PARTIALLY_PAID'
    ? t('status.order.PARTIALLY_PAID')
    : t(LIFECYCLE_STEPS[index]?.labelKey ?? 'status.order.PENDING');

  const caption = canceled
    ? t('status.order.CANCELED')
    : done
      ? t('orders.progress.done')
      : t('orders.progress.stepShort', {
        current: index + 1,
        total: LIFECYCLE_STEPS.length,
        label: stepLabel,
      });

  const ariaLabel = canceled
    ? t('orders.progress.canceledAria')
    : done
      ? t('orders.progress.doneAria')
      : t('orders.progress.stepAria', {
        current: index + 1,
        total: LIFECYCLE_STEPS.length,
        label: stepLabel,
      });

  return (
    <div className={compact ? 'w-24' : 'w-32'} role="img" aria-label={t('orders.progress.regionAria', { detail: ariaLabel })}>
      <div className="flex items-center gap-1" aria-hidden="true">
        {LIFECYCLE_STEPS.map((step, i) => {
          // Trois états par segment : fait (foncé), en cours (moyen), à venir (clair).
          // Une commande annulée n'a plus d'étape en cours, tout passe en neutre.
          // Les nuances sont choisies pour que « en cours » se distingue bien de « fait » et de
          // « à venir » dans les deux thèmes : en mode sombre, un bleu trop foncé se confondait
          // avec le gris bleuté du fond.
          // `gray-500` et pas `gray-600` en mode sombre : la barre est sur une carte `gray-800`,
          // il faut deux nuances d'écart pour qu'une barre de 6 px de haut se voie.
          let tone = 'bg-gray-200 dark:bg-gray-500';
          if (!canceled && i < index) tone = 'bg-primary-600 dark:bg-primary-300';
          else if (!canceled && i === index) tone = 'bg-primary-400 dark:bg-primary-500';
          return (
            <span
              key={step.key}
              title={step.label}
              className={`h-1.5 flex-1 rounded-full transition-colors ${tone}`}
            />
          );
        })}
      </div>
      <p
        className={`mt-1.5 text-[11px] font-medium truncate ${
          canceled ? 'text-gray-500 dark:text-gray-400' : 'text-gray-600 dark:text-gray-300'
        }`}
      >
        {caption}
      </p>
    </div>
  );
};

// Couleurs des cartes de stats, par valeur (voir la section des cartes dans index.css).
// Comme sur le tableau de bord, ces cartes utilisent les nuances de BLEU de la charte et pas
// les couleurs des badges : c'est l'intensité du bleu qui les distingue, dans l'ordre
// neutral < success < accent < info < warning < danger. Les badges de la colonne « Statut »
// gardent vert / orange / rouge, car une ligne de tableau doit se lire sans réfléchir.
// Chaque carte est entièrement colorée (fond, bordure, texte, icône), plus seulement l'icône.
// La valeur `neutral` a été retirée : aucune carte ici ne doit être grise.
// Les classes sont écrites en entier, sinon Tailwind les supprime du build.
const KPI_ACCENTS = {
  success: {
    tile: 'bg-primary-50 border-primary-200/70', label: 'text-primary-800', value: 'text-primary-900',
    iconBg: 'bg-primary-500/15', iconText: 'text-primary-700', ring: 'ring-primary-500'
  },
  accent: {
    tile: 'bg-secondary-100 border-secondary-200/70', label: 'text-secondary-700', value: 'text-secondary-800',
    iconBg: 'bg-secondary-500/20', iconText: 'text-secondary-700', ring: 'ring-secondary-500'
  },
  info: {
    tile: 'bg-primary-100 border-primary-300/70', label: 'text-primary-800', value: 'text-primary-900',
    iconBg: 'bg-primary-600/20', iconText: 'text-primary-700', ring: 'ring-primary-600'
  },
  warning: {
    tile: 'bg-primary-200 border-primary-300/70', label: 'text-primary-900', value: 'text-primary-900',
    iconBg: 'bg-primary-700/20', iconText: 'text-primary-800', ring: 'ring-primary-700'
  },
  danger: {
    tile: 'bg-primary-300 border-primary-400/70', label: 'text-primary-900', value: 'text-primary-900',
    iconBg: 'bg-primary-800/25', iconText: 'text-primary-900', ring: 'ring-primary-800'
  }
};

// Nombre de lignes par page par défaut. Doit être une des valeurs du sélecteur de
// `components/Pagination` (5/10/20/50/100), sinon il s'affiche vide au chargement.
const ORDERS_PER_PAGE = 20;

/** Garde le mode d'affichage d'une visite à l'autre, comme sur les autres écrans. */
const VIEW_MODE_KEY = 'ordersViewMode';

/** Nombre de commandes affichées dans l'aperçu (les dernières créées). */
const RECENT_COUNT = 5;

/**
 * En-tête de colonne triable.
 *
 * Défini en dehors de `Orders` : un composant redéfini à chaque rendu est démonté puis
 * remonté par React, et le bouton perdrait le focus clavier juste après le clic.
 *
 * `aria-sort` donne au lecteur d'écran la même info que l'icône.
 */
/**
 * Colonnes triables, avec la clé de tri envoyée au serveur.
 *
 * Deux colonnes ne sont plus triables, exprès : « Articles » est une somme des quantités, et
 * « Avancement » dépend du statut ET de la facture. La base ne sait trier ni l'un ni l'autre,
 * et les trier seulement dans la page affichée donnerait un faux tri. Donc on enlève le tri
 * plutôt que d'afficher quelque chose de faux.
 *
 * `payableAmount` n'est pas non plus un champ : c'est le montant TTC calculé en SQL, et le
 * serveur fait l'ORDER BY directement sur ce calcul. Le tri correspond donc bien aux montants
 * affichés, même quand il y a une facture.
 */
const SORT_FIELDS = {
  orderNumber: 'orderNumber',
  createdAt: 'createdAt',
  client: 'client.lastName',
  totalAmount: 'payableAmount',
  status: 'status',
};

const SortHeader = ({ label, sortKey, sort, onSort, align = 'left', className = '' }) => {
  const active = sort.key === sortKey;
  const Icon = !active ? ChevronsUpDown : sort.dir === 'asc' ? ChevronUp : ChevronDown;
  return (
    <th
      scope="col"
      aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
      // `px-4` réduit le padding de `.table-th` (px-6) : dans Tailwind `utilities` passe après
      // `components`, donc ça marche sans `!`. Avec huit colonnes en px-6, la colonne
      // « Actions » sortait de l'écran.
      className={`${align === 'right' ? 'table-th-right' : 'table-th'} px-4 ${className}`}
    >
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        className={`inline-flex items-center gap-1.5 hover:text-primary-600 dark:hover:text-primary-400 transition-colors ${
          active ? 'text-primary-700 dark:text-primary-300' : ''
        }`}
      >
        {label}
        <Icon className="w-3.5 h-3.5" aria-hidden="true" />
      </button>
    </th>
  );
};

// Couleurs des boutons icône, selon la gravité : voir en neutre, modifier en info, annuler
// en avertissement, supprimer en danger. Avant, l'orange et le vert utilisés n'étaient pas
// dans la charte et « Annuler » ressemblait autant que « Supprimer ».
const ICON_ACTION_STYLES = {
  neutral: 'text-gray-600 hover:bg-gray-100',
  info: 'text-blue-600 hover:bg-blue-50',
  warning: 'text-amber-600 hover:bg-amber-50',
  danger: 'text-red-600 hover:bg-red-50',
};

// Bouton icône pour les actions secondaires d'une commande.
// `onClick` reçoit l'événement : on arrête la propagation pour ne pas ouvrir le détail
// (clic sur la ligne). `disabled` sert pendant les actions async (génération du PDF)
// pour éviter le double clic.
const IconAction = ({ icon: Icon, title, color = 'neutral', onClick, disabled = false }) => (
  <button
    type="button"
    title={title}
    aria-label={title}
    disabled={disabled}
    onClick={(e) => { e.stopPropagation(); onClick(); }}
    className={`inline-flex items-center justify-center w-9 h-9 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-wait ${ICON_ACTION_STYLES[color]}`}
  >
    <Icon className="w-4 h-4" />
  </button>
);

const Orders = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const [orders, setOrders] = useState([]);
  const [products, setProducts] = useState([]);
  const [clients, setClients] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  // Tous les filtres dans un seul objet : il y en a quinze, et avec un `useState` par filtre
  // on ne pouvait pas tout réinitialiser ou compter les filtres actifs facilement.
  const [filters, setFilters] = useState(EMPTY_ORDER_FILTERS);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  // Tri du tableau. `dir` passe de asc à desc si on reclique sur la même colonne.
  const [sort, setSort] = useState({ key: 'createdAt', dir: 'desc' });
  const [page, setPage] = useState(1);
  const [perPage, setPerPage] = useState(ORDERS_PER_PAGE);
  // Par défaut on n'affiche que les dernières commandes. La liste complète s'affiche avec le
  // bouton de bascule, ou automatiquement dès qu'un filtre est actif.
  const [viewMode, setViewMode] = useState(() => localStorage.getItem(VIEW_MODE_KEY) || 'recent');
  // Nombre total de résultats, renvoyé par le serveur (on ne peut plus le déduire de
  // `orders`, qui ne contient que la page affichée).
  const [pageMeta, setPageMeta] = useState({ totalElements: 0, totalPages: 1 });
  // Nombre de commandes par statut pour les cartes. Porte sur toutes les commandes de
  // l'utilisateur, pas sur la page : les cartes servent justement à filtrer.
  const [summary, setSummary] = useState({
    total: 0, pending: 0, confirmed: 0, invoiced: 0, delivered: 0, canceled: 0,
  });
  // Utilisateurs et villes des listes déroulantes. Il faut la liste complète : quelqu'un qui
  // n'apparaît qu'en page 3 doit être proposé dès la page 1.
  const [filterOptions, setFilterOptions] = useState({ operators: [], cities: [] });
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  // Panier : tout le traitement de la commande se fait là (articles → validation →
  // confirmation → facturation → encaissement → PDF). Ouvert vide pour une nouvelle vente,
  // ou sur une commande existante pour continuer là où elle en est.
  const [showWorkspace, setShowWorkspace] = useState(false);
  const [workspaceOrder, setWorkspaceOrder] = useState(null);
  // Paiement d'une commande facturée : on paie sur sa facture (GET /invoices/order/:id).
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [paymentInvoice, setPaymentInvoice] = useState(null);
  const [paymentLoading, setPaymentLoading] = useState(false);
  // Facture de la commande ouverte dans le détail : sert à n'afficher le bouton de paiement
  // que s'il reste vraiment quelque chose à payer (sinon on affiche « réglée »).
  const [detailInvoice, setDetailInvoice] = useState(null);
  const [detailInvoiceLoading, setDetailInvoiceLoading] = useState(false);
  // Commande dont le PDF est en cours de génération (évite le double clic et affiche un
  // chargement : le PDF est généré dans le navigateur après un ou deux appels réseau).
  const [downloadingOrderId, setDownloadingOrderId] = useState(null);
  // Facturation directement depuis cet écran (sans passer par la page Factures). La commande
  // passe en INVOICED et le bouton « Paiement » apparaît ici.
  const [showInvoiceModal, setShowInvoiceModal] = useState(false);
  const [invoiceOrder, setInvoiceOrder] = useState(null);
  const [invoiceLoading, setInvoiceLoading] = useState(false);
  // TVA et échéance viennent des paramètres de l'entreprise, comme dans le panier : deux
  // écrans qui facturent la même commande doivent proposer le même taux.
  const { settings, defaultTaxRate, defaultDueDate } = useSettings();
  // Taux utilisé pour estimer le TTC des commandes pas encore facturées (liste, filtres et
  // tri) : le même partout.
  const listTaxRate = defaultTaxRate();
  const blankInvoiceForm = () => ({
    invoiceDate: new Date().toISOString().split('T')[0],
    dueDate: defaultDueDate(),
    paymentMethod: 'CASH',
    taxRate: defaultTaxRate(),
    notes: '',
  });
  const [invoiceForm, setInvoiceForm] = useState(blankInvoiceForm);
  const [paymentForm, setPaymentForm] = useState({
    amount: '',
    paymentMethod: 'CASH',
    paymentDate: new Date().toISOString().split('T')[0],
  });
  const [editForm, setEditForm] = useState({
    status: '',
    totalAmount: '',
    orderItems: []
  });

  // Quand on change un filtre on revient à la page 1 : rester en page 4 alors qu'il n'y a
  // plus qu'une page afficherait une liste vide, on croirait à un bug.
  const handleFilterChange = (field, value) => {
    setFilters((prev) => ({ ...prev, [field]: value }));
    setPage(1);
  };

  const resetFilters = () => {
    setFilters(EMPTY_ORDER_FILTERS);
    setPage(1);
  };

  // Ouvre le panier. Sans commande : nouvelle vente. Avec une commande : on reprend là où
  // elle en est (confirmation, facturation, encaissement).
  const openWorkspace = (order = null) => {
    setWorkspaceOrder(order);
    setShowWorkspace(true);
  };

  // Après une action dans le panier, la liste et le stock des produits ont changé.
  const refreshAfterWorkspaceAction = () => {
    refreshOrders();
    fetchProducts();
  };

  // Données de référence et compteurs : chargés une fois, ils ne dépendent pas de la page.
  // Le chargement de la page est plus bas, après `queryParams` dont il dépend (si on
  // l'utilisait ici, il ne serait pas encore initialisé).
  useEffect(() => {
    fetchProducts();
    fetchClients();
    fetchCategories();
    fetchSummary();
    fetchFilterOptions();
  }, []);

  useEffect(() => {
    // Commande à ouvrir au chargement : soit ?orderId= dans l'URL (caisse, suivi des caisses ;
    // le lien peut être partagé ou ouvert dans un nouvel onglet), soit l'ancien passage par
    // localStorage encore utilisé par la page Rapports.
    const selectedOrderId = searchParams.get('orderId') || localStorage.getItem('selectedOrderId');
    if (selectedOrderId && orders.length > 0) {
      const order = orders.find(o => o.id === parseInt(selectedOrderId));
      if (order) {
        setSelectedOrder(order);
        setShowDetailsModal(true);
        // Scroll jusqu'à la commande dans la liste
        setTimeout(() => {
          const element = document.getElementById(`order-${selectedOrderId}`);
          if (element) {
            element.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }, 100);
      }
      // Une fois utilisé, on nettoie les deux pour ne pas rouvrir le détail au rechargement.
      localStorage.removeItem('selectedOrderId');
      if (searchParams.has('orderId')) {
        setSearchParams({}, { replace: true });
      }
    }
  }, [orders, searchParams, setSearchParams]);

  // Quand on ouvre le détail d'une commande facturée, on charge sa facture pour connaître
  // l'état du paiement (payée / reste à payer / annulée) et proposer le bon bouton. Un 404
  // (pas encore de facture) est ignoré : la partie paiement reste cachée.
  useEffect(() => {
    if (!showDetailsModal || !selectedOrder || !canPayOrder(selectedOrder)) {
      setDetailInvoice(null);
      setDetailInvoiceLoading(false);
      return;
    }
    let active = true;
    setDetailInvoice(null);
    setDetailInvoiceLoading(true);
    api.get(`/invoices/order/${selectedOrder.id}`)
      .then(({ data }) => { if (active) setDetailInvoice(data); })
      .catch(() => { if (active) setDetailInvoice(null); })
      .finally(() => { if (active) setDetailInvoiceLoading(false); });
    return () => { active = false; };
  }, [showDetailsModal, selectedOrder]);

  const fetchProducts = async () => {
    try {
      const response = await api.get('/products/active');
      setProducts(response.data);
    } catch (error) {
      console.error('Error fetching products:', error);
      setProducts([]);
    }
  };

  // Catégories : on charge toute la liste (pas seulement celles des produits) pour que la
  // colonne de gauche affiche aussi les catégories vides, où on ajoutera peut-être des produits
  // plus tard. Les catégories désactivées (active === false) sont cachées.
  const fetchCategories = async () => {
    try {
      const response = await api.get('/categories');
      setCategories(response.data);
    } catch (error) {
      console.error('Error fetching categories:', error);
      setCategories([]);
    }
  };

  const fetchClients = async () => {
    try {
      const response = await clientService.getActiveClients();
      setClients(response.data);
    } catch (error) {
      console.error('Error fetching clients:', error);
      setClients([]);
    }
  };

  const fetchOrders = async () => {
    try {
      setLoading(true);
      const { data } = await api.get('/orders/search', { params: queryParams });
      setOrders(data.content || []);
      setPageMeta({
        totalElements: data.totalElements ?? 0,
        totalPages: Math.max(1, data.totalPages ?? 1),
      });
    } catch (error) {
      // Si le chargement échoue, la liste reste vide et on affiche une erreur. Avant, l'écran
      // affichait des fausses commandes de démo qu'on ne pouvait pas distinguer des vraies :
      // l'appli avait l'air de marcher alors que l'API ne répondait pas.
      console.error('Error fetching orders:', error);
      toast.error(t('orders.loadError'));
      setOrders([]);
    } finally {
      setLoading(false);
    }
  };

  /** Compteurs des cartes : à recharger après chaque modif, un changement de statut les fait bouger. */
  const fetchSummary = async () => {
    try {
      const { data } = await api.get('/orders/summary');
      setSummary(data);
    } catch (error) {
      console.error('Error fetching order summary:', error);
    }
  };

  /** Options des filtres. Endpoint à part car /users est réservé à l'ADMIN. */
  const fetchFilterOptions = async () => {
    try {
      const { data } = await api.get('/orders/filter-options');
      setFilterOptions(data);
    } catch (error) {
      console.error('Error fetching order filter options:', error);
    }
  };

  /** Recharge la page, les cartes et les options. À appeler après chaque modif d'une commande. */
  const refreshOrders = async () => {
    await Promise.all([fetchOrders(), fetchSummary(), fetchFilterOptions()]);
  };

  const handleViewDetails = (order) => {
    // On utilise directement les données si on les a déjà
    setSelectedOrder(order);
    setShowDetailsModal(true);
  };

  const handleEdit = (order) => {
    // Une commande ne se modifie qu'en brouillon (PENDING). Une fois confirmée, les lignes
    // sont figées (le stock est sorti et elles servent pour la facture).
    if (order.status !== 'PENDING') {
      toast(t('orders.page.onlyPendingEditable'), { icon: 'ℹ️' });
      handleViewDetails(order);
      return;
    }
    setSelectedOrder(order);
    // Met les lignes au bon format, avec productId
    const formattedItems = (order.items || []).map(item => ({
      productId: item.product?.id?.toString() || '',
      unitPrice: item.unitPrice || 0,
      discount: item.discount || 0,
      quantity: item.quantity || 1,
      product: item.product // Garder l'objet product pour l'affichage
    }));

    setEditForm({
      clientId: order.client?.id?.toString() || '',
      status: order.status || 'PENDING',
      totalAmount: order.totalAmount || 0,
      orderItems: formattedItems
    });
    setShowEditModal(true);
  };

  const handleUpdateOrder = async () => {
    try {
      // Vérifie que chaque ligne a bien un produit
      const hasEmptyProduct = editForm.orderItems.some(item => !item.productId);
      if (hasEmptyProduct) {
        toast.error(t('orders.selectProductForItem'));
        return;
      }

      if (editForm.orderItems.length === 0) {
        toast.error(t('orders.addAtLeastOneItem'));
        return;
      }

      // Contrôle du stock : pas la peine d'enregistrer une commande qu'on ne pourra pas confirmer.
      if (editHasStockIssue) {
        toast.error(t('orders.page.linesExceedStock'));
        return;
      }

      // Lignes au format attendu par le backend (DTO)
      const transformedItems = editForm.orderItems.map(item => ({
        productId: parseInt(item.productId),
        quantity: parseInt(item.quantity),
        discount: parseFloat(item.discount) || 0
      }));

      // Données pour la mise à jour. On n'envoie pas le statut : il change seulement via les
      // actions prévues (confirmation, annulation) côté backend.
      const updateData = {
        items: transformedItems
      };

      await api.put(`/orders/${selectedOrder.id}`, updateData);
      toast.success(t('orders.page.updateSuccess'));
      setShowEditModal(false);
      refreshOrders();
    } catch (error) {
      console.error('Error updating order:', error);
      if (error.response?.status === 401) {
        toast.error(t('auth.sessionExpired'));
      } else {
        toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
      }
    }
  };

  const handleConfirmOrder = async (order) => {
    try {
      await api.post(`/orders/${order.id}/confirm`);
      toast.success(t('orders.workspace.orderConfirmed'));
      refreshOrders();
    } catch (error) {
      console.error('Error confirming order:', error);
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  };

  const handleCancelOrder = async (order) => {
    if (!window.confirm(t('orders.page.confirmCancel', { number: order.orderNumber }))) {
      return;
    }
    try {
      await api.patch(`/orders/${order.id}/cancel`);
      toast.success(t('orders.workspace.orderCanceled'));
      refreshOrders();
    } catch (error) {
      console.error('Error canceling order:', error);
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  };

  // Étape suivante : facturer sans quitter cet écran. On ouvre un modal pré-rempli (mêmes
  // valeurs par défaut que la page Factures), on crée la facture via l'API puis on recharge
  // la liste.
  const handleInvoiceOrder = (order) => {
    setInvoiceOrder(order);
    setInvoiceForm(blankInvoiceForm());
    setShowInvoiceModal(true);
  };

  const handleSubmitInvoice = async () => {
    if (!invoiceOrder) return;
    // Mêmes contrôles que le backend (InvoiceCreateRequest), plus quelques vérifs métier :
    if (!invoiceForm.invoiceDate || !invoiceForm.dueDate) {
      toast.error(t('orders.page.invoiceDatesRequired'));
      return;
    }
    // L'échéance ne peut pas être avant la date d'émission.
    if (invoiceForm.dueDate < invoiceForm.invoiceDate) {
      toast.error(t('orders.steps.dueBeforeInvoice'));
      return;
    }
    const taxRate = parseFloat(invoiceForm.taxRate);
    if (Number.isNaN(taxRate) || taxRate < 0 || taxRate > 100) {
      toast.error(t('orders.page.taxRateRange'));
      return;
    }
    try {
      setInvoiceLoading(true);
      await api.post('/invoices', {
        orderId: invoiceOrder.id,
        invoiceDate: invoiceForm.invoiceDate,
        dueDate: invoiceForm.dueDate,
        paymentMethod: invoiceForm.paymentMethod,
        taxRate,
        notes: invoiceForm.notes?.trim() || null,
      });
      toast.success(t('orders.page.invoiceCreated'));
      setShowInvoiceModal(false);
      refreshOrders();
    } catch (error) {
      console.error('Error creating invoice:', error);
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setInvoiceLoading(false);
    }
  };

  // Bouton principal : la seule étape suivante possible selon le statut (Confirmer → Facturer
  // → Encaisser). La livraison est gérée ailleurs, donc une commande livrée (DELIVERED),
  // annulée (CANCELED) ou déjà payée n'a pas de bouton principal (null). `key` sert à ne pas
  // afficher deux fois « Paiement » quand il est déjà en action secondaire (voir
  // canRecordPayment).
  // `invoice` est optionnelle : si elle n'est pas chargée, on utilise le statut de facture
  // renvoyé par la liste (`order.invoiceStatus`).
  const getPrimaryAction = (order, invoice) => {
    switch (order?.status) {
      case 'PENDING':
        return {
          key: 'CONFIRM',
          label: t('orders.steps.confirmOrder'), shortLabel: t('orders.page.confirmShort'), icon: CheckCircle,
          // Couleur du statut d'arrivée : confirmer donne CONFIRMED (info).
          className: 'bg-blue-600 hover:bg-blue-700', onClick: () => handleConfirmOrder(order)
        };
      case 'CONFIRMED':
        return {
          key: 'INVOICE',
          label: t('orders.page.invoiceOrder'), shortLabel: t('orders.page.invoiceShort'), icon: Euro,
          className: 'bg-violet-600 hover:bg-violet-700', onClick: () => handleInvoiceOrder(order)
        };
      case 'INVOICED': {
        // Facture payée (ou annulée) : plus rien à encaisser, pas de bouton.
        if (paymentSettled(order, invoice)) return null;
        // Si un acompte a déjà été payé, on ne parle plus d'encaisser la facture mais de payer
        // le reste. Le libellé change en conséquence.
        const partial = (invoice?.status ?? order?.invoiceStatus) === 'PARTIALLY_PAID';
        return {
          key: 'PAY',
          label: partial ? t('orders.page.settleBalance') : t('orders.page.recordPayment'),
          shortLabel: partial ? t('orders.page.balanceShort') : t('orders.page.paymentShort'),
          icon: CreditCard,
          className: 'bg-green-600 hover:bg-green-700', onClick: () => handleOpenPayment(order)
        };
      }
      default:
        return null;
    }
  };

  // Règles métier (les mêmes que côté backend) :
  //  - modification seulement en brouillon (OrderService.updateOrder demande PENDING) ;
  //  - annulation possible tant que la commande n'est pas livrée ou déjà annulée,
  //    ET qu'elle n'a pas de facture active.
  const canEditOrder = (order) => order?.status === 'PENDING';

  // Annulation : mêmes conditions que OrderService.cancelOrder, pour ne jamais afficher un
  // bouton qui finirait en erreur 400 :
  //  1. on ne peut passer à CANCELED que depuis PENDING / CONFIRMED / INVOICED
  //     (DELIVERED et CANCELED sont des états finaux) ;
  //  2. une commande facturée ne peut être annulée qu'après avoir annulé sa facture. Donc
  //     une commande payée ou facturée en attente de paiement n'a pas le bouton : il faut
  //     d'abord annuler la facture.
  // `invoice` est optionnelle : sinon on lit le statut de facture renvoyé par la liste.
  const canCancelOrder = (order, invoice = null) => {
    if (!['PENDING', 'CONFIRMED', 'INVOICED'].includes(order?.status)) return false;
    if (order?.status !== 'INVOICED') return true;
    return (invoice?.status ?? order?.invoiceStatus) === 'CANCELED';
  };
  // Une commande est concernée par le paiement dès qu'elle est facturée : c'est ce qui
  // déclenche le chargement de la facture et l'affichage de la partie paiement dans le détail
  // (qui peut afficher « réglée » ou un reste à payer).
  const canPayOrder = (order) => ['INVOICED', 'DELIVERED'].includes(order?.status);

  // Le statut de paiement est sur la facture. La liste /orders en renvoie une copie
  // (`order.invoiceStatus`), ce qui permet de cacher le bouton sans attendre de chargement.
  const paymentSettled = (order, invoice) => {
    const status = invoice?.status ?? order?.invoiceStatus;
    return status === 'PAID' || status === 'CANCELED';
  };

  // Bouton « Paiement » affiché seulement si la commande est facturée et pas encore payée / annulée.
  const canRecordPayment = (order, invoice) =>
    canPayOrder(order) && !paymentSettled(order, invoice);

  // Est-ce qu'il reste une étape à faire ? Ça décide si on propose d'ouvrir le panier depuis
  // le détail. Proposer de « continuer » une commande terminée ne mènerait à rien et ferait
  // croire au caissier qu'il a oublié quelque chose.
  //
  // Sont terminées : la commande annulée, la commande facturée et payée en entier, et la
  // commande livrée sans reste à payer. Par contre une facture annulée ne termine rien : il
  // faut encore annuler la commande (voir canCancelOrder).
  //
  // `invoice` est optionnelle : sinon on lit le statut de facture de la liste. Si le statut
  // est inconnu, on considère qu'il reste quelque chose à faire (mieux vaut un bouton en trop
  // qu'une commande qu'on ne peut plus reprendre).
  const hasNextStep = (order, invoice = null) => {
    if (order?.status === 'CANCELED') return false;
    if (!canPayOrder(order)) return true; // PENDING / CONFIRMED : confirmation ou facturation
    const invoiceStatus = invoice?.status ?? order?.invoiceStatus;
    if (invoiceStatus === 'PAID') return false;
    if (order?.status === 'DELIVERED' && !invoiceStatus) return false;
    return true;
  };

  // Génère et télécharge le PDF de la facture d'une commande. On passe `invoice` quand elle
  // est déjà chargée (détail, carte) pour éviter un appel ; depuis la liste on la cherche par
  // commande. Tous les endpoints /invoices renvoient le même InvoiceResponse, avec la
  // commande, le client et les lignes, donc tout ce qu'il faut pour le PDF.
  const handleDownloadInvoice = async (order, invoice = null) => {
    if (downloadingOrderId) return;
    setDownloadingOrderId(order.id);
    try {
      const fullInvoice = invoice ?? (await api.get(`/invoices/order/${order.id}`)).data;
      // Coordonnées de l'entreprise (en-tête + mentions légales), déjà chargées par
      // `useSettings`. Si elles manquent, on génère quand même le PDF avec les valeurs par
      // défaut.
      await generateInvoicePDF(fullInvoice, settings || {});
    } catch (error) {
      console.error('Error generating invoice PDF:', error);
      toast.error(error.response?.status === 404
        ? t('orders.page.noInvoiceForOrder')
        : t('orders.workspace.pdfError'));
    } finally {
      setDownloadingOrderId(null);
    }
  };

  const remainingOf = (invoice) =>
    Number(invoice?.remainingAmount ?? ((invoice?.totalAmount || 0) - (invoice?.paidAmount || 0))) || 0;

  // Le PDF est proposé dès qu'il y a eu un paiement : facture payée (PAID) ou payée en
  // partie (PARTIALLY_PAID). Le PDF montre l'état du paiement (bandeau « PARTIELLEMENT
  // PAYÉE », montant déjà payé et reste à payer), donc il sert de justificatif d'acompte et
  // on ne peut pas le confondre avec une facture payée.
  // Pas de PDF pour :
  //  - une facture annulée, qui n'a plus de valeur ;
  //  - une facture pas encore payée : ça relève de l'écran Factures, qui permet de la
  //    télécharger à tout moment, donc on ne perd rien.
  // Un reste à payer de zéro compte comme PAID : c'est la même règle que la partie paiement
  // du détail, qui accepte les arrondis au centime.
  const canDownloadInvoice = (order, invoice = null) => {
    if (!canPayOrder(order)) return false;
    const status = invoice?.status ?? order?.invoiceStatus;
    if (status === 'CANCELED') return false;
    if (status === 'PAID' || status === 'PARTIALLY_PAID') return true;
    return !!invoice && remainingOf(invoice) <= 0.001;
  };

  // Charge la facture de la commande puis ouvre le modal de paiement.
  const handleOpenPayment = async (order) => {
    try {
      const { data: invoice } = await api.get(`/invoices/order/${order.id}`);
      if (invoice.status === 'PAID') {
        toast(t('orders.page.invoiceAlreadyPaid'), { icon: 'ℹ️' });
        return;
      }
      if (invoice.status === 'CANCELED') {
        toast.error(t('orders.page.linkedInvoiceCanceled'));
        return;
      }
      setPaymentInvoice(invoice);
      setPaymentForm({
        amount: remainingOf(invoice).toFixed(2),
        paymentMethod: invoice.paymentMethod || 'CASH',
        paymentDate: new Date().toISOString().split('T')[0],
      });
      setShowPaymentModal(true);
    } catch (error) {
      if (error.response?.status === 404) {
        toast.error(t('orders.page.noInvoiceYet'));
      } else {
        console.error('Error loading invoice for payment:', error);
        toast.error(t('orders.page.invoiceLoadError'));
      }
    }
  };

  const handleSubmitPayment = async () => {
    const amount = parseFloat(paymentForm.amount);
    const remaining = remainingOf(paymentInvoice);
    if (!amount || amount <= 0) {
      toast.error(t('orders.page.enterValidAmount'));
      return;
    }
    // Petite marge pour les arrondis des flottants, pour accepter le montant exact.
    if (amount > remaining + 0.001) {
      toast.error(t('orders.steps.amountExceeds', { amount: formatCurrency(remaining) }));
      return;
    }
    try {
      setPaymentLoading(true);
      await api.patch(`/invoices/${paymentInvoice.id}/payment`, {
        amount,
        paymentMethod: paymentForm.paymentMethod,
        paymentDate: paymentForm.paymentDate,
      });
      toast.success(t('orders.page.paymentRecorded'));
      setShowPaymentModal(false);
      setPaymentInvoice(null);
      refreshOrders();
    } catch (error) {
      console.error('Error recording payment:', error);
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setPaymentLoading(false);
    }
  };

  const handleEditItemChange = (index, field, value) => {
    const updatedItems = [...editForm.orderItems];

    // Si le produit change, on met à jour le prix
    if (field === 'productId') {
      // Une ligne par produit : on refuse un produit déjà présent dans la commande.
      const isDuplicate = value &&
        editForm.orderItems.some((it, i) => i !== index && String(it.productId) === String(value));
      if (isDuplicate) {
        toast.error(t('orders.page.duplicateProduct'));
        return;
      }
      const selectedProduct = products.find(p => p.id === parseInt(value));
      if (selectedProduct) {
        updatedItems[index] = {
          ...updatedItems[index],
          productId: value,
          unitPrice: selectedProduct.sellingPrice
        };
      } else {
        updatedItems[index] = { ...updatedItems[index], [field]: value };
      }
    } else {
      updatedItems[index] = { ...updatedItems[index], [field]: value };
    }

    // Recalcule le total
    const newTotal = computeItemsTotal(updatedItems);

    setEditForm(prev => ({
      ...prev,
      orderItems: updatedItems,
      totalAmount: newTotal
    }));
  };

  const handleRemoveItemFromEdit = (index) => {
    const updatedItems = editForm.orderItems.filter((_, i) => i !== index);
    const newTotal = computeItemsTotal(updatedItems);

    setEditForm(prev => ({
      ...prev,
      orderItems: updatedItems,
      totalAmount: newTotal
    }));
  };

  const handleAddItemToEdit = () => {
    setEditForm(prev => ({
      ...prev,
      orderItems: [
        ...prev.orderItems,
        { productId: '', unitPrice: 0, discount: 0, quantity: 1 }
      ]
    }));
  };

  const handleDelete = async (order) => {
    if (window.confirm(t('orders.confirmDelete', { number: order.orderNumber }))) {
      try {
        await api.delete(`/orders/${order.id}`);
        toast.success(t('orders.deleteSuccess'));
        refreshOrders();
      } catch (error) {
        console.error('Error deleting order:', error);
        // Le serveur donne la raison du refus (par exemple un retour client lié) : on affiche
        // son message plutôt qu'un simple « échec », comme pour l'annulation.
        toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
      }
    }
  };

  // Seule l'icône est définie ici : le texte vient de `status.order.*`, utilisé aussi par
  // les badges des autres écrans.
  const getStatusBadge = (status) => {
    const icons = {
      PENDING: Clock,
      CONFIRMED: CheckCircle,
      DELIVERED: Truck,
      INVOICED: Euro,
      PARTIALLY_PAID: Euro,
      PAID: CheckCircle,
      CANCELED: XCircle
    };
    const key = icons[status] ? status : 'PENDING';
    const Icon = icons[key];
    return (
      <span className={badgeClass(ORDER_STATUS_TONE[key])}>
        <Icon className="w-3 h-3" aria-hidden="true" />
        {t(`status.order.${key}`)}
      </span>
    );
  };

  /** Date et heure au format de la langue choisie. */
  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString(t('export.locale'), {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  // Affiche une ligne de commande. Fonction à part pour la réutiliser dans chaque groupe de statut.
  const renderOrderRow = (order) => {
    const isHighlighted = selectedOrder?.id === order.id && showDetailsModal;
    return (
      <motion.tr
        key={order.id}
        id={`order-${order.id}`}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        onClick={() => handleViewDetails(order)}
        className={`transition-colors cursor-pointer ${
          isHighlighted
            ? 'bg-primary-50 dark:bg-primary-500/15 ring-2 ring-primary-400'
            : 'hover:bg-gray-50 dark:hover:bg-gray-700/40'
        }`}
      >
        <td className="px-4 py-3 whitespace-nowrap">
          <div className="flex items-center gap-2">
            <ShoppingCart className="w-4 h-4 text-gray-400 shrink-0" />
            <span className="font-medium text-gray-900 dark:text-gray-100">{order.orderNumber}</span>
          </div>
        </td>
        <td className="hidden 2xl:table-cell px-4 py-3 whitespace-nowrap">
          <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
            <Calendar className="w-4 h-4 text-gray-400 shrink-0" />
            <span>{formatDate(order.createdAt)}</span>
          </div>
        </td>
        {/* Le client passe sur deux lignes au lieu de prendre 266 px d'un coup : c'est la
            colonne la plus large, et la seule qu'on peut couper sans gêner la lecture. */}
        <td className="max-w-[11rem] px-4 py-3">
          {order.client ? (
            /* Société affichée sous le nom si elle existe : en B2B c'est souvent elle qu'on
               cherche, et ça aide à distinguer deux clients qui ont le même nom. */
            <div className="flex items-center gap-2">
              <User className="w-4 h-4 text-gray-400 shrink-0" />
              <div className="min-w-0">
                <p className="font-medium text-gray-900 dark:text-gray-100 break-words">
                  {order.client.firstName} {order.client.lastName}
                </p>
                {order.client.company && (
                  <p className="text-xs text-gray-500 dark:text-gray-400 break-words">{order.client.company}</p>
                )}
              </div>
            </div>
          ) : (
            <span className="badge-neutral">
              {t('orders.walkInClient')}
            </span>
          )}
        </td>
        <td className="hidden min-[1720px]:table-cell px-4 py-3 whitespace-nowrap">
          {/* Nombre d'articles = somme des quantités, pas nombre de lignes : c'est ce qui est
              vraiment sorti du stock. */}
          <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
            <Package className="w-4 h-4 text-gray-400 shrink-0" />
            <span className="tabular-nums">
              {(order.items || []).reduce((n, i) => n + (parseInt(i.quantity) || 0), 0)}
            </span>
          </div>
        </td>
        <td className="px-4 py-3 whitespace-nowrap">
          {/* Total TTC, le montant à payer : celui de la facture dès qu'elle existe, donc le
              même que sur l'écran Factures. Avant on affichait le sous-total HT avant remise,
              qui ne correspondait ni à la facture ni au montant après remise. */}
          {(() => {
            const { amount, estimated } = orderPayableAmount(order, listTaxRate);
            // Ligne en dessous : le type de montant puis la remise, seulement s'il y en a.
            const notes = [
              estimated ? t('orders.page.amountEstimated') : null,
              (parseFloat(order.discount) || 0) > 0
                ? t('orders.page.discountAmount', { amount: formatCurrency(order.discount) })
                : null,
            ].filter(Boolean);
            return (
              <div className="flex flex-col">
                <span
                  className="subsection-title tabular-nums"
                  title={estimated
                    ? t('orders.page.amountEstimatedHint', { rate: Number(listTaxRate || 0).toFixed(2) })
                    : undefined}
                >
                  {formatCurrency(amount)}
                </span>
                {notes.length > 0 && (
                  <span className="text-xs text-gray-500 dark:text-gray-400 tabular-nums">
                    {notes.join(' · ')}
                  </span>
                )}
              </div>
            );
          })()}
        </td>
        <td className="px-4 py-3 whitespace-nowrap">
          {/* La liste /orders renvoie le statut de la facture : une commande facturée et payée
              affiche « Payée », et « Acompte versé » si elle est payée en partie. */}
          {getStatusBadge(resolveOrderStatusKey(order))}
          {/* Affiché sous le badge tant que la colonne « Progression » est cachée : la barre
              reste visible à toutes les tailles sans prendre une colonne de 160 px. */}
          <div className="mt-2 min-[1720px]:hidden">
            <OrderProgress order={order} compact />
          </div>
        </td>
        <td className="hidden min-[1720px]:table-cell px-4 py-3 whitespace-nowrap">
          {/* Le badge donne l'état actuel, la barre montre où en est la commande et ce qu'il
              reste à faire. Les deux se complètent. */}
          <OrderProgress order={order} />
        </td>
        <td className="px-3 py-3 whitespace-nowrap text-sm">
          {/* Boutons : l'action principale (étape suivante PENDING→Confirmer→Facturer→Livrer)
              + paiement, puis les actions secondaires en icônes (détail, modifier, annuler,
              supprimer), séparées par un trait. */}
          <div className="flex items-center justify-end gap-1">
            {(() => {
              const primary = getPrimaryAction(order);
              if (!primary) return null;
              const Icon = primary.icon;
              return (
                <button
                  onClick={(e) => { e.stopPropagation(); primary.onClick(); }}
                  className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 text-white text-xs font-semibold rounded-lg shadow-sm hover:shadow transition-all ${primary.className}`}
                  title={primary.label}
                >
                  <Icon className="w-3.5 h-3.5" />
                  {primary.shortLabel}
                </button>
              );
            })()}

            {/* Commande livrée mais facture pas entièrement payée : pas d'action principale,
                donc on propose le paiement ici. Même libellé que le bouton principal
                (« Solde » si un acompte a déjà été payé). */}
            {canRecordPayment(order) && getPrimaryAction(order)?.key !== 'PAY' && (
              <button
                onClick={(e) => { e.stopPropagation(); handleOpenPayment(order); }}
                className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-green-700 bg-green-50 hover:bg-green-100 text-xs font-semibold rounded-lg border border-green-200 transition-colors"
                title={order.invoiceStatus === 'PARTIALLY_PAID'
                  ? t('orders.page.settleBalance')
                  : t('orders.page.recordPayment')}
              >
                <CreditCard className="w-3.5 h-3.5" />
                {order.invoiceStatus === 'PARTIALLY_PAID'
                  ? t('orders.page.balanceShort')
                  : t('orders.page.paymentShort')}
              </button>
            )}

            <span className="mx-0.5 h-5 w-px bg-gray-200" aria-hidden="true" />

            <IconAction icon={Eye} title={t('orders.page.viewDetail')} color="neutral" onClick={() => handleViewDetails(order)} />

            {canDownloadInvoice(order) && (
              <IconAction
                icon={Download}
                title={downloadingOrderId === order.id
                  ? t('orders.steps.generatingPdf')
                  : t('orders.steps.downloadInvoicePdf')}
                color="neutral"
                disabled={downloadingOrderId === order.id}
                onClick={() => handleDownloadInvoice(order)}
              />
            )}

            {canEditOrder(order) && (
              <IconAction icon={Edit} title={t('orders.page.editOrder')} color="info" onClick={() => handleEdit(order)} />
            )}

            {canCancelOrder(order) && (
              <IconAction icon={XCircle} title={t('orders.steps.cancelOrder')} color="warning" onClick={() => handleCancelOrder(order)} />
            )}

            {order.status === 'CANCELED' && (
              <IconAction icon={Trash2} title={t('common.delete')} color="danger" onClick={() => handleDelete(order)} />
            )}
            {/* DELIVERED : état final, on peut seulement consulter (et payer s'il reste un montant). */}
          </div>
        </td>
      </motion.tr>
    );
  };

  // ---------------------------------------------------------------------------------------
  // Recherche, tri, pagination
  //
  // Tout est fait EN BASE par GET /orders/search : l'écran ne reçoit qu'une page.
  // Les filtres sont donc envoyés en paramètres de requête (voir `queryParams` plus bas)
  // au lieu d'être appliqués sur une liste complète.
  //
  // Pour ajouter un filtre, il faut modifier les deux côtés : le champ dans `OrderFilters`,
  // la clé dans `EMPTY_ORDER_FILTERS`, le paramètre dans `queryParams` et la condition dans
  // `OrderService.buildFilter`. Le faire seulement ici ne filtrerait que la page reçue.
  // ---------------------------------------------------------------------------------------

  // Options des filtres, renvoyées par /orders/filter-options. Si on les tirait de `orders`,
  // on n'aurait que celles de la page affichée et un filtre disparaîtrait en changeant de
  // page. On passe par cet endpoint plutôt que /users, qui est réservé à l'ADMIN alors que ce
  // filtre sert aussi au caissier.
  const orderUsers = filterOptions.operators;
  const orderCities = filterOptions.cities;

  // Si un filtre est actif on affiche toute la liste : filtrer seulement les six premières
  // n'aurait pas de sens (et les cartes de stats sont elles-mêmes des filtres de statut).
  const hasActiveFilters = useMemo(
    () => Object.keys(EMPTY_ORDER_FILTERS).some((key) => filters[key] !== EMPTY_ORDER_FILTERS[key]),
    [filters],
  );
  const showFullList = hasActiveFilters || viewMode === 'all';

  // Debounce sur les deux champs texte, sinon chaque caractère partirait au serveur. Les
  // autres filtres sont des listes ou des dates, choisis en une fois.
  const [debouncedQ, setDebouncedQ] = useState('');
  const [debouncedNotes, setDebouncedNotes] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQ(filters.q.trim());
      setDebouncedNotes(filters.notes.trim());
    }, 300);
    return () => clearTimeout(timer);
  }, [filters.q, filters.notes]);

  /**
   * Paramètres envoyés au serveur.
   *
   * La vue « dernières commandes » est maintenant une vraie requête : la première page triée
   * par date décroissante.
   */
  const queryParams = useMemo(() => {
    if (!showFullList) {
      return { page: 0, size: RECENT_COUNT, sort: 'createdAt,desc' };
    }
    const params = {
      page: page - 1,
      size: perPage,
      sort: `${SORT_FIELDS[sort.key] ?? 'createdAt'},${sort.dir}`,
    };
    if (debouncedQ) params.q = debouncedQ;
    if (debouncedNotes) params.notes = debouncedNotes;
    if (filters.status !== 'ALL') params.status = filters.status;
    // « Pas encore facturée » veut dire pas de facture active, ce n'est pas un statut de facture.
    if (filters.payment === 'NONE') params.notInvoiced = true;
    else if (filters.payment !== 'ALL') params.payment = filters.payment;
    if (filters.clientId) params.clientId = filters.clientId;
    if (filters.clientType !== 'ALL') params.clientType = filters.clientType;
    if (filters.city) params.city = filters.city;
    if (filters.productId) params.productId = filters.productId;
    if (filters.categoryId) params.categoryId = filters.categoryId;
    if (filters.createdById) params.createdById = filters.createdById;
    if (filters.dateFrom) params.dateFrom = filters.dateFrom;
    if (filters.dateTo) params.dateTo = filters.dateTo;
    if (filters.amountMin !== '') params.amountMin = filters.amountMin;
    if (filters.amountMax !== '') params.amountMax = filters.amountMax;
    if (filters.onlyDiscounted) params.onlyDiscounted = true;
    return params;
  }, [showFullList, page, perPage, sort, debouncedQ, debouncedNotes, filters]);

  // Le filtrage, le tri et la pagination sont faits en base : `orders` contient déjà la bonne
  // page dans le bon ordre. Refiltrer ou retrier ici ne porterait que sur les lignes reçues
  // et donnerait un faux tri.
  const displayedOrders = orders;
  const totalPages = pageMeta.totalPages;
  const currentPage = page;

  // Recharge la page quand un filtre, le tri ou le numéro de page change.
  useEffect(() => {
    fetchOrders();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryParams]);

  // Dès que les filtres changent on revient à la page 1 : rester en page 5 alors qu'il n'y en
  // a plus que 2 afficherait un tableau vide.
  useEffect(() => {
    setPage(1);
  }, [debouncedQ, debouncedNotes, filters, sort, perPage, viewMode]);

  const handleViewModeChange = (mode) => {
    setViewMode(mode);
    setPage(1);
    localStorage.setItem(VIEW_MODE_KEY, mode);
  };

  const toggleSort = (key) => {
    setSort((prev) => (
      prev.key === key
        ? { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { key, dir: key === 'createdAt' || key === 'totalAmount' ? 'desc' : 'asc' }
    ));
    setPage(1);
  };

  // Stats
  // Compteurs sur toutes les commandes, calculés en base (voir /api/orders/summary). Si on
  // les calculait sur `orders`, ils ne porteraient que sur la page. Et comme ces cartes
  // servent de filtres, afficher « 3 en attente » puis en montrer douze après le clic serait
  // assez déroutant.
  const stats = summary;

  // Cartes de stats cliquables (chacune filtre la liste sur son statut).
  // Chaque carte prend la couleur du statut qu'elle filtre (voir ORDER_STATUS_TONE).
  const kpiCards = [
    { key: 'ALL', label: t('orders.page.kpiTotal'), value: stats.total, icon: ShoppingCart, accent: 'success' },
    { key: 'PENDING', label: t('dashboard.status.pending'), value: stats.pending, icon: Clock, accent: ORDER_STATUS_TONE.PENDING },
    { key: 'CONFIRMED', label: t('dashboard.status.confirmed'), value: stats.confirmed, icon: CheckCircle, accent: ORDER_STATUS_TONE.CONFIRMED },
    { key: 'INVOICED', label: t('orders.page.kpiInvoiced'), value: stats.invoiced, icon: Euro, accent: ORDER_STATUS_TONE.INVOICED },
    { key: 'DELIVERED', label: t('dashboard.status.delivered'), value: stats.delivered, icon: Truck, accent: ORDER_STATUS_TONE.DELIVERED },
    { key: 'CANCELED', label: t('dashboard.status.canceled'), value: stats.canceled, icon: XCircle, accent: ORDER_STATUS_TONE.CANCELED }
  ];

  // Valeurs calculées du formulaire de modification (le client ne change pas, seuls les articles comptent).
  const editGrossTotal = editForm.orderItems.reduce(
    (sum, item) => sum + (parseFloat(item.unitPrice) || 0) * (parseInt(item.quantity) || 0), 0);
  const editDiscountTotal = editGrossTotal - editForm.totalAmount;
  const editItemCount = editForm.orderItems.length;
  // Mêmes contrôles qu'à la création : une commande PENDING avec une ligne qui dépasse le
  // stock serait enregistrée mais ne pourrait jamais être confirmée (confirmOrder lancerait
  // une InsufficientStockException). On bloque donc avant.
  const editHasStockIssue = editForm.orderItems.some((item) => {
    const product = products.find((p) => p.id === parseInt(item.productId));
    return product && parseInt(item.quantity) > product.stockQuantity;
  });
  const editHasInvalidQty = editForm.orderItems.some((item) => !(parseInt(item.quantity) > 0));
  const editFormValid =
    editItemCount > 0 &&
    editForm.orderItems.every((item) => item.productId) &&
    !editHasStockIssue &&
    !editHasInvalidQty;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <ShoppingCart aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('orders.pageTitle')}</h1>
            <p className="page-subtitle">
              {t('orders.page.subtitle')}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" icon={RefreshCw} onClick={fetchOrders} loading={loading}>
            {t('common.refresh')}
          </Button>
          <Button variant="primary" icon={Plus} onClick={() => openWorkspace()}>
            {t('orders.addOrder')}
          </Button>
        </div>
      </div>

      {/* ---- Niveau 1 : cartes de stats, qui servent aussi de filtres sur le statut ---- */}
      <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-3">
        {kpiCards.map((kpi, i) => {
          // Par défaut `info` et pas du gris : une valeur inconnue ne doit pas remettre une
          // carte grise dans la rangée.
          const accent = KPI_ACCENTS[kpi.accent] ?? KPI_ACCENTS.info;
          const active = filters.status === kpi.key;
          const Icon = kpi.icon;
          return (
            <motion.button
              key={kpi.key}
              type="button"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.04 }}
              onClick={() => handleFilterChange('status', kpi.key)}
              aria-pressed={active}
              className={`text-left rounded-2xl border p-4 transition-all hover:shadow-card-hover ${accent.tile} ${
                active ? `ring-2 ${accent.ring} border-transparent shadow-sm` : ''
              }`}
            >
              <div className="flex items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className={`text-[11px] font-semibold uppercase tracking-wider truncate ${accent.label}`}>{kpi.label}</p>
                  <p className={`text-2xl font-bold mt-1 tabular-nums ${accent.value}`}>{kpi.value}</p>
                </div>
                <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${accent.iconBg}`}>
                  <Icon className={`w-5 h-5 ${accent.iconText}`} />
                </div>
              </div>
            </motion.button>
          );
        })}
      </div>

      {/* ---- Niveau 2 : recherche et filtres ---- */}
      <OrderFilters
        filters={filters}
        onChange={handleFilterChange}
        onReset={resetFilters}
        expanded={filtersExpanded}
        onToggleExpanded={() => setFiltersExpanded((v) => !v)}
        clients={clients}
        products={products}
        categories={categories}
        users={orderUsers}
        cities={orderCities}
      />

      {/* ---- Niveau 3 : la liste, toujours visible ---- */}
      <div className="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-card overflow-hidden">
        {/* En-tête de la liste : ce qu'on affiche et combien. */}
        <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div>
            <h2 className="section-title">{t('orders.page.directoryTitle')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
              {showFullList
                ? t('orders.page.directoryAll')
                : t('orders.page.directoryPreview', { count: RECENT_COUNT })}
            </p>
          </div>
          {/* Le bouton de bascule disparaît quand un filtre est actif : la liste est alors
              forcément complète, et un bouton qui ne fait rien ferait penser à un bug. */}
          {!hasActiveFilters && orders.length > 0 && (
            <SegmentedFilter
              label={t('orders.page.viewModeLabel')}
              value={viewMode}
              onChange={handleViewModeChange}
              options={[
                { value: 'recent', label: t('orders.page.viewRecent', { count: RECENT_COUNT }) },
                { value: 'all', label: t('orders.page.viewAll'), count: orders.length },
              ]}
            />
          )}
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50/80 dark:bg-gray-900/40 border-b border-gray-200 dark:border-gray-700">
              {/*
                Les colonnes s'affichent selon la largeur de l'écran. Le tableau a huit
                colonnes et faisait 1643 px de large quelle que soit la fenêtre. Avec la place
                disponible (largeur moins le menu : 1126 px en 1440, 1052 px en 1366), la
                colonne « Actions » sortait de l'écran sous 1957 px, et il fallait scroller
                horizontalement pour la trouver, sans que rien ne l'indique.

                Donc les colonnes secondaires se cachent au fur et à mesure, comme dans
                `Table.jsx` : « Articles » et « Progression » apparaissent à partir de 1720 px,
                « Date & heure » à partir de 1536 px. En dessous, les cinq colonnes importantes
                (numéro, client, montant, statut, actions) tiennent dans l'écran.

                La barre de progression reste toujours visible : quand sa colonne est cachée,
                elle passe sous le badge de statut (voir la cellule « Statut »).
              */}
              <tr>
                <SortHeader label={t('orders.orderNumber')} sortKey="orderNumber" sort={sort} onSort={toggleSort} />
                <SortHeader label={t('orders.page.columnDateTime')} sortKey="createdAt" sort={sort} onSort={toggleSort} className="hidden 2xl:table-cell" />
                <SortHeader label={t('orders.client')} sortKey="client" sort={sort} onSort={toggleSort} />
                {/* Pas triable : c'est une somme des quantités, la base ne sait pas trier
                    dessus (voir SORT_FIELDS). */}
                <th className="hidden min-[1720px]:table-cell px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  {t('orders.items')}
                </th>
                <SortHeader label={t('orders.page.columnAmount')} sortKey="totalAmount" sort={sort} onSort={toggleSort} />
                <SortHeader label={t('orders.status')} sortKey="status" sort={sort} onSort={toggleSort} />
                {/* Pas triable : dépend du statut ET de la facture (voir SORT_FIELDS). */}
                <th className="hidden min-[1720px]:table-cell px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  {t('orders.page.columnProgress')}
                </th>
                <th scope="col" className="table-th-right px-3">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
              {loading ? (
                /* Lignes squelettes plutôt qu'un « Chargement… » au milieu : on voit la
                   structure du tableau et on ne confond pas avec « aucun résultat ». */
                Array.from({ length: 5 }).map((_, rowIndex) => (
                  <tr key={`skeleton-${rowIndex}`}>
                    {Array.from({ length: 8 }).map((__, cellIndex) => (
                      <td key={cellIndex} className="px-6 py-4">
                        <div className="skeleton h-4 w-full max-w-[10rem]" />
                      </td>
                    ))}
                  </tr>
                ))
              ) : displayedOrders.length === 0 ? (
                <tr>
                  <td colSpan={8}>
                    {/* Deux messages différents : « aucune commande » et « aucun résultat ».
                        Le second propose d'enlever les filtres. */}
                    <div className="text-center py-12">
                      <ShoppingCart className="empty-state-icon mb-4" />
                      {orders.length === 0 ? (
                        <>
                          <p className="text-gray-500 dark:text-gray-400">{t('orders.page.emptyNoOrders')}</p>
                          <button type="button" onClick={() => openWorkspace()} className="btn-primary mt-4">
                            <Plus className="w-4 h-4" />
                            {t('orders.page.createFirst')}
                          </button>
                        </>
                      ) : (
                        <>
                          <p className="text-gray-500 dark:text-gray-400">
                            {t('orders.page.emptyNoMatch')}
                          </p>
                          <button type="button" onClick={resetFilters} className="btn-secondary mt-4">
                            <RotateCcw className="w-4 h-4" />
                            {t('orders.page.resetFilters')}
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                displayedOrders.map((order) => renderOrderRow(order))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination seulement quand toute la liste est affichée (l'aperçu n'a que six
            lignes, une pagination n'aurait pas de sens). */}
        {showFullList && !loading && pageMeta.totalElements > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={pageMeta.totalElements}
            itemsPerPage={perPage}
            onPageChange={setPage}
            onItemsPerPageChange={(n) => { setPerPage(n); setPage(1); }}
          />
        )}
      </div>

      {/* Order Details Modal */}
      <AnimatePresence>
        {showDetailsModal && selectedOrder && (() => {
          /* Montants et conditions calculés une seule fois, pour avoir le même chiffre dans
             l'en-tête, le récapitulatif et les boutons. */
          const client = selectedOrder.client;
          const items = selectedOrder.items || [];
          const grossTotal = Number(selectedOrder.totalAmount || 0);
          const orderDiscount = Number(selectedOrder.discount || 0);
          const netTotal = orderNetAmount(selectedOrder);
          const canceledOrder = selectedOrder.status === 'CANCELED';
          const invoiceCanceled = detailInvoice?.status === 'CANCELED';
          const liveInvoice = detailInvoice && !invoiceCanceled ? detailInvoice : null;
          const paid = Number(liveInvoice?.paidAmount || 0);
          const invoiceRemaining = liveInvoice ? remainingOf(liveInvoice) : 0;
          const settled = !!liveInvoice && invoiceRemaining <= 0.001;
          // Une fois la facture créée, le montant de référence est le TTC (c'est lui qu'on
          // encaisse). Sans facture active, on affiche le HT.
          const headlineAmount = liveInvoice ? liveInvoice.totalAmount : netTotal;
          const primary = getPrimaryAction(selectedOrder, detailInvoice);
          const showPayment = canPayOrder(selectedOrder) && !!liveInvoice && !settled;
          const clientAddress = [
            client?.address,
            [client?.postalCode, client?.city].filter(Boolean).join(' '),
            client?.country,
          ].filter((part) => part && part.trim()).join(', ');

          return (
          <Modal
            isOpen={showDetailsModal}
            onClose={() => setShowDetailsModal(false)}
            title={t('orders.detailsTitle')}
            size="lg"
          >
            <div className="space-y-6">
              {/* ---- En-tête : quelle commande, quel montant, et où elle en est.
                   L'avancement est ici et pas en bas avec les boutons, car c'est la première
                   chose qu'on veut savoir en ouvrant une commande. ---- */}
              <header className="-mx-6 -mt-6 border-b border-gray-200 bg-gray-50 px-6 py-6 dark:border-gray-700 dark:bg-gray-900/40">
                <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between lg:gap-8">
                  <div className="min-w-0">
                    <h3 className="truncate text-xl font-bold tracking-tight text-gray-900 dark:text-gray-100">
                      {selectedOrder.orderNumber}
                    </h3>
                    <p className="mt-0.5 truncate text-sm text-gray-500 dark:text-gray-400">
                      {client
                        ? `${client.firstName || ''} ${client.lastName || ''}`.trim()
                        : t('orders.walkInClient')}
                    </p>
                    <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-2">
                      {/* Statut réel, selon le paiement de la facture : « Payée » une fois tout
                          payé, « Acompte versé » tant qu'il reste un montant. */}
                      {getStatusBadge(resolveOrderStatusKey(selectedOrder, detailInvoice))}
                      <span className="inline-flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                        <Calendar className="h-3.5 w-3.5" aria-hidden="true" />
                        {formatDate(selectedOrder.createdAt)}
                      </span>
                      {items.length > 0 && (
                        <span className="text-xs text-gray-400 dark:text-gray-500">
                          {t('orders.page.lineCount', { count: items.length })}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="lg:flex-shrink-0 lg:text-right">
                    <p className="text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">
                      {liveInvoice ? t('orders.totalInclTax') : t('orders.totalExclTax')}
                    </p>
                    <p className="mt-1 text-2xl font-bold tabular-nums text-gray-900 dark:text-gray-100">
                      {formatCurrency(headlineAmount)}
                    </p>
                    {showPayment && (
                      <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                        {t('orders.remainingDue')} · {formatCurrency(invoiceRemaining)}
                      </p>
                    )}
                  </div>
                </div>

                <div className="mt-6">
                {canceledOrder ? (
                  <div className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2.5 text-sm font-medium text-red-700 dark:bg-red-500/10 dark:text-red-300">
                    <XCircle className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
                    {t('orders.steps.canceledNotice')}
                  </div>
                ) : (
                  (() => {
                    const currentIndex = lifecycleIndexFor(selectedOrder.status, detailInvoice);
                    // Comme dans la liste : en cas de paiement partiel, l'étape en cours
                    // s'appelle « Payée » mais ce n'est pas encore le cas. On affiche l'état réel.
                    const partiallyPaid =
                      (detailInvoice?.status ?? selectedOrder.invoiceStatus) === 'PARTIALLY_PAID';
                    return (
                      <ol className="flex items-center">
                        {LIFECYCLE_STEPS.map((step, idx) => {
                          const done = idx < currentIndex;
                          const current = idx === currentIndex;
                          const label = current && partiallyPaid
                            ? t('status.order.PARTIALLY_PAID')
                            : t(step.labelKey);
                          return (
                            <li key={step.key} className={`flex items-center ${idx < LIFECYCLE_STEPS.length - 1 ? 'flex-1' : ''}`}>
                              <div className="flex flex-col items-center">
                                <span
                                  aria-hidden="true"
                                  className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold ${
                                    done ? 'bg-green-600 text-white'
                                    : current ? 'bg-blue-600 text-white ring-4 ring-blue-100 dark:ring-blue-500/25'
                                    : 'bg-gray-200 text-gray-500 dark:bg-gray-700 dark:text-gray-400'}`}
                                >
                                  {done ? <CheckCircle className="h-4 w-4" /> : idx + 1}
                                </span>
                                <span className={`mt-1.5 whitespace-nowrap text-xs ${
                                  current
                                    ? 'font-semibold text-blue-700 dark:text-blue-300'
                                    : 'text-gray-500 dark:text-gray-400'}`}
                                >
                                  {label}
                                </span>
                              </div>
                              {idx < LIFECYCLE_STEPS.length - 1 && (
                                <div className={`mx-2 h-0.5 flex-1 ${
                                  idx < currentIndex ? 'bg-green-600' : 'bg-gray-200 dark:bg-gray-700'}`}
                                />
                              )}
                            </li>
                          );
                        })}
                      </ol>
                    );
                  })()
                )}
                </div>
              </header>

              {/* ---- Client ---- */}
              <section className="space-y-3">
                <h4 className="subsection-title flex items-center gap-2">
                  <User className="h-4 w-4 text-gray-400" aria-hidden="true" />
                  {t('orders.clientInfoTitle')}
                </h4>
                {client ? (
                  /* Bloc compact, le même que dans le détail d'une facture : le client comme
                     sur une enveloppe, avec ses coordonnées pour le contacter. */
                  <div className="flex flex-wrap items-start justify-between gap-4 rounded-xl border border-gray-200 px-4 py-3 dark:border-gray-700">
                    <div className="min-w-0 space-y-1">
                      <p className="flex flex-wrap items-center gap-2">
                        <span className="truncate font-semibold text-gray-900 dark:text-gray-100">
                          {`${client.firstName || ''} ${client.lastName || ''}`.trim()}
                        </span>
                        {client.company && <span className="badge-accent">{client.company}</span>}
                      </p>
                      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-gray-600 dark:text-gray-400">
                        {client.email && (
                          <span className="inline-flex items-center gap-1.5">
                            <Mail className="h-3.5 w-3.5 text-gray-400" aria-hidden="true" />
                            {client.email}
                          </span>
                        )}
                        {client.phone && (
                          <span className="inline-flex items-center gap-1.5">
                            <Phone className="h-3.5 w-3.5 text-gray-400" aria-hidden="true" />
                            {client.phone}
                          </span>
                        )}
                      </p>
                      {clientAddress && (
                        <p className="flex items-start gap-1.5 text-sm text-gray-600 dark:text-gray-400">
                          <MapPin className="mt-0.5 h-3.5 w-3.5 flex-shrink-0 text-gray-400" aria-hidden="true" />
                          {clientAddress}
                        </p>
                      )}
                    </div>
                    <div className="flex flex-shrink-0 flex-wrap gap-2">
                      {client.email && (
                        <a href={`mailto:${client.email}`} className="quick-action">
                          <Mail className="h-4 w-4" aria-hidden="true" />
                          {t('clients.quickEmail')}
                        </a>
                      )}
                      {client.phone && (
                        <a href={`tel:${client.phone}`} className="quick-action">
                          <Phone className="h-4 w-4" aria-hidden="true" />
                          {t('clients.quickCall')}
                        </a>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="flex flex-wrap items-center gap-3 rounded-xl border border-dashed border-gray-300 px-4 py-3 dark:border-gray-600">
                    <span className="badge-neutral">{t('orders.walkInClient')}</span>
                    <span className="text-sm text-gray-500 dark:text-gray-400">{t('orders.page.noClientInfo')}</span>
                  </div>
                )}
              </section>

              {/* ---- Articles ---- */}
              {items.length > 0 && (
                <section className="space-y-3">
                  <h4 className="subsection-title flex items-center gap-2">
                    <Package className="h-4 w-4 text-gray-400" aria-hidden="true" />
                    {t('orders.orderItemsTitle')}
                    <span className="font-normal text-gray-400 dark:text-gray-500">
                      · {t('orders.page.lineCount', { count: items.length })}
                    </span>
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
                        {items.map((item, index) => (
                          <tr key={index} className="text-sm text-gray-700 dark:text-gray-300">
                            <td className="px-4 py-3">
                              {item.product?.name || item.productName || t('common.product')}
                            </td>
                            <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(item.unitPrice)}</td>
                            <td className="px-4 py-3 text-right tabular-nums">
                              {item.discount > 0 ? `${parseFloat(item.discount).toFixed(2)} %` : '—'}
                            </td>
                            <td className="px-4 py-3 text-right tabular-nums">{item.quantity}</td>
                            <td className="px-4 py-3 text-right font-semibold tabular-nums text-gray-900 dark:text-gray-100">
                              {formatCurrency(item.totalPrice ?? computeLineTotal(item))}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </section>
              )}

              {/* ---- Récapitulatif : aligné à droite sous les lignes, comme sur une facture.
                   Le paiement est aussi ici, c'est un montant donc il va avec les autres
                   montants et pas au milieu des boutons. ---- */}
              <section className="space-y-3">
                <h4 className="subsection-title">{t('invoices.sectionSummary')}</h4>
                <div className="divide-y divide-gray-100 rounded-xl border border-gray-200 sm:ml-auto sm:max-w-sm dark:divide-gray-700/60 dark:border-gray-700">
                  <AmountRow label={t('orders.subtotalExclTax')} value={formatCurrency(grossTotal)} />
                  {orderDiscount > 0 && (
                    <AmountRow
                      label={t('common.discount')}
                      value={`−${formatCurrency(orderDiscount)}`}
                      tone="text-red-600 dark:text-red-400"
                    />
                  )}
                  <AmountRow
                    label={t('orders.totalExclTax')}
                    value={formatCurrency(netTotal)}
                    emphasis={!liveInvoice}
                  />
                  {liveInvoice && (
                    <>
                      <AmountRow
                        label={t('orders.taxWithRate', { rate: Number(liveInvoice.taxRate || 0) })}
                        value={formatCurrency(liveInvoice.taxAmount)}
                      />
                      <div className="bg-gray-50 dark:bg-gray-900/40">
                        <AmountRow
                          label={t('orders.totalInclTax')}
                          value={formatCurrency(liveInvoice.totalAmount)}
                          emphasis
                        />
                      </div>
                      {paid > 0 && (
                        <AmountRow
                          label={t('invoices.alreadyPaid')}
                          value={formatCurrency(paid)}
                          tone="text-green-600 dark:text-green-400"
                        />
                      )}
                      <AmountRow
                        label={t('orders.remainingDue')}
                        value={formatCurrency(invoiceRemaining)}
                        emphasis
                      />
                    </>
                  )}
                </div>
                {!liveInvoice && !canceledOrder && (
                  <p className="text-xs text-gray-500 dark:text-gray-400 sm:text-right">
                    {t('orders.page.exclTaxNotice')}
                  </p>
                )}
              </section>

              {/* ---- Situation : les messages sur la commande. Les boutons sont regroupés en
                   bas de la fiche (avant ils étaient mélangés à ces messages, six boutons
                   pleine largeur avec des styles tous différents). ---- */}
              {(detailInvoiceLoading || settled || invoiceCanceled
                || (selectedOrder.status === 'DELIVERED' && (!primary || primary.key === 'PAY'))) && (
                <div className="space-y-2">
                  {detailInvoiceLoading && canPayOrder(selectedOrder) && (
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                      {t('orders.page.loadingPaymentStatus')}
                    </p>
                  )}

                  {invoiceCanceled && (
                    <div className="flex items-center gap-2 rounded-lg bg-gray-50 px-3 py-2.5 text-sm font-medium text-gray-600 dark:bg-gray-900/40 dark:text-gray-300">
                      <XCircle className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
                      {t('orders.steps.invoiceCanceledNotice')}
                    </div>
                  )}

                  {settled && (
                    <div className="flex items-center gap-2 rounded-lg bg-green-50 px-3 py-2.5 text-sm font-medium text-green-700 dark:bg-green-500/10 dark:text-green-300">
                      <CheckCircle className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
                      {t('orders.page.invoiceFullySettled', {
                        paid: formatCurrency(paid),
                        total: formatCurrency(Number(liveInvoice.totalAmount || 0)),
                      })}
                    </div>
                  )}

                  {selectedOrder.status === 'DELIVERED' && (!primary || primary.key === 'PAY') && (
                    <p className="flex items-center gap-2 text-sm font-medium text-green-700 dark:text-green-300">
                      <CheckCircle className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
                      {t('orders.page.deliveredDone')}
                    </p>
                  )}
                </div>
              )}

              {/* « Annuler » est caché tant que la facture est active (voir canCancelOrder),
                  il faut d'abord annuler la facture dans l'écran Factures. Ce message
                  s'affiche seulement dans ce cas, pour expliquer quoi faire. */}
              {selectedOrder.status === 'INVOICED' && !canCancelOrder(selectedOrder, detailInvoice) && (
                <p className="text-xs text-gray-400 dark:text-gray-500">
                  {t('orders.steps.cancelInvoiceFirst')}
                </p>
              )}

              {/* ---- Boutons collés en bas du modal : avec quelques articles le détail
                   dépasse la hauteur de l'écran, et l'étape suivante doit rester visible.
                   Ordre : fermer, puis documents, puis l'action qui fait avancer. ---- */}
              <div className="sticky -bottom-6 -mx-6 -mb-6 flex flex-wrap items-center gap-3 border-t border-gray-200 bg-white/95 px-6 py-4 backdrop-blur dark:border-gray-700 dark:bg-gray-800/95">
                <Button variant="secondary" onClick={() => setShowDetailsModal(false)}>
                  {t('common.close')}
                </Button>

                <div className="ml-auto flex flex-wrap items-center justify-end gap-3">
                  {/* L'écran Factures ouvre directement le détail grâce à `state.invoiceId`. */}
                  {detailInvoice && (
                    <Button
                      variant="secondary"
                      icon={FileText}
                      onClick={() => {
                        setShowDetailsModal(false);
                        navigate('/invoices', { state: { invoiceId: detailInvoice.id } });
                      }}
                    >
                      {t('orders.steps.viewInvoice', { number: detailInvoice.invoiceNumber })}
                    </Button>
                  )}

                  {/* Le PDF sert de justificatif pour le client : proposé dès qu'il y a une
                      facture utilisable, mais moins mis en avant que l'action principale. */}
                  {canDownloadInvoice(selectedOrder, detailInvoice) && (
                    <Button
                      variant="secondary"
                      icon={Download}
                      disabled={downloadingOrderId === selectedOrder.id}
                      onClick={() => handleDownloadInvoice(selectedOrder, detailInvoice)}
                    >
                      {downloadingOrderId === selectedOrder.id
                        ? t('orders.steps.generatingPdf')
                        : t('orders.steps.downloadInvoicePdf')}
                    </Button>
                  )}

                  {/* Le panier sert à continuer le traitement, le détail à voir où on en est.
                      Pour une commande terminée il n'y a plus rien à faire, donc le panier
                      n'est proposé que s'il reste une étape. */}
                  {hasNextStep(selectedOrder, detailInvoice) && (
                    <Button
                      variant="outline"
                      icon={ClipboardList}
                      onClick={() => { setShowDetailsModal(false); openWorkspace(selectedOrder); }}
                    >
                      {t('orders.page.continueInWorkspace')}
                    </Button>
                  )}

                  {/* Une seule action principale : encaisser s'il reste un montant à payer,
                      sinon l'étape suivante (confirmer, facturer). */}
                  {showPayment ? (
                    <Button
                      variant="success"
                      icon={CreditCard}
                      onClick={() => { setShowDetailsModal(false); handleOpenPayment(selectedOrder); }}
                    >
                      {paid > 0
                        ? t('orders.page.recordAdditionalPayment')
                        : t('orders.page.recordPayment')}
                    </Button>
                  ) : primary && primary.key !== 'PAY' && (
                    <Button
                      variant="primary"
                      icon={primary.icon}
                      onClick={() => { setShowDetailsModal(false); primary.onClick(); }}
                    >
                      {primary.label}
                    </Button>
                  )}
                </div>
              </div>
            </div>
          </Modal>
          );
        })()}
      </AnimatePresence>

      {/* Edit Order Modal */}
      <AnimatePresence>
        {showEditModal && selectedOrder && (
          <Modal
            isOpen={showEditModal}
            onClose={() => setShowEditModal(false)}
            title={t('orders.editOrderTitle', { number: selectedOrder.orderNumber })}
            size="fullscreen"
          >
            <div className="max-w-3xl mx-auto space-y-4">
              {/* Infos de la commande (lecture seule) + client, dans une seule carte compacte. */}
              <div className="bg-gray-50 p-4 rounded-xl border border-gray-200 space-y-3">
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div>
                    <p className="text-[11px] text-gray-400 font-semibold uppercase tracking-wide mb-0.5">{t('orders.orderNumber')}</p>
                    <p className="font-bold text-gray-900 text-sm">{selectedOrder.orderNumber}</p>
                  </div>
                  <div>
                    <p className="text-[11px] text-gray-400 font-semibold uppercase tracking-wide mb-0.5">{t('orders.date')}</p>
                    <p className="font-semibold text-gray-700 text-sm">{formatDate(selectedOrder.createdAt)}</p>
                  </div>
                  <div>
                    {/* Statut en lecture seule : il change via la confirmation, la facturation et la livraison. */}
                    <p className="text-[11px] text-gray-400 font-semibold uppercase tracking-wide mb-0.5">{t('orders.status')}</p>
                    {getStatusBadge(selectedOrder.status)}
                  </div>
                </div>

                <div className="pt-3 border-t border-gray-200">
                  <p className="text-[11px] text-gray-400 font-semibold uppercase tracking-wide mb-1">{t('orders.client')}</p>
                  {selectedOrder.client ? (
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                      <span className="inline-flex items-center gap-1 font-bold text-gray-900">
                        <User className="w-3.5 h-3.5 text-blue-600 shrink-0" />
                        {selectedOrder.client.firstName} {selectedOrder.client.lastName}
                      </span>
                      {selectedOrder.client.company && (
                        <span className="font-medium text-blue-600 bg-blue-100 px-1.5 py-0.5 rounded">
                          {selectedOrder.client.company}
                        </span>
                      )}
                      <span className="inline-flex items-center gap-1 text-gray-600 min-w-0">
                        <Mail className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                        <span className="truncate">{selectedOrder.client.email}</span>
                      </span>
                      {selectedOrder.client.phone && (
                        <span className="inline-flex items-center gap-1 text-gray-600">
                          <Phone className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                          {selectedOrder.client.phone}
                        </span>
                      )}
                      {selectedOrder.client.address && (
                        <span className="inline-flex items-center gap-1 text-gray-600 min-w-0">
                          <MapPin className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                          <span className="truncate">{selectedOrder.client.address}</span>
                        </span>
                      )}
                    </div>
                  ) : (
                    <span className="badge-neutral">
                      {t('orders.walkInClient')}
                    </span>
                  )}
                </div>
              </div>

              {/* Order Items */}
              <div className="bg-gray-50 p-4 rounded-xl border border-gray-200">
                <div className="flex items-center justify-between gap-3 mb-3">
                  <h3 className="subsection-title flex items-center gap-2">
                    <Package className="w-4 h-4 text-blue-600" /> {t('orders.items')}
                    {editItemCount > 0 && (
                      <span className="text-xs font-medium text-gray-400">· {editItemCount}</span>
                    )}
                  </h3>
                  <button
                    onClick={handleAddItemToEdit}
                    className="inline-flex items-center gap-1.5 px-2.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-lg transition-all shadow-sm hover:shadow-md"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    {t('common.add')}
                  </button>
                </div>

                {editForm.orderItems.length === 0 ? (
                  <button
                    type="button"
                    onClick={handleAddItemToEdit}
                    className="w-full flex flex-col items-center gap-1 py-6 bg-white rounded-lg border-2 border-dashed border-gray-300 hover:border-blue-400 hover:bg-blue-50/40 transition-colors group"
                  >
                    <Package className="w-6 h-6 text-gray-400 group-hover:text-blue-500 transition-colors" />
                    <p className="text-gray-500 text-xs">{t('orders.page.noItemClickToAdd')}</p>
                  </button>
                ) : (
                  <div className="space-y-2">
                    {/* En-tête des colonnes (desktop) : les labels ne sont affichés qu'ici. */}
                    <div className="hidden md:grid grid-cols-12 gap-2 px-2 text-[11px] font-semibold uppercase tracking-wide text-gray-400">
                      <span className="col-span-5">{t('common.product')}</span>
                      <span className="col-span-2 text-right">{t('orders.page.discountPercent')}</span>
                      <span className="col-span-2 text-right">{t('orders.recap.qtyShort')}</span>
                      <span className="col-span-2 text-right">{t('common.total')}</span>
                      <span className="col-span-1" />
                    </div>
                    {editForm.orderItems.map((item, index) => {
                      const selectedProduct = products.find(p => p.id === parseInt(item.productId));
                      const exceedsStock = selectedProduct && parseInt(item.quantity) > selectedProduct.stockQuantity;
                      return (
                        <div key={index} className="grid grid-cols-12 gap-2 items-start bg-white rounded-lg border border-gray-200 p-2">
                          {/* Produit + légende prix/stock */}
                          <div className="col-span-12 md:col-span-5 min-w-0">
                            <SearchableSelect
                              options={products}
                              value={item.productId}
                              onChange={(value) => handleEditItemChange(index, 'productId', value)}
                              getOptionValue={(product) => product.id}
                              getOptionLabel={(product) => product.name}
                              getOptionSearch={(product) => `${product.code || ''} ${product.barcode || ''}`}
                              placeholder={t('orders.page.searchProduct')}
                              noResultsText={t('orders.page.noProductFound')}
                              minChars={1}
                              inputClassName="w-full pl-9 pr-9 py-1.5 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition-all"
                              renderOption={(product) => (
                                <span className="flex items-center justify-between gap-2">
                                  <span className="flex flex-col min-w-0">
                                    <span className="font-medium truncate">{product.name}</span>
                                    <span className="text-xs text-gray-400 truncate">
                                      {t('orders.page.productCode', { code: product.code })}
                                      {product.barcode ? ` · ${product.barcode}` : ''}
                                    </span>
                                  </span>
                                  <span className="text-xs text-gray-500 shrink-0">
                                    {formatCurrency(product.sellingPrice)} · {product.stockQuantity} {product.unit}
                                  </span>
                                </span>
                              )}
                            />
                            {selectedProduct && (
                              <p className="mt-1 px-1 text-[11px] text-gray-400 truncate">
                                {t('orders.page.unitPriceAndStock', {
                                  price: formatCurrency(selectedProduct.sellingPrice),
                                  stock: selectedProduct.stockQuantity,
                                  unit: selectedProduct.unit || '',
                                })}
                              </p>
                            )}
                          </div>

                          {/* Remise */}
                          <div className="col-span-4 md:col-span-2">
                            <label className="md:hidden block text-[11px] font-semibold text-gray-500 mb-1">{t('orders.page.discountPercent')}</label>
                            <input
                              type="number"
                              min="0"
                              max="100"
                              step="0.01"
                              value={item.discount}
                              onChange={(e) => handleEditItemChange(index, 'discount', Math.min(Math.max(parseFloat(e.target.value) || 0, 0), 100))}
                              className="w-full px-2 py-1.5 bg-white border border-gray-300 rounded-lg text-sm font-semibold text-gray-900 text-right focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition-all"
                            />
                          </div>

                          {/* Quantité */}
                          <div className="col-span-4 md:col-span-2">
                            <label className="md:hidden block text-[11px] font-semibold text-gray-500 mb-1">{t('orders.recap.qtyShort')}</label>
                            <input
                              type="number"
                              min="1"
                              value={item.quantity}
                              onChange={(e) => handleEditItemChange(index, 'quantity', parseInt(e.target.value) || 1)}
                              className={`w-full px-2 py-1.5 bg-white border rounded-lg text-sm font-semibold text-gray-900 text-right focus:outline-none focus:ring-2 transition-all ${
                                exceedsStock
                                  ? 'border-red-400 focus:border-red-500 focus:ring-red-200'
                                  : 'border-gray-300 focus:border-blue-500 focus:ring-blue-200'
                              }`}
                            />
                            {exceedsStock && (
                              <p className="mt-1 text-[11px] font-medium text-red-600 text-right">
                                {t('orders.page.maxShort', { qty: selectedProduct.stockQuantity })}
                              </p>
                            )}
                          </div>

                          {/* Total ligne */}
                          <div className="col-span-3 md:col-span-2 text-right md:pt-1.5">
                            {parseFloat(item.discount) > 0 && (
                              <span className="block text-[11px] text-gray-400 line-through leading-tight">
                                {formatCurrency(item.unitPrice * item.quantity)}
                              </span>
                            )}
                            <span className="text-sm font-bold text-gray-900">
                              {formatCurrency(computeLineTotal(item))}
                            </span>
                          </div>

                          {/* Retirer */}
                          <div className="col-span-1 flex justify-end md:pt-1">
                            <button
                              onClick={() => handleRemoveItemFromEdit(index)}
                              className="p-1.5 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                              title={t('orders.cart.removeLine')}
                              aria-label={t('orders.cart.removeLine')}
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Récapitulatif compact : métadonnées à gauche, total à droite. */}
              <div className="bg-gray-50 p-4 rounded-xl border border-gray-200 flex items-end justify-between gap-4">
                <div className="text-xs text-gray-500 space-y-0.5">
                  <p>
                    {t('orders.page.lineCount', { count: editItemCount })}
                    {editDiscountTotal > 0.001 && (
                      <span className="text-red-600">
                        {' '}
                        {t('orders.page.discountDeducted', {
                          amount: formatCurrency(editDiscountTotal),
                        })}
                      </span>
                    )}
                  </p>
                  <p>{t('orders.page.exclTaxNotice')}</p>
                </div>
                <div className="text-right shrink-0">
                  <span className="block text-[11px] font-semibold uppercase tracking-wide text-gray-400">{t('orders.totalAmountLabel')}</span>
                  <span className="text-2xl font-black text-blue-600">
                    {formatCurrency(editForm.totalAmount)}
                  </span>
                </div>
              </div>

              {/* Message bloquant : explique pourquoi on ne peut pas enregistrer. */}
              {editHasStockIssue && (
                <div className="flex items-center gap-2 p-2.5 bg-red-50 text-red-700 rounded-lg text-xs font-medium border border-red-200">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  {t('orders.page.stockBlocking')}
                </div>
              )}

              {/* Actions */}
              <div className="flex flex-col-reverse sm:flex-row gap-2 sm:justify-end pt-1">
                <button
                  onClick={() => setShowEditModal(false)}
                  className="px-5 py-2 border-2 border-gray-300 text-gray-700 bg-white hover:bg-gray-100 rounded-lg font-semibold text-sm transition-all"
                >
                  {t('common.cancel')}
                </button>
                <button
                  onClick={handleUpdateOrder}
                  disabled={!editFormValid}
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-semibold text-sm transition-all flex items-center justify-center gap-2 shadow-md hover:shadow-lg disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-blue-600"
                >
                  <CheckCircle className="w-4 h-4" />
                  {t('common.saveChanges')}
                  {editForm.totalAmount > 0 && (
                    <span className="font-black">· {formatCurrency(editForm.totalAmount)}</span>
                  )}
                </button>
              </div>
            </div>
          </Modal>
        )}
      </AnimatePresence>

      {/* Panier : création de la commande et toute la suite au même endroit (articles,
          remises, validation, confirmation, facturation, encaissement, PDF). */}
      <OrderWorkspace
        isOpen={showWorkspace}
        onClose={() => setShowWorkspace(false)}
        initialOrder={workspaceOrder}
        products={products}
        clients={clients}
        categories={categories}
        onDataChanged={refreshAfterWorkspaceAction}
        onClientsChanged={fetchClients}
        onDetachOrder={() => setWorkspaceOrder(null)}
        onOpenInvoice={(invoice) => {
          setShowWorkspace(false);
          navigate('/invoices', { state: { invoiceId: invoice.id } });
        }}
      />

      {/* Invoice Modal : facturation directement ici, sans passer par la page Factures */}
      <AnimatePresence>
        {showInvoiceModal && invoiceOrder && (
          <Modal
            isOpen={showInvoiceModal}
            onClose={() => setShowInvoiceModal(false)}
            title={t('orders.page.invoiceOrderTitle', { number: invoiceOrder.orderNumber })}
          >
            {(() => {
              // Aperçu du calcul (comme dans InvoiceService) : HT − remise + TVA = TTC. Pas de
              // remise saisie ici (elle est sur les lignes de commande), donc elle vaut 0.
              const subtotal = Number(invoiceOrder.totalAmount || 0);
              const parsedRate = parseFloat(invoiceForm.taxRate);
              const rate = Number.isNaN(parsedRate) ? 0 : parsedRate;
              const taxAmount = subtotal * (rate / 100);
              const totalTTC = subtotal + taxAmount;
              const dueBeforeInvoice =
                invoiceForm.invoiceDate && invoiceForm.dueDate &&
                invoiceForm.dueDate < invoiceForm.invoiceDate;
              const rateInvalid = Number.isNaN(parsedRate) || rate < 0 || rate > 100;

              return (
                <form
                  onSubmit={(e) => { e.preventDefault(); handleSubmitInvoice(); }}
                  className="space-y-5"
                >
                  {/* Récapitulatif + aperçu du montant facturé (HT → TVA → TTC) */}
                  <div className="bg-gray-50 p-4 rounded-xl border border-gray-200 space-y-2 text-sm">
                    <div className="flex items-center justify-between">
                      <span className="text-gray-600 flex items-center gap-1.5">
                        <ShoppingCart className="w-4 h-4" /> {t('orders.page.orderWord')}
                      </span>
                      <span className="subsection-title">{invoiceOrder.orderNumber}</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-gray-600">{t('orders.page.amountExclTax')}</span>
                      <span className="subsection-title">{formatCurrency(subtotal)}</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-gray-600">
                        {rateInvalid
                          ? t('orders.page.taxUnknownRate')
                          : t('orders.taxWithRate', { rate: rate.toFixed(2) })}
                      </span>
                      <span className="subsection-title">{rateInvalid ? '—' : formatCurrency(taxAmount)}</span>
                    </div>
                    <div className="flex items-center justify-between border-t border-gray-200 pt-2 mt-1">
                      <span className="text-gray-800 font-semibold">{t('orders.page.totalToInvoiceInclTax')}</span>
                      <span className="text-base font-bold text-violet-700">{rateInvalid ? '—' : formatCurrency(totalTTC)}</span>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        {t('invoices.invoiceDateLabel')} <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="date"
                        autoFocus
                        required
                        value={invoiceForm.invoiceDate}
                        onChange={(e) => setInvoiceForm({ ...invoiceForm, invoiceDate: e.target.value })}
                        className="w-full px-3 py-2.5 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition-all"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        {t('orders.recap.dueOn')} <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="date"
                        required
                        min={invoiceForm.invoiceDate || undefined}
                        value={invoiceForm.dueDate}
                        onChange={(e) => setInvoiceForm({ ...invoiceForm, dueDate: e.target.value })}
                        className={`w-full px-3 py-2.5 bg-white border rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:ring-2 transition-all ${
                          dueBeforeInvoice
                            ? 'border-red-400 focus:border-red-500 focus:ring-red-200'
                            : 'border-gray-300 focus:border-blue-500 focus:ring-blue-200'
                        }`}
                      />
                      {dueBeforeInvoice && (
                        <p className="mt-1 text-xs text-red-600">
                          {t('orders.page.dueOnOrAfterInvoice')}
                        </p>
                      )}
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">{t('orders.page.plannedPaymentMethod')}</label>
                      <select
                        value={invoiceForm.paymentMethod}
                        onChange={(e) => setInvoiceForm({ ...invoiceForm, paymentMethod: e.target.value })}
                        className="w-full px-3 py-2.5 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition-all"
                      >
                        {PAYMENT_METHODS.map((m) => (
                          <option key={m.value} value={m.value}>{t(m.labelKey)}</option>
                        ))}
                      </select>
                      <p className="mt-1 text-xs text-gray-500">
                        {t('orders.page.noPaymentAtThisStage')}
                      </p>
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        {t('settings.taxRateLabel')} <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        max="100"
                        required
                        value={invoiceForm.taxRate}
                        onChange={(e) => setInvoiceForm({ ...invoiceForm, taxRate: e.target.value })}
                        className={`w-full px-3 py-2.5 bg-white border rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:ring-2 transition-all ${
                          rateInvalid
                            ? 'border-red-400 focus:border-red-500 focus:ring-red-200'
                            : 'border-gray-300 focus:border-blue-500 focus:ring-blue-200'
                        }`}
                        placeholder="0.00"
                      />
                      <p className="mt-1 text-xs text-gray-500">
                        {t('orders.page.taxAppliedToNet')}
                      </p>
                    </div>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">{t('orders.page.notesOptional')}</label>
                    <textarea
                      rows={2}
                      maxLength={500}
                      value={invoiceForm.notes}
                      onChange={(e) => setInvoiceForm({ ...invoiceForm, notes: e.target.value })}
                      className="w-full px-3 py-2.5 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition-all"
                      placeholder={t('orders.page.invoiceMentionPlaceholder')}
                    />
                  </div>

                  <div className="flex gap-3 justify-end pt-2">
                    <button
                      type="button"
                      onClick={() => setShowInvoiceModal(false)}
                      disabled={invoiceLoading}
                      className="px-6 py-2.5 border-2 border-gray-300 text-gray-700 bg-white hover:bg-gray-100 rounded-lg font-semibold transition-all disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      {t('common.cancel')}
                    </button>
                    <button
                      type="submit"
                      disabled={invoiceLoading || dueBeforeInvoice || rateInvalid}
                      className="px-6 py-2.5 bg-violet-600 hover:bg-violet-700 text-white rounded-lg font-semibold transition-all flex items-center gap-2 shadow-md hover:shadow-lg disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      <Euro className="w-5 h-5" />
                      {invoiceLoading ? t('orders.steps.creating') : t('orders.steps.createInvoice')}
                    </button>
                  </div>
                </form>
              );
            })()}
          </Modal>
        )}
      </AnimatePresence>

      {/* Payment Modal : paiement sur la facture de la commande */}
      <AnimatePresence>
        {showPaymentModal && paymentInvoice && (
          <Modal
            isOpen={showPaymentModal}
            onClose={() => setShowPaymentModal(false)}
            title={t('orders.page.recordPayment')}
          >
            {(() => {
              const remaining = remainingOf(paymentInvoice);
              const amountNum = parseFloat(paymentForm.amount);
              const amountEntered = paymentForm.amount !== '' && !Number.isNaN(amountNum);
              const amountValid = amountEntered && amountNum > 0 && amountNum <= remaining + 0.001;
              const exceeds = amountEntered && amountNum > remaining + 0.001;
              const newRemaining = amountValid ? Math.max(remaining - amountNum, 0) : remaining;
              const willSettle = amountValid && newRemaining <= 0.001;
              const today = new Date().toISOString().split('T')[0];

              return (
                <form
                  onSubmit={(e) => { e.preventDefault(); handleSubmitPayment(); }}
                  className="space-y-5"
                >
                  {/* Récapitulatif de la facture */}
                  <div className="bg-gray-50 p-4 rounded-xl border border-gray-200 space-y-2 text-sm">
                    <div className="flex items-center justify-between">
                      <span className="text-gray-600 flex items-center gap-1.5">
                        <FileText className="w-4 h-4" /> {t('invoices.sectionInvoice')}
                      </span>
                      <span className="subsection-title">{paymentInvoice.invoiceNumber}</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-gray-600">{t('orders.page.totalInvoiced')}</span>
                      <span className="subsection-title">{formatCurrency(paymentInvoice.totalAmount)}</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-gray-600">{t('invoices.paidAmount')}</span>
                      <span className="font-semibold text-green-600">{formatCurrency(paymentInvoice.paidAmount)}</span>
                    </div>
                    <div className="flex items-center justify-between pt-2 border-t border-gray-200">
                      <span className="text-gray-700 font-semibold">{t('orders.remainingDue')}</span>
                      <span className="text-lg font-black text-amber-600">{formatCurrency(remaining)}</span>
                    </div>
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="block text-sm font-medium text-gray-700">
                        {t('orders.steps.amountReceived')} <span className="text-red-500">*</span>
                      </label>
                      <button
                        type="button"
                        onClick={() => setPaymentForm({ ...paymentForm, amount: remaining.toFixed(2) })}
                        className="text-xs font-semibold text-green-700 hover:text-green-800 hover:underline"
                      >
                        {t('orders.page.payAllAmount', { amount: formatCurrency(remaining) })}
                      </button>
                    </div>
                    <div className="flex items-center gap-2">
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        max={remaining.toFixed(2)}
                        autoFocus
                        required
                        value={paymentForm.amount}
                        onChange={(e) => setPaymentForm({ ...paymentForm, amount: e.target.value })}
                        className={`w-full px-3 py-2.5 bg-white border rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:ring-2 transition-all ${
                          exceeds
                            ? 'border-red-400 focus:border-red-500 focus:ring-red-200'
                            : 'border-gray-300 focus:border-blue-500 focus:ring-blue-200'
                        }`}
                        placeholder="0.00"
                      />
                      <span className="text-gray-600">€</span>
                    </div>
                    {exceeds ? (
                      <p className="mt-1 text-xs text-red-600 flex items-center gap-1">
                        <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                        {t('orders.steps.amountExceeds', { amount: formatCurrency(remaining) })}
                      </p>
                    ) : amountValid ? (
                      <p className="mt-1 text-xs text-gray-500">
                        {willSettle
                          ? t('orders.page.paymentWillSettle')
                          : t('orders.page.remainingAfterPayment', {
                            amount: formatCurrency(newRemaining),
                          })}
                      </p>
                    ) : null}
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">{t('orders.page.paymentMethod')}</label>
                    <select
                      value={paymentForm.paymentMethod}
                      onChange={(e) => setPaymentForm({ ...paymentForm, paymentMethod: e.target.value })}
                      className="w-full px-3 py-2.5 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition-all"
                    >
                      {PAYMENT_METHODS.map((m) => (
                        <option key={m.value} value={m.value}>{t(m.labelKey)}</option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">{t('orders.page.paymentDate')}</label>
                    <input
                      type="date"
                      max={today}
                      value={paymentForm.paymentDate}
                      onChange={(e) => setPaymentForm({ ...paymentForm, paymentDate: e.target.value })}
                      className="w-full px-3 py-2.5 bg-white border border-gray-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition-all"
                    />
                  </div>

                  <div className="flex gap-3 justify-end pt-2">
                    <button
                      type="button"
                      onClick={() => setShowPaymentModal(false)}
                      disabled={paymentLoading}
                      className="px-6 py-2.5 border-2 border-gray-300 text-gray-700 bg-white hover:bg-gray-100 rounded-lg font-semibold transition-all disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      {t('common.cancel')}
                    </button>
                    <button
                      type="submit"
                      disabled={paymentLoading || !amountValid}
                      className="px-6 py-2.5 bg-green-600 hover:bg-green-700 text-white rounded-lg font-semibold transition-all flex items-center gap-2 shadow-md hover:shadow-lg disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      <CreditCard className="w-5 h-5" />
                      {paymentLoading ? t('orders.steps.saving') : t('orders.page.submitPayment')}
                    </button>
                  </div>
                </form>
              );
            })()}
          </Modal>
        )}
      </AnimatePresence>

    </div>
  );
};

export default Orders;
