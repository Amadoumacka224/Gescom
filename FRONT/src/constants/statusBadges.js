/**
 * Couleur associée à chaque statut, au même endroit pour toute l'appli.
 *
 * Avant, chaque page avait ses propres couleurs : « Livrée » était en `green-100/800` dans
 * Reports, en `green-50/700` (`badge-success`) dans Deliveries et en `green-100/700` dans
 * Orders, et seul OrderStatusBadge gérait le mode sombre. Maintenant tous les écrans passent
 * par ce fichier.
 *
 * Les valeurs possibles sont décrites au début de la section Badges de `src/index.css`.
 */

/** Statuts d'une commande. Indigo pour la facturation : ni un succès ni une alerte. */
export const ORDER_STATUS_TONE = {
  PENDING: 'warning',
  CONFIRMED: 'info',
  INVOICED: 'accent',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
  DELIVERED: 'success',
  CANCELED: 'danger',
};

/** Paiement d'une facture. */
export const INVOICE_STATUS_TONE = {
  UNPAID: 'danger',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
  CANCELED: 'neutral',
};

/** Livraison : planifiée puis livrée, pas d'autre état (voir Delivery.DeliveryStatus). */
export const DELIVERY_STATUS_TONE = {
  PENDING: 'warning',
  DELIVERED: 'success',
};

/**
 * Mouvements de stock : une entrée ajoute du stock, une sortie en retire.
 * Un retour est une entrée un peu spéciale (en indigo, comme la facturation), et la casse
 * est mise en avant sans être bloquante.
 */
export const STOCK_MOVEMENT_TONE = {
  STOCK_IN: 'success',
  STOCK_OUT: 'danger',
  ADJUSTMENT: 'info',
  RETURN: 'accent',
  DAMAGE: 'warning',
  TRANSFER: 'neutral',
};

/**
 * Types d'activité du journal. Utilisé par l'historique et la fiche utilisateur, qui avaient
 * chacun leur version (« Entrée stock » était bleue d'un côté et verte de l'autre). Le texte
 * dit ce qui s'est passé, la couleur juste le type : vert pour un ajout, rouge pour une
 * suppression, neutre pour une simple consultation.
 */
export const ACTIVITY_TONE = {
  CREATE: 'success',
  UPDATE: 'info',
  DELETE: 'danger',
  VIEW: 'neutral',
  LOGIN: 'neutral',
  LOGOUT: 'neutral',
  SALE: 'success',
  PAYMENT: 'success',
  STOCK_IN: 'success',
  STOCK_OUT: 'danger',
  EXPORT: 'info',
  IMPORT: 'info',
};

/**
 * Classe de badge pour une valeur donnée.
 * Si la valeur est inconnue on prend `neutral` : un badge gris vaut mieux qu'un badge sans
 * style (`badge-primary` et `badge-secondary` n'existaient pas en CSS et n'avaient pas de couleur).
 */
export const badgeClass = (tone) =>
  `badge-${['success', 'warning', 'danger', 'info', 'accent', 'neutral'].includes(tone) ? tone : 'neutral'}`;

/** Classe de carte de statistique pour une valeur donnée. */
export const statTileClass = (tone) =>
  `stat-tile-${['success', 'warning', 'danger', 'info', 'accent', 'neutral'].includes(tone) ? tone : 'neutral'}`;

/**
 * Classe d'une ligne de détail dans un bloc de résumé (même logique, en plus compact).
 * Les noms de classes sont écrits en entier et pas construits avec une variable : Tailwind
 * ne détecte que les chaînes complètes, sinon la classe disparaît du build.
 */
const MINI_STAT_CLASS = {
  success: 'mini-stat-success',
  warning: 'mini-stat-warning',
  danger: 'mini-stat-danger',
  info: 'mini-stat-info',
  accent: 'mini-stat-accent',
  neutral: 'mini-stat-neutral',
};

export const miniStatClass = (tone) => MINI_STAT_CLASS[tone] ?? MINI_STAT_CLASS.neutral;

/**
 * Couleur d'un panneau de résumé (icône d'en-tête et anneau).
 * Elle sert juste à reconnaître le panneau (commandes / factures / livraisons), pas un statut.
 * Les statuts utilisent `metricBarClass` et `toneDotClass`.
 */
const PANEL_TONE_CLASS = {
  success: 'panel-tone-success',
  warning: 'panel-tone-warning',
  danger: 'panel-tone-danger',
  info: 'panel-tone-info',
  accent: 'panel-tone-accent',
  neutral: 'panel-tone-neutral',
};

export const panelToneClass = (tone) => PANEL_TONE_CLASS[tone] ?? PANEL_TONE_CLASS.neutral;

/** Couleur de remplissage d'une barre de répartition, selon le statut. */
const METRIC_BAR_CLASS = {
  success: 'metric-bar-success',
  warning: 'metric-bar-warning',
  danger: 'metric-bar-danger',
  info: 'metric-bar-info',
  accent: 'metric-bar-accent',
  neutral: 'metric-bar-neutral',
};

export const metricBarClass = (tone) => METRIC_BAR_CLASS[tone] ?? METRIC_BAR_CLASS.neutral;

/** Petite pastille de couleur à côté du nom d'un statut. */
const TONE_DOT_CLASS = {
  success: 'tone-dot-success',
  warning: 'tone-dot-warning',
  danger: 'tone-dot-danger',
  info: 'tone-dot-info',
  accent: 'tone-dot-accent',
  neutral: 'tone-dot-neutral',
};

export const toneDotClass = (tone) => TONE_DOT_CLASS[tone] ?? TONE_DOT_CLASS.neutral;

/**
 * Statut affiché pour une commande : le paiement passe avant le statut de la commande.
 * Une commande facturée et payée affiche « Payée » même si son statut reste INVOICED
 * jusqu'à la livraison.
 */
export const resolveOrderStatusKey = (order, invoice = null) => {
  const invoiceStatus = invoice?.status ?? order?.invoiceStatus;
  if (order?.status === 'INVOICED') {
    if (invoiceStatus === 'PAID') return 'PAID';
    if (invoiceStatus === 'PARTIALLY_PAID') return 'PARTIALLY_PAID';
  }
  return order?.status && ORDER_STATUS_TONE[order.status] ? order.status : 'PENDING';
};
