/**
 * Calcul des montants d'une ligne de commande.
 *
 * Même calcul que `OrderService.computeLineTotal` côté backend : prix de vente × quantité,
 * moins la remise en pourcentage. C'est ici parce que trois écrans le faisaient chacun de
 * leur côté (création, modification, panier), et une différence d'arrondi entre l'aperçu et
 * le montant enregistré, on ne la voit qu'une fois la commande facturée.
 */

/** Remise de ligne limitée entre 0 et 100 % (le backend refuse le reste). */
export const clampDiscount = (value) => Math.min(Math.max(parseFloat(value) || 0, 0), 100);

/** Montant brut d'une ligne, sans la remise (sert pour le prix barré). */
export const lineGrossTotal = (item) =>
  (parseFloat(item?.unitPrice) || 0) * (parseInt(item?.quantity) || 0);

/** Montant net d'une ligne : brut − remise. */
export const computeLineTotal = (item) =>
  lineGrossTotal(item) * (1 - clampDiscount(item?.discount) / 100);

/** Total HT d'un panier = somme des montants nets des lignes. */
export const computeItemsTotal = (items) =>
  (items || []).reduce((sum, item) => sum + computeLineTotal(item), 0);

/** Total brut d'un panier, avant les remises des lignes. */
export const computeItemsGross = (items) =>
  (items || []).reduce((sum, item) => sum + lineGrossTotal(item), 0);

/**
 * Montant HT d'une commande après la remise globale, ce que le client doit hors taxes.
 *
 * `totalAmount` est le sous-total *avant* la remise globale (voir
 * `OrderService.applyGlobalDiscount`, qui calcule `finalAmount`). Si on l'affichait dans une
 * liste, on montrerait un montant que personne ne paiera dès qu'il y a une remise. On part
 * donc de `finalAmount`, et on le recalcule si le backend ne l'a pas encore rempli.
 */
export const orderNetAmount = (order) => {
  const gross = parseFloat(order?.totalAmount) || 0;
  const discount = parseFloat(order?.discount) || 0;
  const final = parseFloat(order?.finalAmount);
  return Number.isNaN(final) ? gross - discount : final;
};

/**
 * Montant TTC d'une commande, ce que le client paie vraiment. C'est ce qu'une liste de
 * commandes doit afficher pour être d'accord avec la facture.
 *
 * Si la commande est facturée, on prend le total de la facture : c'est le seul montant
 * officiel, avec la TVA et la remise (`InvoiceService.createInvoice`). Une facture annulée
 * ne compte pas, la commande est alors traitée comme pas facturée.
 *
 * Si elle n'est pas encore facturée, ce montant n'existe pas encore : la TVA n'est fixée
 * qu'à la facturation. On l'estime avec le taux des paramètres, comme le panier, et le flag
 * `estimated` permet à l'appelant de dire que c'est une estimation.
 */
export const orderPayableAmount = (order, taxRate) => {
  const live = order?.invoiceStatus && order.invoiceStatus !== 'CANCELED';
  const billed = parseFloat(order?.invoiceTotalAmount);
  if (live && !Number.isNaN(billed)) return { amount: billed, estimated: false };
  const rate = parseFloat(taxRate) || 0;
  return { amount: orderNetAmount(order) * (1 + rate / 100), estimated: true };
};
