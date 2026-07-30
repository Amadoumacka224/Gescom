/**
 * Modes de règlement, alignés sur l'énumération `Invoice.PaymentMethod` du backend.
 * Toute valeur absente d'ici est refusée par la désérialisation côté serveur.
 *
 * Seule la clé de traduction est portée ici : les libellés vivent sous `payment.methods.*`.
 */
export const PAYMENT_METHODS = [
  { value: 'CASH', labelKey: 'payment.methods.CASH' },
  { value: 'CREDIT_CARD', labelKey: 'payment.methods.CREDIT_CARD' },
  { value: 'DEBIT_CARD', labelKey: 'payment.methods.DEBIT_CARD' },
  { value: 'BANK_TRANSFER', labelKey: 'payment.methods.BANK_TRANSFER' },
  { value: 'CHECK', labelKey: 'payment.methods.CHECK' },
  { value: 'MOBILE_PAYMENT', labelKey: 'payment.methods.MOBILE_PAYMENT' },
];

/** Clé de traduction d'un mode de règlement reçu du serveur. */
export const paymentMethodLabelKey = (method) =>
  PAYMENT_METHODS.some((m) => m.value === method)
    ? `payment.methods.${method}`
    : 'payment.methods.unknown';

export default PAYMENT_METHODS;
