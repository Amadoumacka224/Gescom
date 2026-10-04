/**
 * Types d'action du journal d'activité.
 *
 * Doit correspondre à `ActivityLog.ActionType` côté backend. Avant, cette liste était copiée
 * dans l'historique et dans la fiche utilisateur, et les deux n'étaient plus pareilles : la
 * fiche utilisateur n'avait pas EXPORT et IMPORT, qui s'affichaient donc en brut.
 *
 * On ne met ici que des clés de traduction. Les textes sont dans `activity.actions.*` (i18n)
 * et les couleurs dans `ACTIVITY_TONE` (`statusBadges.js`).
 */
export const ACTION_TYPES = [
  'CREATE',
  'UPDATE',
  'DELETE',
  'VIEW',
  'LOGIN',
  'LOGOUT',
  'SALE',
  'PAYMENT',
  'STOCK_IN',
  'STOCK_OUT',
  'EXPORT',
  'IMPORT',
];

/**
 * Clé de traduction d'une action, à passer à `t()`.
 * Pour un type inconnu on renvoie une clé générique qui affiche la valeur brute.
 */
export const actionLabelKey = (actionType) =>
  ACTION_TYPES.includes(actionType) ? `activity.actions.${actionType}` : 'activity.actions.unknown';
