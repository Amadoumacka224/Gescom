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
