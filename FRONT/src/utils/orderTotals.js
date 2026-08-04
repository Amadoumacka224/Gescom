/**
 * Calculs de montants d'une ligne de commande.
 *
 * Miroir exact de `OrderService.computeLineTotal` côté backend : prix de vente × quantité,
 * diminué de la remise exprimée en pourcentage. Centralisé ici parce que trois écrans
 * l'appliquaient chacun de leur côté (création, modification, panier de traitement) — et
 * qu'un écart d'arrondi entre l'aperçu et le montant réellement enregistré est le genre de
 * divergence qu'on ne voit qu'une fois la commande facturée.
 */

/** Remise de ligne bornée à [0, 100] % — le backend refuse tout ce qui sort de cet intervalle. */
export const clampDiscount = (value) => Math.min(Math.max(parseFloat(value) || 0, 0), 100);

/** Montant brut d'une ligne, remise non déduite (sert à afficher le prix barré). */
export const lineGrossTotal = (item) =>
  (parseFloat(item?.unitPrice) || 0) * (parseInt(item?.quantity) || 0);

/** Montant net d'une ligne : brut − remise. */
export const computeLineTotal = (item) =>
  lineGrossTotal(item) * (1 - clampDiscount(item?.discount) / 100);

/** Total HT d'un panier = somme des montants nets de ses lignes. */
export const computeItemsTotal = (items) =>
  (items || []).reduce((sum, item) => sum + computeLineTotal(item), 0);

/** Total brut d'un panier, avant remises de ligne. */
export const computeItemsGross = (items) =>
  (items || []).reduce((sum, item) => sum + lineGrossTotal(item), 0);

/**
 * Montant HT d'une commande, remise globale déduite — ce que le client doit hors taxes.
 *
 * `totalAmount` est le sous-total *avant* remise globale (cf. `OrderService.applyGlobalDiscount`,
 * qui en dérive `finalAmount`). L'afficher tel quel dans une liste revient à annoncer un montant
 * que personne ne paiera dès qu'une remise existe. On repart donc de `finalAmount`, en le
 * recalculant si le backend ne l'a pas encore renseigné.
 */
export const orderNetAmount = (order) => {
  const gross = parseFloat(order?.totalAmount) || 0;
  const discount = parseFloat(order?.discount) || 0;
  const final = parseFloat(order?.finalAmount);
  return Number.isNaN(final) ? gross - discount : final;
};

/**
 * Montant TTC d'une commande — ce que le client règle réellement, et donc ce qu'une liste de
 * commandes doit annoncer pour ne pas contredire la facture.
 *
 * Facturée, la référence est le total de la facture : c'est le seul montant émis, taux de TVA
 * et remise commerciale compris (`InvoiceService.createInvoice`). Une facture annulée ne compte
 * pas — la commande retombe alors dans le cas non facturé.
 *
 * Pas encore facturée, ce montant n'existe pas : la TVA n'est arrêtée qu'à la facturation. On
 * l'estime au taux configuré dans les réglages, exactement comme le panier de traitement, et le
 * drapeau `estimated` permet à l'appelant de le dire plutôt que de laisser croire à un montant
 * ferme.
 */
export const orderPayableAmount = (order, taxRate) => {
  const live = order?.invoiceStatus && order.invoiceStatus !== 'CANCELED';
  const billed = parseFloat(order?.invoiceTotalAmount);
  if (live && !Number.isNaN(billed)) return { amount: billed, estimated: false };
  const rate = parseFloat(taxRate) || 0;
  return { amount: orderNetAmount(order) * (1 + rate / 100), estimated: true };
};
