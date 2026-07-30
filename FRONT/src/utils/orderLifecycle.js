/**
 * Position d'une commande dans le parcours du panier de traitement.
 *
 * Quatre étapes, celles que le caissier enchaîne réellement — la livraison, elle, se pilote
 * depuis son propre écran et n'apparaît donc pas ici. L'avancement croise le statut de la
 * commande et celui de sa facture : le règlement n'est pas porté par la commande, une
 * commande facturée reste INVOICED jusqu'à la livraison même une fois soldée.
 */
export const WORKSPACE_STEPS = [
  { key: 'CART', labelKey: 'orders.workspace.steps.CART' },
  { key: 'CONFIRM', labelKey: 'orders.workspace.steps.CONFIRM' },
  { key: 'INVOICE', labelKey: 'orders.workspace.steps.INVOICE' },
  { key: 'PAYMENT', labelKey: 'orders.workspace.steps.PAYMENT' },
];

/** Reste dû d'une facture, tolérant l'absence du champ calculé par le backend. */
export const remainingOf = (invoice) =>
  Number(invoice?.remainingAmount ?? ((invoice?.totalAmount || 0) - (invoice?.paidAmount || 0))) || 0;

/**
 * Index de l'étape *en cours* : celles d'index inférieur sont franchies, un index égal au
 * nombre d'étapes signifie « processus terminé », −1 « commande annulée ».
 */
export const workspaceStepIndex = (order, invoice) => {
  if (!order) return 0;
  if (order.status === 'CANCELED') return -1;
  const invoiceStatus = invoice?.status ?? order.invoiceStatus;
  switch (order.status) {
    case 'PENDING': return 1;
    case 'CONFIRMED': return 2;
    case 'INVOICED':
      return invoiceStatus === 'PAID' || invoiceStatus === 'CANCELED' ? WORKSPACE_STEPS.length : 3;
    case 'DELIVERED':
      return !invoiceStatus || invoiceStatus === 'PAID' ? WORKSPACE_STEPS.length : 3;
    default: return 0;
  }
};
