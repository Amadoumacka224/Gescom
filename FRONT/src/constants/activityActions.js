/**
 * Types d'action du journal d'activité.
 *
 * Doit refléter `ActivityLog.ActionType` côté backend. La table vivait en double — dans
 * l'historique et dans la fiche utilisateur — et les deux copies avaient divergé : celle de
 * la fiche utilisateur ignorait EXPORT et IMPORT, qui s'y affichaient donc en jeton brut.
 *
 * Ce module ne porte que des clés de traduction, jamais de libellé : les libellés vivent sous
 * `activity.actions.*` dans les catalogues i18n. Les couleurs associées sont dans
 * `ACTIVITY_TONE` (`statusBadges.js`).
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
 * Clé de traduction du libellé d'une action, à passer à `t()`.
 * Un type inconnu retombe sur une clé générique qui affiche le jeton brut plutôt que rien.
 */
export const actionLabelKey = (actionType) =>
  ACTION_TYPES.includes(actionType) ? `activity.actions.${actionType}` : 'activity.actions.unknown';
