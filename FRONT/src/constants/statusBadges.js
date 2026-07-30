/**
 * Correspondance unique statut métier → couleur sémantique.
 *
 * Chaque page définissait auparavant sa propre table de couleurs : « Livrée » sortait en
 * `green-100/800` dans Reports, `green-50/700` via `badge-success` dans Deliveries et
 * `green-100/700` dans Orders, et seul OrderStatusBadge gérait le mode sombre. Tout écran
 * affichant un statut passe désormais par ce fichier.
 *
 * Les jetons disponibles sont décrits en tête de la section Badges de `src/index.css`.
 */

/** Cycle de vie d'une commande. L'indigo marque le jalon de facturation, ni succès ni alerte. */
export const ORDER_STATUS_TONE = {
  PENDING: 'warning',
  CONFIRMED: 'info',
  INVOICED: 'accent',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
  DELIVERED: 'success',
  CANCELED: 'danger',
};

/** Règlement d'une facture. */
export const INVOICE_STATUS_TONE = {
  UNPAID: 'danger',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
  CANCELED: 'neutral',
};

/** Livraison : planifiée, puis livrée. Le cycle s'arrête là (cf. Delivery.DeliveryStatus). */
export const DELIVERY_STATUS_TONE = {
  PENDING: 'warning',
  DELIVERED: 'success',
};

/**
 * Mouvements de stock : une entrée enrichit le stock, une sortie l'appauvrit.
 * Un retour est une entrée d'origine particulière (indigo, comme le jalon de facturation
 * il signale une étape à part), une casse appelle l'attention sans être bloquante.
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
 * Types d'activité du journal. Table partagée entre l'historique et la fiche utilisateur,
 * qui en tenaient deux versions divergentes — « Entrée stock » y était bleue d'un côté et
 * verte de l'autre. Le libellé identifie l'action, la couleur n'en signale que la nature :
 * ce qui ajoute est vert, ce qui retire est rouge, ce qui ne fait que consulter est neutre.
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
 * Classe de badge pour un jeton sémantique.
 * Un jeton inconnu retombe sur `neutral` : mieux vaut un badge gris qu'un badge sans style
 * (`badge-primary` et `badge-secondary`, jamais définis en CSS, s'affichaient sans couleur).
 */
export const badgeClass = (tone) =>
  `badge-${['success', 'warning', 'danger', 'info', 'accent', 'neutral'].includes(tone) ? tone : 'neutral'}`;

/** Classe de tuile d'indicateur pour un jeton sémantique. */
export const statTileClass = (tone) =>
  `stat-tile-${['success', 'warning', 'danger', 'info', 'accent', 'neutral'].includes(tone) ? tone : 'neutral'}`;

/**
 * Classe de ligne de détail d'un bloc de synthèse (même sémantique, format compact).
 * Les noms sont écrits en toutes lettres, pas construits par interpolation : le scanner de
 * Tailwind ne repère que les littéraux, et une classe qu'il ne voit pas est purgée du build.
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
 * Teinte de domaine d'un panneau de synthèse : médaillon d'en-tête et anneau de progression.
 * Elle identifie le panneau (commandes / factures / livraisons) et n'encode aucun état ;
 * les états passent par `metricBarClass` et `toneDotClass`.
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

/** Remplissage d'une barre de répartition, à la teinte du statut mesuré. */
const METRIC_BAR_CLASS = {
  success: 'metric-bar-success',
  warning: 'metric-bar-warning',
  danger: 'metric-bar-danger',
  info: 'metric-bar-info',
  accent: 'metric-bar-accent',
  neutral: 'metric-bar-neutral',
};

export const metricBarClass = (tone) => METRIC_BAR_CLASS[tone] ?? METRIC_BAR_CLASS.neutral;

/** Pastille de légende accolée au libellé d'un statut. */
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
 * Statut d'affichage d'une commande : le règlement prime sur le cycle de vie.
 * Une commande facturée puis réglée s'affiche « Payée » alors que son statut reste INVOICED
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
