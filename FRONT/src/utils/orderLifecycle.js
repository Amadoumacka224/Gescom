/**
 * Repères communs au parcours de traitement d'une commande.
 *
 * La liste des étapes et le calcul de l'étape courante ont été retirés avec le ruban de
 * progression qu'ils alimentaient : l'avancement se lit désormais sur l'action proposée par le
 * panier, qui est de toute façon la seule suite possible à un instant donné.
 */

/** Reste dû d'une facture, tolérant l'absence du champ calculé par le backend. */
export const remainingOf = (invoice) =>
  Number(invoice?.remainingAmount ?? ((invoice?.totalAmount || 0) - (invoice?.paidAmount || 0))) || 0;
