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

// Enchaînement linéaire des étapes pilotées depuis ce tableau de bord (hors CANCELED, qui est un
// échappatoire). La livraison est gérée ailleurs ; en revanche le paiement fait partie intégrante
// du processus et figure désormais comme dernière étape (« Payée »). L'état de paiement n'est pas
// porté par la commande mais par la facture liée : il est donc déduit de celle-ci (cf.
// lifecycleIndexFor, qui reçoit la facture).
const LIFECYCLE_STEPS = [
  { key: 'PENDING', labelKey: 'status.order.PENDING' },
  { key: 'CONFIRMED', labelKey: 'status.order.CONFIRMED' },
  { key: 'INVOICED', labelKey: 'status.order.INVOICED' },
  { key: 'PAID', labelKey: 'status.order.PAID' }
];

// Position de la commande dans la timeline [En attente, Confirmée, Facturée, Payée], en croisant le
// statut de commande et l'état de la facture liée (source de vérité du paiement) :
//  - l'index pointe l'étape « courante » (en cours) ; les étapes d'index inférieur sont complétées ;
//  - un index >= LIFECYCLE_STEPS.length signifie « toutes les étapes franchies » (processus terminé).
// Règles :
//  - facturée + intégralement réglée → toutes les étapes complétées ;
//  - facturée + paiement partiel → étape « Payée » en cours ;
//  - facturée + impayée → étape « Facturée » en cours, « Payée » à venir ;
//  - livrée → processus considéré complet (réglé), sauf facture connue encore impayée.
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

// Le statut affiché est résolu par `constants/statusBadges.resolveOrderStatusKey`, partagé avec
// la caisse et le tableau de bord. Cet écran en tenait sa propre version, qui ne reconnaissait
// que le règlement intégral : une commande partiellement réglée s'affichait « Facturée » ici et
// « Acompte versé » à la caisse, pour la même donnée.

/**
 * Avancement d'une commande dans son cycle de vie, en barre à segments.
 *
 * Forme choisie : quatre segments côte à côte, un par étape du cycle
 * [En attente → Confirmée → Facturée → Payée]. C'est la représentation adéquate ICI, dans une
 * cellule de tableau répétée sur des dizaines de lignes :
 *   - un anneau de progression (`components/ProgressRing`) demande ~40 px de haut et ferait
 *     doubler la hauteur de chaque ligne, alors qu'il n'encode pas davantage ;
 *   - une barre continue dirait « 75 % » sans dire de quoi — or les étapes sont discrètes et
 *     nommées, pas un pourcentage ;
 *   - une courbe n'a pas de sens : il n'y a pas de série temporelle, mais une position dans
 *     une file d'étapes.
 * Quatre segments alignés se lisent d'un coup d'œil sur toute la colonne, et la comparaison
 * entre lignes se fait sans lire — c'est ce qu'on demande à un tableau.
 *
 * La couleur ne porte jamais seule l'information : la légende sous la barre nomme l'étape
 * courante et son rang (« 3/4 · Facturée »), et `aria-label` la reprend pour les lecteurs
 * d'écran. Les nuances suivent l'échelle monochrome de la charte (cf. src/index.css).
 *
 * L'avancement est calculé par `lifecycleIndexFor`, qui croise le statut de la commande et
 * celui de sa facture — le paiement n'est pas porté par la commande. Dans la liste, cette
 * facture se résume à `order.invoiceStatus`.
 */
const OrderProgress = ({ order }) => {
  const { t } = useTranslation();
  const index = lifecycleIndexFor(order.status, { status: order.invoiceStatus });
  const canceled = index < 0;
  const done = index >= LIFECYCLE_STEPS.length;

  // La dernière étape s'intitule « Payée » : c'est son objectif, pas l'état atteint. Sur un
  // règlement partiel, la légende annonçait donc « 4/4 · Payée » pour une facture à moitié
  // réglée. On y nomme l'état réel, dans les mêmes termes que le badge de la colonne voisine.
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
    <div className="w-32" role="img" aria-label={t('orders.progress.regionAria', { detail: ariaLabel })}>
      <div className="flex items-center gap-1" aria-hidden="true">
        {LIFECYCLE_STEPS.map((step, i) => {
          // Trois états par segment : franchi (dense), en cours (médian), à venir (piste).
          // Une commande annulée n'a plus d'étape en cours : tout retombe en piste neutre.
          // Les crans sont choisis pour que « en cours » se détache à la fois du franchi et de
          // la piste, dans les deux thèmes : en sombre, un bleu trop foncé pour l'étape courante
          // se confondrait avec le gris bleuté de la piste.
          // `gray-500` et non `gray-600` en sombre : la piste est posée sur une carte
          // `gray-800`, et il faut deux crans d'écart pour qu'un filet de 6 px de haut se
          // détache du bleu nuit du fond.
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

// Palettes des cartes KPI, indexées par jeton (cf. section Tuiles d'indicateurs de index.css).
// Comme les tuiles du tableau de bord, ce bandeau suit l'échelle MONOCHROME de la charte et
// non les teintes sémantiques des badges : c'est la profondeur du bleu qui ordonne les cartes,
// dans l'ordre neutral < success < accent < info < warning < danger. Les badges de la colonne
// « Statut », eux, gardent vert / ambre / rouge — un bandeau porte l'identité de l'écran, une
// ligne de tableau doit se lire sans réfléchir.
// Chaque carte est teintée en entier — surface, bordure, libellé, valeur, disque d'icône —
// et non plus blanche avec une seule icône colorée. Le jeton `neutral` a été retiré : aucune
// carte de ce bandeau ne doit tomber en gris, la teinte est ce qui rend le statut lisible
// d'un coup d'œil. Classes écrites en toutes lettres, sinon Tailwind les purge du build.
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

// Nombre de lignes par page par défaut. Doit rester une des valeurs proposées par le sélecteur
// de `components/Pagination` (5/10/20/50/100), sinon celui-ci s'affiche vide au chargement.
const ORDERS_PER_PAGE = 20;

/** Mémorise le mode d'affichage entre deux visites, comme les autres tableaux de bord. */
const VIEW_MODE_KEY = 'ordersViewMode';

/** Nombre de commandes mises en avant dans la vue d'aperçu (les dernières créées). */
const RECENT_COUNT = 5;

/**
 * En-tête de colonne triable.
 *
 * Déclaré au niveau du module et non dans le corps de `Orders` : un composant redéfini à
 * chaque rendu est démonté puis remonté par React, et le bouton perdrait le focus au clavier
 * juste après le clic qui a déclenché le tri.
 *
 * `aria-sort` porte au lecteur d'écran la même information que l'icône.
 */
/**
 * Colonnes triables, traduites en clé de tri pour le serveur.
 *
 * Deux colonnes ont perdu leur tri, et c'est délibéré : « Articles » est une somme des
 * quantités des lignes, « Avancement » se déduit du statut ET de celui de la facture. Ni l'un
 * ni l'autre n'est une colonne que la base sait ordonner, et les trier au sein de la seule page
 * affichée produirait un tableau qui se prétend trié sans l'être — le piège même que la
 * pagination serveur est censée fermer. Mieux vaut retirer l'affordance que mentir.
 *
 * `payableAmount` n'est pas non plus un champ : c'est le montant TTC reconstruit en SQL, que le
 * serveur sait ordonner parce qu'il pose l'ORDER BY sur l'expression elle-même. Le tri suit
 * donc exactement les chiffres de la colonne, y compris quand une facture change la donne.
 */
const SORT_FIELDS = {
  orderNumber: 'orderNumber',
  createdAt: 'createdAt',
  client: 'client.lastName',
  totalAmount: 'payableAmount',
  status: 'status',
};

const SortHeader = ({ label, sortKey, sort, onSort, align = 'left' }) => {
  const active = sort.key === sortKey;
  const Icon = !active ? ChevronsUpDown : sort.dir === 'asc' ? ChevronUp : ChevronDown;
  return (
    <th
      scope="col"
      aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
      className={align === 'right' ? 'table-th-right' : 'table-th'}
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

// Teintes des actions en icône, graduées par gravité et prises dans la palette sémantique :
// consultation neutre, modification informative, annulation en avertissement, suppression
// en danger. L'orange et l'émeraude d'origine sortaient de la palette et mettaient
// « Annuler » et « Supprimer » sur un pied d'égalité visuelle.
const ICON_ACTION_STYLES = {
  neutral: 'text-gray-600 hover:bg-gray-100',
  info: 'text-blue-600 hover:bg-blue-50',
  warning: 'text-amber-600 hover:bg-amber-50',
  danger: 'text-red-600 hover:bg-red-50',
};

// Bouton d'action secondaire (icône) de la barre d'actions d'une commande.
// `onClick` reçoit l'évènement : on stoppe la propagation pour ne pas déclencher
// le clic de la ligne (ouverture du détail). `disabled` couvre les actions asynchrones
// (génération de PDF) pour empêcher le double déclenchement.
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
  // Critères de recherche regroupés dans un seul objet : la liste en compte quinze, et un
  // `useState` par critère rendait impossible une réinitialisation ou un comptage global.
  const [filters, setFilters] = useState(EMPTY_ORDER_FILTERS);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  // Tri du tableau. `dir` bascule asc/desc sur la colonne déjà active.
  const [sort, setSort] = useState({ key: 'createdAt', dir: 'desc' });
  const [page, setPage] = useState(1);
  const [perPage, setPerPage] = useState(ORDERS_PER_PAGE);
  // Vue par défaut : seules les dernières commandes créées sont mises en avant. La liste
  // complète s'obtient par la bascule d'affichage — ou d'office dès qu'un filtre est actif.
  const [viewMode, setViewMode] = useState(() => localStorage.getItem(VIEW_MODE_KEY) || 'recent');
  // Cardinalité du résultat courant, renvoyée par le serveur : elle n'est plus déductible de
  // `orders`, qui ne porte que la page affichée.
  const [pageMeta, setPageMeta] = useState({ totalElements: 0, totalPages: 1 });
  // Décompte par statut des tuiles. Il décrit tout le périmètre de l'utilisateur, jamais la
  // page : les tuiles servent justement à filtrer, donc à changer de page.
  const [summary, setSummary] = useState({
    total: 0, pending: 0, confirmed: 0, invoiced: 0, delivered: 0, canceled: 0,
  });
  // Opérateurs et villes des listes déroulantes. Ils doivent rester exhaustifs : un opérateur
  // qui n'apparaît qu'en page 3 doit être proposé depuis la page 1.
  const [filterOptions, setFilterOptions] = useState({ operators: [], cities: [] });
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  // Panier de traitement : poste de travail unique du cycle de vie (articles → validation →
  // confirmation → facturation → encaissement → PDF). Ouvert vierge pour une nouvelle vente,
  // ou sur une commande existante pour en reprendre le cours là où il en est.
  const [showWorkspace, setShowWorkspace] = useState(false);
  const [workspaceOrder, setWorkspaceOrder] = useState(null);
  // Paiement d'une commande facturée : on encaisse sur la facture liée (GET /invoices/order/:id).
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [paymentInvoice, setPaymentInvoice] = useState(null);
  const [paymentLoading, setPaymentLoading] = useState(false);
  // Facture liée à la commande ouverte dans le modal de détail : sert à n'afficher l'action de
  // paiement que si un reliquat existe réellement (et à montrer un statut « réglée » sinon).
  const [detailInvoice, setDetailInvoice] = useState(null);
  const [detailInvoiceLoading, setDetailInvoiceLoading] = useState(false);
  // Commande dont le PDF de facture est en cours de génération (verrou anti double-clic + retour
  // visuel : le document est produit côté navigateur après un ou deux appels réseau).
  const [downloadingOrderId, setDownloadingOrderId] = useState(null);
  // Facturation en ligne : on crée la facture directement depuis ce tableau de bord (sans renvoi
  // vers la page Factures). La commande passe alors à INVOICED et l'action « Paiement » apparaît ici.
  const [showInvoiceModal, setShowInvoiceModal] = useState(false);
  const [invoiceOrder, setInvoiceOrder] = useState(null);
  const [invoiceLoading, setInvoiceLoading] = useState(false);
  // Taux de TVA et échéance viennent des réglages de l'entreprise, comme dans l'atelier de
  // commande : deux écrans qui facturent la même commande doivent proposer le même taux.
  const { settings, defaultTaxRate, defaultDueDate } = useSettings();
  // Taux retenu pour estimer le TTC des commandes pas encore facturées, dans la liste comme
  // dans les filtres et le tri : un seul et même chiffre pour les trois.
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

  // Modifier un critère renvoie systématiquement en page 1 : rester en page 4 d'un jeu de
  // résultats qui vient d'en perdre trois affiche une liste vide et se lit comme un bug.
  const handleFilterChange = (field, value) => {
    setFilters((prev) => ({ ...prev, [field]: value }));
    setPage(1);
  };

  const resetFilters = () => {
    setFilters(EMPTY_ORDER_FILTERS);
    setPage(1);
  };

  // Ouverture du panier de traitement. Sans commande : nouvelle vente. Avec commande : reprise
  // du traitement là où il en est (confirmation, facturation, encaissement).
  const openWorkspace = (order = null) => {
    setWorkspaceOrder(order);
    setShowWorkspace(true);
  };

  // Après une action du panier, la liste et le stock des produits ont bougé.
  const refreshAfterWorkspaceAction = () => {
    refreshOrders();
    fetchProducts();
  };

  // Référentiels et agrégats : chargés une fois, ils ne dépendent pas de la page affichée.
  // Le chargement de la page elle-même est déclaré plus bas, après `queryParams` dont il
  // dépend — le référencer ici le lirait avant son initialisation.
  useEffect(() => {
    fetchProducts();
    fetchClients();
    fetchCategories();
    fetchSummary();
    fetchFilterOptions();
  }, []);

  useEffect(() => {
    // Commande à ouvrir au chargement : soit ?orderId= dans l'URL (caisse, supervision — le
    // lien reste partageable et ouvrable dans un nouvel onglet), soit l'ancien relais par
    // localStorage encore utilisé par la page Rapports.
    const selectedOrderId = searchParams.get('orderId') || localStorage.getItem('selectedOrderId');
    if (selectedOrderId && orders.length > 0) {
      const order = orders.find(o => o.id === parseInt(selectedOrderId));
      if (order) {
        setSelectedOrder(order);
        setShowDetailsModal(true);
        // Scroll to the order in the list
        setTimeout(() => {
          const element = document.getElementById(`order-${selectedOrderId}`);
          if (element) {
            element.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }, 100);
      }
      // Consommé : on nettoie les deux canaux pour ne pas rouvrir le détail au rechargement.
      localStorage.removeItem('selectedOrderId');
      if (searchParams.has('orderId')) {
        setSearchParams({}, { replace: true });
      }
    }
  }, [orders, searchParams, setSearchParams]);

  // À l'ouverture du détail d'une commande facturable, on récupère sa facture pour connaître
  // l'état réel du paiement (réglée / reliquat / annulée) et adapter l'action proposée. On ignore
  // un 404 (pas encore de facture) : la section paiement reste alors masquée.
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

  // Catégories : on charge la liste complète (et non celles déduites des produits) afin que la
  // colonne de gauche du POS affiche aussi les catégories encore vides — des produits pourront y
  // être rattachés plus tard. On masque les catégories désactivées (active === false).
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
      // Un échec de chargement laisse la liste vide et le signale. L'écran retombait
      // auparavant sur un jeu de commandes fictives, que rien ne distinguait de vraies
      // ventes : l'application paraissait fonctionner alors que l'API était injoignable.
      console.error('Error fetching orders:', error);
      toast.error(t('orders.loadError'));
      setOrders([]);
    } finally {
      setLoading(false);
    }
  };

  /** Décompte des tuiles : à rafraîchir après toute écriture, un changement de statut le déplaçant. */
  const fetchSummary = async () => {
    try {
      const { data } = await api.get('/orders/summary');
      setSummary(data);
    } catch (error) {
      console.error('Error fetching order summary:', error);
    }
  };

  /** Options des filtres. Endpoint dédié : /users est réservé à l'ADMIN, pas ce périmètre-ci. */
  const fetchFilterOptions = async () => {
    try {
      const { data } = await api.get('/orders/filter-options');
      setFilterOptions(data);
    } catch (error) {
      console.error('Error fetching order filter options:', error);
    }
  };

  /** Recharge page, tuiles et options — à appeler après toute écriture sur une commande. */
  const refreshOrders = async () => {
    await Promise.all([fetchOrders(), fetchSummary(), fetchFilterOptions()]);
  };

  const handleViewDetails = (order) => {
    // Optimisation: Utiliser directement les données si disponibles
    setSelectedOrder(order);
    setShowDetailsModal(true);
  };

  const handleEdit = (order) => {
    // Une commande n'est modifiable qu'au stade brouillon (PENDING). Une fois confirmée,
    // ses lignes sont figées (elles ont sorti du stock et servent de base à la facture).
    if (order.status !== 'PENDING') {
      toast(t('orders.page.onlyPendingEditable'), { icon: 'ℹ️' });
      handleViewDetails(order);
      return;
    }
    setSelectedOrder(order);
    // Transformer les items pour avoir le bon format avec productId
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
      // Vérifier que tous les articles ont un produit sélectionné
      const hasEmptyProduct = editForm.orderItems.some(item => !item.productId);
      if (hasEmptyProduct) {
        toast.error(t('orders.selectProductForItem'));
        return;
      }

      if (editForm.orderItems.length === 0) {
        toast.error(t('orders.addAtLeastOneItem'));
        return;
      }

      // Garde de stock : éviter d'enregistrer une commande qui ne pourra pas être confirmée.
      if (editHasStockIssue) {
        toast.error(t('orders.page.linesExceedStock'));
        return;
      }

      // Transformer les items pour le backend (nouveau format DTO)
      const transformedItems = editForm.orderItems.map(item => ({
        productId: parseInt(item.productId),
        quantity: parseInt(item.quantity),
        discount: parseFloat(item.discount) || 0
      }));

      // Préparer les données pour la mise à jour. Le statut n'est plus envoyé : il est piloté
      // par les actions dédiées (confirmation, annulation) côté backend.
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

  // Étape suivante du cycle : facturation en ligne, sans quitter le tableau de bord. On ouvre un
  // modal pré-rempli (mêmes réglages par défaut que la page Factures) ; la création se fait via
  // l'API puis la liste est rafraîchie.
  const handleInvoiceOrder = (order) => {
    setInvoiceOrder(order);
    setInvoiceForm(blankInvoiceForm());
    setShowInvoiceModal(true);
  };

  const handleSubmitInvoice = async () => {
    if (!invoiceOrder) return;
    // Validations alignées sur le contrat backend (InvoiceCreateRequest) + cohérence métier :
    if (!invoiceForm.invoiceDate || !invoiceForm.dueDate) {
      toast.error(t('orders.page.invoiceDatesRequired'));
      return;
    }
    // L'échéance ne peut pas précéder l'émission de la facture.
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

  // Bouton principal : décrit l'unique « prochaine étape » cohérente du cycle de vie selon le
  // statut courant — Confirmer → Facturer → Encaisser. La livraison se gère ailleurs ; une commande
  // déjà livrée (DELIVERED), hors-flux (CANCELED) ou déjà soldée n'expose donc pas d'action
  // principale (null). La clé `key` permet d'éviter d'afficher en double l'action « Paiement »
  // là où elle est déjà proposée comme action secondaire (cf. canRecordPayment).
  // `invoice` est optionnelle : quand elle n'est pas chargée, on retombe sur le statut de facture
  // porté par la liste (`order.invoiceStatus`).
  const getPrimaryAction = (order, invoice) => {
    switch (order?.status) {
      case 'PENDING':
        return {
          key: 'CONFIRM',
          label: t('orders.steps.confirmOrder'), shortLabel: t('orders.page.confirmShort'), icon: CheckCircle,
          // Teinte du statut d'arrivée : confirmer mène à CONFIRMED (info).
          className: 'bg-blue-600 hover:bg-blue-700', onClick: () => handleConfirmOrder(order)
        };
      case 'CONFIRMED':
        return {
          key: 'INVOICE',
          label: t('orders.page.invoiceOrder'), shortLabel: t('orders.page.invoiceShort'), icon: Euro,
          className: 'bg-violet-600 hover:bg-violet-700', onClick: () => handleInvoiceOrder(order)
        };
      case 'INVOICED': {
        // Facture soldée (ou annulée) : plus rien à encaisser, on n'expose aucune action.
        if (paymentSettled(order, invoice)) return null;
        // Un acompte déjà versé change la nature de l'action : il ne s'agit plus d'encaisser
        // la facture mais d'en solder le reliquat. Le libellé le dit.
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

  // Règles métier (miroir du backend) garantissant la cohérence des transitions :
  //  - modification autorisée uniquement au stade brouillon (OrderService.updateOrder exige PENDING) ;
  //  - annulation possible tant que la commande n'est pas dans un état terminal (livrée/annulée)
  //    ET qu'aucune facture vivante ne lui est rattachée.
  const canEditOrder = (order) => order?.status === 'PENDING';

  // Annulation : reproduit fidèlement les deux gardes de OrderService.cancelOrder, pour ne jamais
  // proposer un bouton qui finirait en erreur 400 :
  //  1. la machine à états autorise CANCELED depuis PENDING / CONFIRMED / INVOICED uniquement
  //     (DELIVERED et CANCELED sont terminaux) ;
  //  2. une commande facturée ne s'annule qu'une fois sa facture elle-même annulée (cohérence
  //     financière). Une commande payée — ou seulement facturée en attente de règlement — n'expose
  //     donc pas l'action : le parcours passe d'abord par l'annulation de la facture.
  // `invoice` est optionnelle : à défaut on lit le statut de facture porté par la liste.
  const canCancelOrder = (order, invoice = null) => {
    if (!['PENDING', 'CONFIRMED', 'INVOICED'].includes(order?.status)) return false;
    if (order?.status !== 'INVOICED') return true;
    return (invoice?.status ?? order?.invoiceStatus) === 'CANCELED';
  };
  // Une commande est *concernée* par le paiement dès qu'elle est facturée : c'est ce prédicat qui
  // déclenche le chargement de la facture et l'affichage de la section paiement du détail (laquelle
  // sait montrer « réglée » aussi bien qu'un reliquat).
  const canPayOrder = (order) => ['INVOICED', 'DELIVERED'].includes(order?.status);

  // Le statut de paiement vit sur la facture. La liste /orders en porte une copie
  // (`order.invoiceStatus`), ce qui permet de masquer l'action sans attendre un chargement.
  const paymentSettled = (order, invoice) => {
    const status = invoice?.status ?? order?.invoiceStatus;
    return status === 'PAID' || status === 'CANCELED';
  };

  // Action « Paiement » réellement proposable : facturée et pas encore soldée/annulée.
  const canRecordPayment = (order, invoice) =>
    canPayOrder(order) && !paymentSettled(order, invoice);

  // Reste-t-il une étape à traiter ? C'est ce qui décide d'ouvrir — ou non — le panier de
  // traitement depuis le détail : proposer de « poursuivre » une commande close n'a pas de
  // suite à offrir, et donne au caissier l'impression d'avoir manqué quelque chose.
  //
  // Sont closes : la commande annulée, celle qui est facturée puis intégralement réglée, et la
  // commande livrée sans reliquat. Une facture *annulée* ne clôt rien en revanche : la commande
  // reste à annuler à son tour (cf. canCancelOrder), il y a donc bien une suite.
  //
  // `invoice` est optionnelle : à défaut on lit le statut de facture porté par la liste. En cas
  // de statut inconnu on considère qu'il reste du travail — mieux vaut une action de trop qu'un
  // dossier qu'on ne peut plus reprendre.
  const hasNextStep = (order, invoice = null) => {
    if (order?.status === 'CANCELED') return false;
    if (!canPayOrder(order)) return true; // PENDING / CONFIRMED : confirmation ou facturation
    const invoiceStatus = invoice?.status ?? order?.invoiceStatus;
    if (invoiceStatus === 'PAID') return false;
    if (order?.status === 'DELIVERED' && !invoiceStatus) return false;
    return true;
  };

  // Génère et télécharge le PDF de la facture liée à une commande. `invoice` est passée quand
  // elle est déjà chargée (détail, carte vedette) pour éviter un aller-retour ; depuis la liste
  // on la résout par commande. Tous les points d'entrée /invoices renvoient le même
  // InvoiceResponse, qui porte la commande, son client et ses lignes — soit tout ce dont le
  // générateur a besoin.
  const handleDownloadInvoice = async (order, invoice = null) => {
    if (downloadingOrderId) return;
    setDownloadingOrderId(order.id);
    try {
      const fullInvoice = invoice ?? (await api.get(`/invoices/order/${order.id}`)).data;
      // Coordonnées de l'entreprise (en-tête + mentions légales), déjà chargées par
      // `useSettings` : leur indisponibilité ne bloque pas l'édition du document, le
      // générateur applique ses propres valeurs par défaut.
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

  // Téléchargement du PDF : proposé dès qu'un règlement est intervenu — facture soldée (PAID)
  // comme partiellement payée (PARTIALLY_PAID). Le document produit reflète fidèlement l'état du
  // règlement — bandeau « PARTIELLEMENT PAYÉE », montant déjà réglé et reste à payer — il vaut
  // donc justificatif d'acompte et ne risque pas d'être pris pour une facture soldée.
  // Restent exclues :
  //  - la facture annulée, document caduc qui n'a pas à circuler ;
  //  - la facture encore impayée, qui relève du suivi de facturation plutôt que du tableau de bord
  //    des commandes — l'écran Factures la fournit à tout stade, aucune capacité n'est perdue.
  // Le reliquat nul est accepté au même titre que le statut PAID : c'est la règle déjà retenue
  // par la section paiement du détail, qui tolère les arrondis au centime.
  const canDownloadInvoice = (order, invoice = null) => {
    if (!canPayOrder(order)) return false;
    const status = invoice?.status ?? order?.invoiceStatus;
    if (status === 'CANCELED') return false;
    if (status === 'PAID' || status === 'PARTIALLY_PAID') return true;
    return !!invoice && remainingOf(invoice) <= 0.001;
  };

  // Récupère la facture liée à la commande puis ouvre le modal de paiement.
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
    // Petite tolérance flottante pour autoriser le solde exact.
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

    // Si on change le produit, mettre à jour le prix automatiquement
    if (field === 'productId') {
      // Une ligne par produit : on refuse un produit déjà présent ailleurs dans la commande.
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

    // Recalculer le total
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
        // Le refus est motivé côté serveur (retour client rattaché, par exemple) : afficher le
        // message reçu plutôt qu'un « échec » qui n'apprend rien, comme pour l'annulation.
        toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
      }
    }
  };

  // L'icône est le seul apport local : le libellé vient de la table canonique `status.order.*`,
  // partagée avec les badges des autres écrans.
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

  /** Date et heure dans la convention de la langue active. */
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

  // Rend une ligne de commande. Extraite pour être réutilisée dans chaque groupe de statut.
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
        <td className="px-6 py-4 whitespace-nowrap">
          <div className="flex items-center gap-2">
            <ShoppingCart className="w-4 h-4 text-gray-400 shrink-0" />
            <span className="font-medium text-gray-900 dark:text-gray-100">{order.orderNumber}</span>
          </div>
        </td>
        <td className="px-6 py-4 whitespace-nowrap">
          <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
            <Calendar className="w-4 h-4 text-gray-400 shrink-0" />
            <span>{formatDate(order.createdAt)}</span>
          </div>
        </td>
        <td className="px-6 py-4 whitespace-nowrap">
          {order.client ? (
            /* Société affichée sous le nom quand elle existe : c'est souvent elle qu'on cherche
               dans une liste B2B, et elle départage les homonymes. */
            <div className="flex items-center gap-2">
              <User className="w-4 h-4 text-gray-400 shrink-0" />
              <div className="min-w-0">
                <p className="font-medium text-gray-900 dark:text-gray-100 truncate">
                  {order.client.firstName} {order.client.lastName}
                </p>
                {order.client.company && (
                  <p className="text-xs text-gray-500 dark:text-gray-400 truncate">{order.client.company}</p>
                )}
              </div>
            </div>
          ) : (
            <span className="badge-neutral">
              {t('orders.walkInClient')}
            </span>
          )}
        </td>
        <td className="px-6 py-4 whitespace-nowrap">
          {/* Nombre d'articles = somme des quantités, pas nombre de lignes : c'est ce qui est
              réellement sorti du stock. */}
          <div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
            <Package className="w-4 h-4 text-gray-400 shrink-0" />
            <span className="tabular-nums">
              {(order.items || []).reduce((n, i) => n + (parseInt(i.quantity) || 0), 0)}
            </span>
          </div>
        </td>
        <td className="px-6 py-4 whitespace-nowrap">
          {/* Total TTC, le montant réellement réclamé — celui de la facture dès qu'elle existe,
              donc le même chiffre que sur l'écran Factures. C'est le sous-total HT *avant*
              remise globale qui figurait ici : il ne correspondait ni à la facture, ni même à
              ce que la commande valait une fois remisée. */}
          {(() => {
            const { amount, estimated } = orderPayableAmount(order, listTaxRate);
            // Sous-ligne : la nature du chiffre puis la remise, seulement si elles existent.
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
        <td className="px-6 py-4 whitespace-nowrap">
          {/* La liste /orders porte le statut de la facture liée : une commande facturée puis
              réglée s'affiche « Payée » — et « Acompte versé » si le règlement est partiel. */}
          {getStatusBadge(resolveOrderStatusKey(order))}
        </td>
        <td className="px-6 py-4 whitespace-nowrap">
          {/* Complémentaire du badge, pas redondant : le badge nomme l'état courant, la barre
              situe la commande dans le cycle et montre ce qu'il reste à faire. */}
          <OrderProgress order={order} />
        </td>
        <td className="px-6 py-4 whitespace-nowrap text-sm">
          {/* Barre d'actions : bouton principal (prochaine étape du cycle
              PENDING→Confirmer→Facturer→Livrer) + paiement, puis actions
              secondaires en icônes (détail, modification, annulation, suppression),
              séparées par un trait fin pour une lecture plus claire. */}
          <div className="flex items-center justify-end gap-1.5">
            {(() => {
              const primary = getPrimaryAction(order);
              if (!primary) return null;
              const Icon = primary.icon;
              return (
                <button
                  onClick={(e) => { e.stopPropagation(); primary.onClick(); }}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-white text-xs font-semibold rounded-lg shadow-sm hover:shadow transition-all ${primary.className}`}
                  title={primary.label}
                >
                  <Icon className="w-3.5 h-3.5" />
                  {primary.shortLabel}
                </button>
              );
            })()}

            {/* Cas d'une commande livrée dont la facture garde un reliquat : l'action principale
                est vide, l'encaissement reste proposé ici. Même vocabulaire que le bouton
                principal — « Solde » dès qu'un acompte a été versé. */}
            {canRecordPayment(order) && getPrimaryAction(order)?.key !== 'PAY' && (
              <button
                onClick={(e) => { e.stopPropagation(); handleOpenPayment(order); }}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-green-700 bg-green-50 hover:bg-green-100 text-xs font-semibold rounded-lg border border-green-200 transition-colors"
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
            {/* DELIVERED : statut terminal — consultation (et paiement si reliquat) uniquement. */}
          </div>
        </td>
      </motion.tr>
    );
  };

  // ---------------------------------------------------------------------------------------
  // Recherche, tri, pagination
  //
  // Tout est appliqué EN BASE, par GET /orders/search : cet écran ne reçoit qu'une page.
  // Les critères sont donc traduits en paramètres de requête (voir `queryParams` plus bas) et
  // non plus évalués sur une liste chargée en entier.
  //
  // Ajouter un critère se fait désormais des deux côtés : son champ dans `OrderFilters`, sa
  // clé dans `EMPTY_ORDER_FILTERS`, son paramètre dans `queryParams`, et sa condition dans
  // `OrderService.buildFilter`. L'évaluer ici ne porterait que sur la page reçue.
  // ---------------------------------------------------------------------------------------

  // Options des filtres, servies par /orders/filter-options. Les déduire de `orders` ne
  // proposerait plus que les valeurs de la page affichée, et un critère disparaîtrait de la
  // liste dès qu'on change de page. L'endpoint reste dans le périmètre des commandes : lister
  // les utilisateurs par /users est réservé à l'ADMIN, alors que ce filtre sert aussi au caissier.
  const orderUsers = filterOptions.operators;
  const orderCities = filterOptions.cities;

  // Un critère actif force la liste complète : filtrer pour n'en voir que six premières
  // n'aurait aucun sens — et les tuiles d'indicateurs sont elles-mêmes des filtres de statut.
  const hasActiveFilters = useMemo(
    () => Object.keys(EMPTY_ORDER_FILTERS).some((key) => filters[key] !== EMPTY_ORDER_FILTERS[key]),
    [filters],
  );
  const showFullList = hasActiveFilters || viewMode === 'all';

  // Frappe temporisée sur les deux champs libres : sans cela, chaque caractère partirait au
  // serveur. Les autres critères sont des listes ou des dates, choisis d'un coup.
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
   * Critères envoyés au serveur.
   *
   * La vue « dernières commandes » n'est plus un découpage de la liste chargée mais une requête
   * à part entière : la première page triée par date décroissante. La même chose, exprimée là
   * où sont les données.
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
    // « Pas encore facturée » est l'ABSENCE de facture vivante, pas un statut de facture.
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

  // Le filtrage, le tri et la pagination sont faits en base : `orders` porte déjà la page
  // demandée, dans l'ordre demandé. Refiltrer ou retrier ici ne porterait que sur les lignes
  // reçues, et donnerait un tableau qui se prétend trié sans l'être.
  const displayedOrders = orders;
  const totalPages = pageMeta.totalPages;
  const currentPage = page;

  // Rechargement de la page à chaque changement de critère, de tri ou de numéro de page.
  useEffect(() => {
    fetchOrders();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryParams]);

  // Toute modification du périmètre ramène à la première page : rester en page 5 d'un résultat
  // qui n'en compte plus que 2 afficherait un tableau vide.
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
  // Décompte du périmètre entier, agrégé en base (cf. /api/orders/summary). Le recalculer sur
  // `orders` ne décrirait que la page — et ces tuiles sont elles-mêmes des filtres de statut :
  // annoncer « 3 en attente » d'après la page ouverte, puis en afficher douze après un clic,
  // ferait douter de l'un comme de l'autre.
  const stats = summary;

  // Cartes KPI cliquables (chacune filtre la liste sur son statut).
  // Chaque carte reprend le jeton du statut qu'elle filtre (cf. ORDER_STATUS_TONE).
  const kpiCards = [
    { key: 'ALL', label: t('orders.page.kpiTotal'), value: stats.total, icon: ShoppingCart, accent: 'success' },
    { key: 'PENDING', label: t('dashboard.status.pending'), value: stats.pending, icon: Clock, accent: ORDER_STATUS_TONE.PENDING },
    { key: 'CONFIRMED', label: t('dashboard.status.confirmed'), value: stats.confirmed, icon: CheckCircle, accent: ORDER_STATUS_TONE.CONFIRMED },
    { key: 'INVOICED', label: t('orders.page.kpiInvoiced'), value: stats.invoiced, icon: Euro, accent: ORDER_STATUS_TONE.INVOICED },
    { key: 'DELIVERED', label: t('dashboard.status.delivered'), value: stats.delivered, icon: Truck, accent: ORDER_STATUS_TONE.DELIVERED },
    { key: 'CANCELED', label: t('dashboard.status.canceled'), value: stats.canceled, icon: XCircle, accent: ORDER_STATUS_TONE.CANCELED }
  ];

  // Valeurs dérivées du formulaire d'édition (le client est figé : seuls les articles comptent).
  const editGrossTotal = editForm.orderItems.reduce(
    (sum, item) => sum + (parseFloat(item.unitPrice) || 0) * (parseInt(item.quantity) || 0), 0);
  const editDiscountTotal = editGrossTotal - editForm.totalAmount;
  const editItemCount = editForm.orderItems.length;
  // Mêmes garde-fous que la création : une commande PENDING dont une ligne dépasse le stock
  // serait enregistrée mais ne pourrait jamais être confirmée (confirmOrder lève alors une
  // InsufficientStockException). On bloque donc en amont.
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

      {/* ---- Étage 1 : indicateurs, qui servent aussi de filtres rapides sur le statut ---- */}
      <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-3">
        {kpiCards.map((kpi, i) => {
          // Repli sur `info` plutôt que sur un gris : un jeton inconnu ne doit pas
          // réintroduire une carte neutre dans le bandeau.
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

      {/* ---- Étage 2 : recherche et filtres ---- */}
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

      {/* ---- Étage 3 : la liste de travail, toujours visible ---- */}
      <div className="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-card overflow-hidden">
        {/* En-tête du répertoire : ce qu'on regarde, et l'étendue de ce qu'on regarde. */}
        <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div>
            <h2 className="section-title">{t('orders.page.directoryTitle')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
              {showFullList
                ? t('orders.page.directoryAll')
                : t('orders.page.directoryPreview', { count: RECENT_COUNT })}
            </p>
          </div>
          {/* La bascule disparaît quand un filtre est actif : la liste est alors complète
              d'office, un sélecteur sans effet se lirait comme une panne. */}
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
              <tr>
                <SortHeader label={t('orders.orderNumber')} sortKey="orderNumber" sort={sort} onSort={toggleSort} />
                <SortHeader label={t('orders.page.columnDateTime')} sortKey="createdAt" sort={sort} onSort={toggleSort} />
                <SortHeader label={t('orders.client')} sortKey="client" sort={sort} onSort={toggleSort} />
                {/* Non triable : somme des quantités des lignes, que la base n'ordonne pas —
                    voir SORT_FIELDS. */}
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  {t('orders.items')}
                </th>
                <SortHeader label={t('orders.page.columnAmount')} sortKey="totalAmount" sort={sort} onSort={toggleSort} />
                <SortHeader label={t('orders.status')} sortKey="status" sort={sort} onSort={toggleSort} />
                {/* Non triable : dérivé du statut ET de celui de la facture — voir SORT_FIELDS. */}
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  {t('orders.page.columnProgress')}
                </th>
                <th scope="col" className="table-th-right">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
              {loading ? (
                /* Lignes squelettes plutôt qu'un « Chargement… » centré : la structure du tableau
                   reste visible, et rien ne peut se lire à tort comme « aucun résultat ». */
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
                    {/* Deux vides différents : « aucune commande » et « aucun résultat » n'appellent
                        pas la même action, le second doit proposer de relâcher les filtres. */}
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

        {/* Pagination : seulement en liste complète — la vue d'aperçu n'affiche que six lignes,
            un pied de pagination y serait trompeur. */}
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
          /* Montants et décisions dérivés une seule fois : le même chiffre ne doit pas pouvoir
             diverger entre l'en-tête, le récapitulatif et le pied d'actions. */
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
          // Une fois la facture émise, le montant de référence est le total TTC : c'est lui
          // qui est encaissé. Sans facture vivante, la commande se lit en HT.
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
              {/* ---- En-tête : de quelle commande parle-t-on, pour combien, et où en est-elle.
                   L'avancement figure ici et non au fond de la fiche parmi les boutons : c'est
                   la première question qu'on se pose en ouvrant une commande. ---- */}
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
                      {/* Statut effectif : reflète le paiement réel porté par la facture —
                          « Payée » une fois soldée, « Acompte versé » tant qu'un reliquat subsiste. */}
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
                    // Même nuance que dans la liste : sur un règlement partiel, l'étape en cours
                    // s'appelle « Payée » par destination, pas par état. On nomme l'état atteint.
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
                  /* Bloc compact, identique à celui du détail de facture : le client se lit
                     comme sur l'enveloppe, avec de quoi le joindre sans quitter l'écran. */
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

              {/* ---- Récapitulatif : aligné à droite sous les lignes, comme sur un document
                   commercial. Le règlement y figure aussi — c'est un montant, sa place est dans
                   la colonne des montants, pas au milieu des boutons. ---- */}
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

              {/* ---- Situation : les constats du dossier. Les boutons, eux, sont regroupés au
                   pied de la fiche — ils étaient jusqu'ici mêlés à ces messages, en une pile de
                   six boutons pleine largeur aux styles tous différents. ---- */}
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

              {/* Sortie du cul-de-sac de l'annulation : « Annuler » est masqué tant que la facture
                  est vivante (cf. canCancelOrder), et la seule marche à suivre est de l'annuler
                  d'abord depuis l'écran Factures. La note ne s'affiche donc que dans ce cas. */}
              {selectedOrder.status === 'INVOICED' && !canCancelOrder(selectedOrder, detailInvoice) && (
                <p className="text-xs text-gray-400 dark:text-gray-500">
                  {t('orders.steps.cancelInvoiceFirst')}
                </p>
              )}

              {/* ---- Actions, collées au bas de la modale : le détail dépasse la hauteur d'écran
                   dès quelques lignes d'articles, et l'étape suivante doit rester sous la main.
                   Ordre de lecture : sortie, puis pièces jointes, puis ce qui fait avancer. ---- */}
              <div className="sticky bottom-0 -mx-6 -mb-6 flex flex-wrap items-center gap-3 border-t border-gray-200 bg-white/95 px-6 py-4 backdrop-blur dark:border-gray-700 dark:bg-gray-800/95">
                <Button variant="secondary" onClick={() => setShowDetailsModal(false)}>
                  {t('common.close')}
                </Button>

                <div className="ml-auto flex flex-wrap items-center justify-end gap-3">
                  {/* L'écran Factures ouvre directement le détail via `state.invoiceId`. */}
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

                  {/* Le PDF sert de justificatif au client : proposé dès qu'il y a une facture
                      exploitable, mais en retrait de l'action attendue à ce stade. */}
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

                  {/* Le panier répond à « qu'est-ce que j'en fais maintenant ? », là où le détail
                      répond à « où en est cette commande ? ». La question ne se pose plus sur un
                      dossier clos : le panier n'est proposé que s'il reste une étape à traiter. */}
                  {hasNextStep(selectedOrder, detailInvoice) && (
                    <Button
                      variant="outline"
                      icon={ClipboardList}
                      onClick={() => { setShowDetailsModal(false); openWorkspace(selectedOrder); }}
                    >
                      {t('orders.page.continueInWorkspace')}
                    </Button>
                  )}

                  {/* Une seule action principale : encaisser dès qu'un montant reste dû, sinon
                      l'étape suivante du cycle (confirmer, facturer). */}
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
              {/* Méta commande (lecture seule) + client, regroupés dans une seule carte compacte. */}
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
                    {/* Statut en lecture seule : il évolue via la confirmation, la facturation et la livraison. */}
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
                    {/* En-tête de colonnes (desktop) : labels affichés une seule fois ici. */}
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

              {/* Avertissement bloquant : explique pourquoi l'enregistrement est désactivé. */}
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

      {/* Panier de traitement : création d'une commande et suite de son cycle de vie au même
          endroit (articles, remises, validation, confirmation, facturation, encaissement, PDF). */}
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

      {/* Invoice Modal — facturation en ligne, sans renvoi vers la page Factures */}
      <AnimatePresence>
        {showInvoiceModal && invoiceOrder && (
          <Modal
            isOpen={showInvoiceModal}
            onClose={() => setShowInvoiceModal(false)}
            title={t('orders.page.invoiceOrderTitle', { number: invoiceOrder.orderNumber })}
          >
            {(() => {
              // Aperçu du calcul (miroir de InvoiceService) : HT − remise + TVA = TTC. La remise n'est
              // pas saisie ici (gérée au niveau des lignes de commande), elle vaut donc 0.
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

      {/* Payment Modal — encaissement sur la facture liée à la commande */}
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
