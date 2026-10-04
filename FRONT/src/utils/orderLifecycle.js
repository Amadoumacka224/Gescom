/**
 * Fonctions communes pour le traitement d'une commande.
 *
 * La liste des étapes et le calcul de l'étape en cours ont été supprimés en même temps que
 * la barre de progression qui les utilisait : maintenant on voit l'avancement avec le bouton
 * proposé par le panier, qui est de toute façon la seule étape possible à ce moment-là.
 */

/** Reste à payer d'une facture, même si le champ calculé par le backend est absent. */
export const remainingOf = (invoice) =>
  Number(invoice?.remainingAmount ?? ((invoice?.totalAmount || 0) - (invoice?.paidAmount || 0))) || 0;
