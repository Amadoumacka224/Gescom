import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from 'react-hot-toast';
import { CheckCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import clientService from '../services/clientService';
import Modal from './Modal';
import Button from './Button';
import ClientFormFields from './ClientFormFields';
import ConfirmModal from './ConfirmModal';
import StripeTerminalModal from './StripeTerminalModal';
import OrderWorkspaceCatalog from './OrderWorkspaceCatalog';
import OrderWorkspaceCart from './OrderWorkspaceCart';
import OrderWorkspaceRecap from './OrderWorkspaceRecap';
import OrderWorkspaceSteps from './OrderWorkspaceSteps';
import useSettings from '../hooks/useSettings';
import { computeItemsTotal, clampDiscount } from '../utils/orderTotals';
import {
  CLIENT_FIELD_ORDER,
  EMPTY_CLIENT_FORM,
  buildClientPayload,
  validateClient,
} from '../utils/clientForm';
import { extractErrorMessage } from '../utils/apiError';
import { formatCurrency } from '../utils/format';
import { generateInvoicePDF } from '../utils/pdfGenerator';

/*
 * Le scanner tire @zxing (plus de 450 ko) pour une modale qu'on n'ouvre qu'en caisse, et
 * seulement pour scanner. Importé statiquement, il partait dans le paquet d'entrée et se
 * téléchargeait sur toutes les pages, scanner ou non. `lazy` le charge à l'ouverture.
 *
 * La modale est déjà montée à la demande (`showScanner &&` plus bas) : rien d'autre à changer
 * qu'une frontière `Suspense` autour d'elle.
 */
const BarcodeScannerModal = lazy(() => import('./BarcodeScannerModal'));

/**
 * Panier de traitement d'une commande — poste de travail unique du cycle de vie.
 *
 * Auparavant, une commande se traitait en quatre endroits : le modal de création pour le
 * panier, la ligne du tableau pour la confirmation, un autre modal pour la facturation, un
 * troisième pour l'encaissement, l'écran Factures pour le PDF. Chaque étape refermait la
 * précédente et faisait perdre le contexte de la vente.
 *
 * Ici, le panier reste à l'écran du premier article au règlement final, et c'est lui qui
 * porte l'étape suivante :
 *
 *   Panier ─▶ Valider ─▶ Confirmer ─▶ Facturer ─▶ Encaisser ─▶ PDF ─▶ Nouvelle commande
 *              (POST)     (stock −)    (facture)   (paiement)
 *
 * Aucune règle métier n'est ajoutée ni contournée : chaque bouton appelle l'endpoint qui
 * existait déjà (`POST /orders`, `POST /orders/{id}/confirm`, `POST /invoices`,
 * `PATCH /invoices/{id}/payment`, terminal Stripe), dans l'ordre qu'impose la machine à
 * états du backend. Ce composant ne fait que rassembler le parcours au même endroit.
 *
 * Deux zones : à gauche la sélection d'articles tant que la commande est modifiable — elle
 * cède la place au dossier dès que les lignes sont figées —, à droite le panier et ses étapes.
 */
const OrderWorkspace = ({
  isOpen,
  onClose,
  initialOrder = null,
  products,
  clients,
  categories,
  onDataChanged,
  onClientsChanged,
  onOpenInvoice,
  onDetachOrder,
}) => {
  const { t } = useTranslation();
  const [order, setOrder] = useState(null);
  const [invoice, setInvoice] = useState(null);
  // Commande dont la facture a déjà été résolue — y compris quand la réponse est « aucune
  // facture ». Sans ce témoin, le chargement se relancerait à chaque changement d'identité de
  // `invoice`, donc indéfiniment.
  const [invoiceLoadedFor, setInvoiceLoadedFor] = useState(null);
  const [invoiceLoading, setInvoiceLoading] = useState(false);
  const [busy, setBusy] = useState(null);

  // Réglages de l'entreprise : taux de TVA et délai de paiement par défaut, plus les
  // coordonnées reprises sur le PDF. Même source que les autres écrans de facturation, avec
  // les mêmes valeurs de repli si l'appel échoue — un taux qui diffère d'un écran à l'autre
  // est le premier motif d'appel au support.
  const { settings, defaultTaxRate, defaultDueDate } = useSettings();

  const [cart, setCart] = useState({ clientMode: 'registered', clientId: '', items: [], notes: '' });

  const [showScanner, setShowScanner] = useState(false);
  const [showCreateClient, setShowCreateClient] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [terminalAmount, setTerminalAmount] = useState(null);
  // Boîte de confirmation générique : création, confirmation (sortie de stock) et annulation
  // partagent le même composant, seul le texte et l'action changent.
  const [confirmDialog, setConfirmDialog] = useState(null);

  // Création d'un client à la volée : mêmes champs et mêmes règles que l'écran Clients.
  // Le formulaire tenait ici sa propre version — trois champs vérifiés d'un bloc, aucun
  // contrôle de format, ni ville ni code postal ni pays.
  const [newClientForm, setNewClientForm] = useState(EMPTY_CLIENT_FORM);
  const [newClientTouched, setNewClientTouched] = useState({});
  const [newClientSubmitted, setNewClientSubmitted] = useState(false);
  const [newClientServerErrors, setNewClientServerErrors] = useState({});
  const [creatingClient, setCreatingClient] = useState(false);

  // Lignes d'une commande existante ramenées au format du panier. `product` est conservé pour
  // que l'affichage tienne même si l'article a été désactivé depuis (il sort alors de la liste
  // des produits actifs, mais la commande, elle, le contient toujours).
  const itemsFromOrder = (source) => (source.items || []).map((item) => ({
    productId: String(item.product?.id ?? ''),
    unitPrice: Number(item.unitPrice) || 0,
    discount: Number(item.discount) || 0,
    quantity: Number(item.quantity) || 1,
    product: item.product,
  }));

  /** Facture connue pour la commande courante — pose aussi le témoin de résolution. */
  const applyInvoice = (data, orderId) => {
    setInvoice(data);
    setInvoiceLoadedFor(orderId);
  };

  const resetDraft = useCallback(() => {
    setOrder(null);
    setInvoice(null);
    setInvoiceLoadedFor(null);
    setCart({ clientMode: 'registered', clientId: '', items: [], notes: '' });
  }, []);

  // Ouverture : soit sur une commande existante (reprise du traitement), soit sur un panier
  // vierge. Volontairement indexé sur l'identifiant et non sur l'objet : la liste parente se
  // rafraîchit après chaque action et renverrait sinon un nouvel objet à chaque rendu, ce qui
  // écraserait la saisie en cours.
  const initialOrderId = initialOrder?.id ?? null;
  const initialOrderRef = useRef(initialOrder);
  initialOrderRef.current = initialOrder;

  useEffect(() => {
    if (!isOpen) return;
    const source = initialOrderRef.current;
    if (source) {
      setOrder(source);
      setInvoice(null);
      setInvoiceLoadedFor(null);
      setCart({
        clientMode: source.client ? 'registered' : 'guest',
        clientId: source.client?.id ? String(source.client.id) : '',
        items: itemsFromOrder(source),
        notes: source.notes || '',
      });
    } else {
      resetDraft();
    }
  }, [isOpen, initialOrderId, resetDraft]);

  // Facture liée, dès que la commande est facturée : c'est elle qui porte le règlement.
  useEffect(() => {
    if (!order || !['INVOICED', 'DELIVERED'].includes(order.status)) return undefined;
    if (invoiceLoadedFor === order.id) return undefined;
    let active = true;
    setInvoiceLoading(true);
    api.get(`/invoices/order/${order.id}`)
      .then(({ data }) => { if (active) applyInvoice(data, order.id); })
      // Un 404 est un cas normal (commande livrée sans facture accessible) : on retient la
      // réponse pour ne pas rejouer l'appel en boucle.
      .catch(() => { if (active) applyInvoice(null, order.id); })
      .finally(() => { if (active) setInvoiceLoading(false); });
    return () => { active = false; };
  }, [order, invoiceLoadedFor]);

  const refreshOrder = useCallback(async (orderId) => {
    try {
      const { data } = await api.get(`/orders/${orderId}`);
      setOrder(data);
      return data;
    } catch (error) {
      console.error('Error refreshing order:', error);
      return null;
    }
  }, []);

  // ─────────────────────────── Panier : articles et remises ───────────────────────────

  const editable = !order || order.status === 'PENDING';

  const setItems = (updater) => {
    setCart((prev) => ({
      ...prev,
      items: typeof updater === 'function' ? updater(prev.items) : updater,
    }));
  };

  /**
   * Ajout d'un produit : une ligne par produit, quantité cumulée, jamais au-delà du stock
   * disponible — la même règle qu'applique `OrderService.confirmOrder` sous verrou. Renvoie
   * false si l'ajout a été refusé, ce qui permet au scan de ne pas vider son champ.
   */
  const addProduct = (product, qty = 1) => {
    if (!product) return false;
    if (!editable) {
      toast.error(t('orders.workspace.itemsLocked'));
      return false;
    }
    const quantity = Math.max(1, parseInt(qty) || 1);
    if (product.stockQuantity <= 0) {
      toast.error(t('orders.workspace.productOutOfStock', { name: product.name }));
      return false;
    }
    const items = cart.items;
    const index = items.findIndex((it) => parseInt(it.productId) === product.id);
    const current = index >= 0 ? (parseInt(items[index].quantity) || 0) : 0;
    const target = current + quantity;
    if (target > product.stockQuantity) {
      toast.error(t('orders.workspace.insufficientStockNamed', {
        name: product.name,
        max: product.stockQuantity,
      }));
      return false;
    }
    setItems(index >= 0
      ? items.map((it, i) => (i === index ? { ...it, quantity: target } : it))
      : [...items, {
          productId: String(product.id),
          unitPrice: product.sellingPrice,
          discount: 0,
          quantity,
          product,
        }]);
    return true;
  };

  const handleQtyStep = (index, product, delta) => {
    const current = parseInt(cart.items[index]?.quantity) || 0;
    const next = current + delta;
    if (next <= 0) {
      setItems((items) => items.filter((_, i) => i !== index));
      return;
    }
    if (product && next > product.stockQuantity) {
      toast.error(t('orders.workspace.insufficientStock', { max: product.stockQuantity }));
      return;
    }
    setItems((items) => items.map((it, i) => (i === index ? { ...it, quantity: next } : it)));
  };

  const handleLineChange = (index, field, value) => {
    const parsed = field === 'discount' ? clampDiscount(value) : value;
    setItems((items) => items.map((it, i) => (i === index ? { ...it, [field]: parsed } : it)));
  };

  const handleRemoveLine = (index) => setItems((items) => items.filter((_, i) => i !== index));

  const handleGlobalDiscount = (percent) => {
    setItems((items) => items.map((it) => ({ ...it, discount: percent })));
    toast.success(percent > 0
      ? t('orders.workspace.globalDiscountApplied', { percent })
      : t('orders.workspace.globalDiscountCleared'));
  };

  // ─────────────────────────── Scan de code-barres ───────────────────────────

  const scanFeedback = (p) =>
    t('orders.workspace.scanAdded', {
      name: p.name,
      code: p.code,
      price: formatCurrency(p.sellingPrice),
      stock: p.stockQuantity,
    });

  /**
   * Résolution d'un code : correspondance locale parmi les produits actifs déjà chargés
   * (instantané, cas courant), puis interrogation du backend — seule source fiable pour
   * distinguer un produit désactivé (200 avec active=false) d'un code inconnu (404).
   */
  const resolveScannedProduct = async (rawCode) => {
    const code = (rawCode || '').trim();
    if (!code) return { status: 'error', message: t('orders.workspace.emptyBarcode') };
    const local = products.find((p) => (p.barcode || '').trim() === code);
    if (local) {
      return local.active === false ? { status: 'inactive', product: local } : { status: 'ok', product: local };
    }
    try {
      const { data } = await api.get(`/products/barcode/${encodeURIComponent(code)}`);
      if (data?.active === false) return { status: 'inactive', product: data };
      return { status: 'ok', product: data };
    } catch (error) {
      if (error.response?.status === 404) {
        return { status: 'unknown', message: t('scanner.notices.unknownCode', { code }) };
      }
      return { status: 'error', message: extractErrorMessage(error) };
    }
  };

  const handleScanBarcode = async (rawCode) => {
    const code = (rawCode || '').trim();
    if (!code) {
      toast.error(t('orders.workspace.scanOrTypeBarcode'));
      return false;
    }
    setScanning(true);
    try {
      const result = await resolveScannedProduct(code);
      if (result.status === 'inactive') {
        toast.error(t('orders.workspace.productDisabled', { name: result.product.name }));
        return false;
      }
      if (result.status !== 'ok') {
        toast.error(result.message);
        return false;
      }
      if (!addProduct(result.product)) return false;
      toast.success(scanFeedback(result.product));
      return true;
    } finally {
      setScanning(false);
    }
  };

  const handleScannerConfirm = (product, qty) => {
    const added = addProduct(product, qty);
    if (added) toast.success(scanFeedback(product));
    return added;
  };

  // ─────────────────────────── Valeurs dérivées ───────────────────────────

  // Montant HT de référence : celui enregistré par le backend dès que la commande existe (c'est
  // lui que `InvoiceService` reprendra comme sous-total), le total du panier tant qu'elle n'est
  // qu'un brouillon.
  const cartTotal = computeItemsTotal(cart.items);
  const totalHT = order ? Number(order.totalAmount || 0) : cartTotal;
  const unitCount = cart.items.reduce((n, it) => n + (parseInt(it.quantity) || 0), 0);

  const cartQtyByProduct = useMemo(
    () => cart.items.reduce((acc, it) => {
      acc[String(it.productId)] = parseInt(it.quantity) || 0;
      return acc;
    }, {}),
    [cart.items]
  );

  const hasStockIssue = cart.items.some((item) => {
    const product = products.find((p) => p.id === parseInt(item.productId));
    return product && parseInt(item.quantity) > product.stockQuantity;
  });
  const hasInvalidQty = cart.items.some((item) => !(parseInt(item.quantity) > 0));
  const clientOk = cart.clientMode === 'guest' || !!cart.clientId;

  // Modifications non enregistrées d'une commande déjà créée : comparaison ligne à ligne sur
  // ce qui part réellement au backend (produit, quantité, remise) plus la note.
  const dirty = useMemo(() => {
    if (!order) return false;
    const original = itemsFromOrder(order);
    if (original.length !== cart.items.length) return true;
    if ((order.notes || '') !== (cart.notes || '')) return true;
    return cart.items.some((item, i) => (
      String(item.productId) !== String(original[i].productId)
      || (parseInt(item.quantity) || 0) !== (parseInt(original[i].quantity) || 0)
      || Math.abs(clampDiscount(item.discount) - clampDiscount(original[i].discount)) > 0.001
    ));
  }, [order, cart.items, cart.notes]);

  // Panier recevable : ce qui relève des lignes elles-mêmes. Le client n'en fait plus partie —
  // il se vérifie à la validation (voir `onValidate`), afin que son absence n'ait pas à occuper
  // un bandeau en permanence pour expliquer un bouton grisé.
  const canValidate = cart.items.length > 0
    && cart.items.every((item) => item.productId)
    && !hasStockIssue
    && !hasInvalidQty;

  const blockingHint = (() => {
    if (cart.items.length === 0) return t('orders.workspace.hintAddItem');
    if (hasInvalidQty) return t('orders.workspace.hintInvalidQty');
    if (hasStockIssue) return t('orders.workspace.hintStockExceeded');
    return null;
  })();

  // ─────────────────────────── Étapes du cycle de vie ───────────────────────────

  const payloadItems = () => cart.items.map((item) => ({
    productId: parseInt(item.productId),
    quantity: parseInt(item.quantity),
    discount: parseFloat(item.discount) || 0,
  }));

  const runStep = async (key, action) => {
    setBusy(key);
    try {
      await action();
    } finally {
      setBusy(null);
    }
  };

  const createOrder = () => runStep('create', async () => {
    try {
      const { data } = await api.post('/orders', {
        clientId: cart.clientMode === 'guest' ? null : parseInt(cart.clientId),
        items: payloadItems(),
        notes: cart.notes.trim() || null,
      });
      setOrder(data);
      setCart((prev) => ({ ...prev, items: itemsFromOrder(data), notes: data.notes || '' }));
      toast.success(t('orders.workspace.orderCreated', { number: data.orderNumber }));
      onDataChanged?.();
    } catch (error) {
      console.error('Error creating order:', error);
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  });

  const saveItems = () => runStep('save', async () => {
    try {
      // La remise globale n'est pas renvoyée : le backend ne touche qu'aux champs présents dans
      // la requête (cf. OrderService.updateOrder). Les notes, elles, sont bien pilotées par cet
      // écran — chaîne vide pour les effacer.
      const { data } = await api.put(`/orders/${order.id}`, {
        items: payloadItems(),
        notes: cart.notes.trim(),
      });
      setOrder(data);
      setCart((prev) => ({ ...prev, items: itemsFromOrder(data), notes: data.notes || '' }));
      toast.success(t('orders.workspace.orderUpdated'));
      onDataChanged?.();
    } catch (error) {
      console.error('Error updating order:', error);
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  });

  const confirmOrder = () => runStep('confirm', async () => {
    try {
      const { data } = await api.post(`/orders/${order.id}/confirm`);
      setOrder(data);
      setCart((prev) => ({ ...prev, items: itemsFromOrder(data) }));
      toast.success(t('orders.workspace.orderConfirmed'));
      onDataChanged?.();
    } catch (error) {
      console.error('Error confirming order:', error);
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  });

  const createInvoice = (form) => runStep('invoice', async () => {
    try {
      const { data } = await api.post('/invoices', { orderId: order.id, ...form });
      applyInvoice(data, order.id);
      await refreshOrder(order.id);
      toast.success(t('orders.workspace.invoiceCreated', { number: data.invoiceNumber }));
      onDataChanged?.();
    } catch (error) {
      console.error('Error creating invoice:', error);
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  });

  const recordPayment = (form) => runStep('pay', async () => {
    try {
      const { data } = await api.patch(`/invoices/${invoice.id}/payment`, form);
      applyInvoice(data, order.id);
      await refreshOrder(order.id);
      toast.success(data.status === 'PAID'
        ? t('orders.workspace.invoiceSettled')
        : t('orders.workspace.depositRecorded'));
      onDataChanged?.();
    } catch (error) {
      console.error('Error recording payment:', error);
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    }
  });

  const cancelOrder = () => setConfirmDialog({
    title: t('orders.steps.cancelOrder'),
    message: `Annuler la commande ${order.orderNumber} ? Le stock déjà consommé sera restitué.`,
    type: 'warning',
    onConfirm: () => runStep('cancel', async () => {
      try {
        await api.patch(`/orders/${order.id}/cancel`);
        await refreshOrder(order.id);
        toast.success(t('orders.workspace.orderCanceled'));
        onDataChanged?.();
      } catch (error) {
        console.error('Error canceling order:', error);
        toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
      }
    }),
  });

  const downloadPdf = () => runStep('pdf', async () => {
    try {
      // La facture chargée porte déjà la commande, son client et ses lignes : c'est tout ce
      // dont le générateur a besoin, aucun aller-retour supplémentaire.
      await generateInvoicePDF(invoice, settings || {});
    } catch (error) {
      console.error('Error generating invoice PDF:', error);
      toast.error(t('orders.workspace.pdfError'));
    }
  });

  const handleReset = () => {
    resetDraft();
    onDetachOrder?.();
  };

  // ─────────────────────────── Création d'un client à la volée ───────────────────────────

  /**
   * Mêmes règles que l'écran Clients (cf. `utils/clientForm`) : les deux enregistrent le même
   * `ClientRequest`, ils doivent refuser les mêmes saisies sous les mêmes messages.
   */
  const newClientErrors = useMemo(() => validateClient(newClientForm, t), [newClientForm, t]);

  const visibleNewClientErrors = useMemo(() => {
    const shown = {};
    Object.entries(newClientErrors).forEach(([field, message]) => {
      if (newClientSubmitted || newClientTouched[field]) shown[field] = message;
    });
    return { ...shown, ...newClientServerErrors };
  }, [newClientErrors, newClientServerErrors, newClientSubmitted, newClientTouched]);

  const handleNewClientChange = (e) => {
    const { name, value, type, checked } = e.target;
    setNewClientForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
    setNewClientServerErrors((prev) => (prev[name] === undefined ? prev : { ...prev, [name]: undefined }));
  };

  const handleNewClientBlur = (e) => {
    const { name } = e.target;
    setNewClientTouched((prev) => (prev[name] ? prev : { ...prev, [name]: true }));
  };

  const closeCreateClient = () => {
    setShowCreateClient(false);
    setNewClientForm(EMPTY_CLIENT_FORM);
    setNewClientTouched({});
    setNewClientSubmitted(false);
    setNewClientServerErrors({});
  };

  const submitNewClient = async () => {
    setNewClientSubmitted(true);
    const invalid = CLIENT_FIELD_ORDER.filter((field) => newClientErrors[field]);
    if (invalid.length > 0) {
      document.getElementById(invalid[0])?.focus();
      return;
    }

    setCreatingClient(true);
    try {
      const { data } = await clientService.createClient(buildClientPayload(newClientForm));
      toast.success(t('orders.workspace.clientCreated'));
      closeCreateClient();
      await onClientsChanged?.();
      // Le client tout juste créé devient celui de la vente en cours : c'est la raison
      // pour laquelle on le crée sans quitter le panier.
      setCart((prev) => ({ ...prev, clientMode: 'registered', clientId: String(data.id) }));
    } catch (error) {
      console.error('Error creating client:', error);
      // Le refus du serveur est ramené sur le champ concerné plutôt que sur un simple toast.
      const raw = error.response?.data;
      const fieldErrors = typeof raw === 'object' && raw?.fieldErrors ? { ...raw.fieldErrors } : {};
      if (error.response?.status === 409) fieldErrors.email = t('clients.errorEmailTaken');
      const flagged = CLIENT_FIELD_ORDER.filter((field) => fieldErrors[field]);
      if (flagged.length > 0) {
        setNewClientServerErrors(fieldErrors);
        setTimeout(() => document.getElementById(flagged[0])?.focus(), 0);
      }
      toast.error(t('common.errorPrefixed', { message: extractErrorMessage(error) }));
    } finally {
      setCreatingClient(false);
    }
  };

  if (!isOpen) return null;

  return (
    <>
      <Modal
        isOpen={isOpen}
        onClose={onClose}
        title={order
          ? t('orders.workspace.modalTitle', { number: order.orderNumber })
          : t('orders.addOrder')}
        size="fullscreen"
        fill
      >
        {/* La hauteur vient du modal (`fill`) plutôt que d'un calcul en vh recopiant ses
            marges : les deux colonnes descendent jusqu'en bas de l'écran quel que soit
            l'affichage, et le panier récupère tout ce que le catalogue ne prend pas.

            Les deux zones ne tiennent côte à côte qu'à partir de `lg`. En dessous, le panier
            réclamait toute la largeur (`w-full`) sans pouvoir se réduire (`shrink-0`) : le
            catalogue tombait à 0 px sur téléphone — plus aucun moyen d'ajouter un article — et
            à des vignettes de 35 px sur tablette. Elles s'empilent donc, catalogue au-dessus
            (on choisit avant d'encaisser), le tout défilant d'un seul bloc. */}
        <div className="flex h-full min-h-0 flex-col gap-3 overflow-y-auto lg:flex-row lg:overflow-hidden">
          {editable ? (
            <OrderWorkspaceCatalog
              products={products}
              categories={categories}
              cartQtyByProduct={cartQtyByProduct}
              onAddProduct={addProduct}
              onScanBarcode={handleScanBarcode}
              onOpenScanner={() => setShowScanner(true)}
              scanning={scanning}
            />
          ) : (
            <OrderWorkspaceRecap order={order} invoice={invoice} invoiceLoading={invoiceLoading} />
          )}

          <OrderWorkspaceCart
            order={order}
            invoice={invoice}
            items={cart.items}
            products={products}
            clients={clients}
            clientMode={cart.clientMode}
            clientId={cart.clientId}
            notes={cart.notes}
            editable={editable}
            taxRate={defaultTaxRate()}
            onClientModeChange={(mode) =>
              setCart((prev) => ({ ...prev, clientMode: mode, clientId: mode === 'guest' ? '' : prev.clientId }))}
            onClientChange={(value) => setCart((prev) => ({ ...prev, clientId: value }))}
            onCreateClient={() => setShowCreateClient(true)}
            onNotesChange={(value) => setCart((prev) => ({ ...prev, notes: value }))}
            onQtyStep={handleQtyStep}
            onLineChange={handleLineChange}
            onRemoveLine={handleRemoveLine}
            onApplyGlobalDiscount={handleGlobalDiscount}
          >
            <OrderWorkspaceSteps
              order={order}
              invoice={invoice}
              invoiceLoading={invoiceLoading}
              busy={busy}
              itemCount={cart.items.length}
              unitCount={unitCount}
              totalHT={totalHT}
              dirty={dirty}
              canValidate={canValidate}
              blockingHint={blockingHint}
              defaultTaxRate={defaultTaxRate()}
              defaultDueDate={defaultDueDate()}
              onValidate={() => {
                // Rappel au moment où il sert, et là seulement : le choix du client se fait en
                // haut du panier, sous un champ déjà marqué obligatoire. Un bandeau permanent
                // pour le redire coûtait une ligne à chaque vente.
                if (!clientOk) {
                  toast.error(t('orders.workspace.hintPickClient'));
                  return;
                }
                setConfirmDialog({
                  title: t('orders.confirmCreateTitle'),
                  message: t('orders.workspace.confirmCreateMessage', {
                    client: cart.clientMode === 'guest'
                      ? t('orders.walkInClient')
                      : t('orders.workspace.registeredClient'),
                    count: cart.items.length,
                    total: formatCurrency(cartTotal),
                  }),
                  type: 'info',
                  onConfirm: createOrder,
                });
              }}
              onSaveItems={saveItems}
              onConfirm={() => setConfirmDialog({
                title: t('orders.steps.confirmOrder'),
                message: t('orders.workspace.confirmOrderMessage', {
                  number: order?.orderNumber,
                  count: unitCount,
                }),
                type: 'info',
                onConfirm: confirmOrder,
              })}
              onCreateInvoice={createInvoice}
              onRecordPayment={recordPayment}
              onOpenTerminal={(amount) => setTerminalAmount(amount)}
              onDownloadPdf={downloadPdf}
              onCancelOrder={cancelOrder}
              onReset={handleReset}
              onOpenInvoice={() => onOpenInvoice?.(invoice)}
            />
          </OrderWorkspaceCart>
        </div>
      </Modal>

      {/* Scanner caméra, monté à la demande pour repartir d'un état propre à chaque ouverture. */}
      {showScanner && (
        <Suspense fallback={null}>
          <BarcodeScannerModal
            isOpen
            onClose={() => setShowScanner(false)}
            resolveProduct={resolveScannedProduct}
            onConfirm={handleScannerConfirm}
          />
        </Suspense>
      )}

      {/* Terminal carte : même facture et même montant que l'encaissement manuel, seul le canal
          change. C'est le serveur qui solde la facture, jamais le terminal. */}
      {terminalAmount !== null && invoice && (
        <StripeTerminalModal
          isOpen
          onClose={() => setTerminalAmount(null)}
          invoice={invoice}
          amount={terminalAmount}
          onPaid={async () => {
            await refreshOrder(order.id);
            try {
              const { data } = await api.get(`/invoices/order/${order.id}`);
              applyInvoice(data, order.id);
            } catch (error) {
              console.warn('Facture non rechargée après paiement carte:', error);
            }
            onDataChanged?.();
          }}
        />
      )}

      {/* Création d'un client sans quitter le panier : la vente en cours n'est pas perdue. */}
      <Modal
        isOpen={showCreateClient}
        onClose={closeCreateClient}
        title={t('orders.workspace.newClientTitle')}
        size="lg"
      >
        {/* `noValidate` : la validation est celle du formulaire, pas celle du navigateur, dont
            les bulles natives s'affichent hors de la charte et dans la langue du navigateur. */}
        <form
          noValidate
          onSubmit={(e) => { e.preventDefault(); submitNewClient(); }}
        >
          {/* Le statut n'est pas proposé : un client créé pendant une vente est actif par
              construction, et l'interrupteur n'apporterait qu'une question de plus au comptoir. */}
          <ClientFormFields
            values={newClientForm}
            errors={visibleNewClientErrors}
            onChange={handleNewClientChange}
            onBlur={handleNewClientBlur}
            showErrorSummary={newClientSubmitted}
            showStatus={false}
          />

          <div className="sticky -bottom-6 -mx-6 -mb-6 mt-6 flex flex-col-reverse gap-3 border-t border-gray-200 bg-white/95 px-6 py-4 backdrop-blur sm:flex-row sm:items-center sm:justify-between dark:border-gray-700 dark:bg-gray-800/95">
            <p className="text-xs text-gray-500 dark:text-gray-400">{t('clients.requiredHint')}</p>
            <div className="flex items-center justify-end gap-3">
              <Button variant="secondary" type="button" onClick={closeCreateClient}>
                {t('common.cancel')}
              </Button>
              <Button variant="primary" type="submit" icon={CheckCircle} loading={creatingClient}>
                {t('orders.workspace.createClient')}
              </Button>
            </div>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        isOpen={!!confirmDialog}
        onClose={() => setConfirmDialog(null)}
        onConfirm={() => {
          const action = confirmDialog?.onConfirm;
          setConfirmDialog(null);
          action?.();
        }}
        title={confirmDialog?.title || ''}
        message={confirmDialog?.message || ''}
        type={confirmDialog?.type || 'info'}
      />
    </>
  );
};

export default OrderWorkspace;
