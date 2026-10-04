/**
 * Modes de paiement, les mêmes que l'enum `Invoice.PaymentMethod` du backend.
 * Une valeur qui n'est pas dans cette liste sera refusée par le serveur.
 *
 * On ne garde que la clé de traduction, les textes sont dans `payment.methods.*`.
 */
export const PAYMENT_METHODS = [
  { value: 'CASH', labelKey: 'payment.methods.CASH' },
  { value: 'CREDIT_CARD', labelKey: 'payment.methods.CREDIT_CARD' },
  { value: 'DEBIT_CARD', labelKey: 'payment.methods.DEBIT_CARD' },
  { value: 'BANK_TRANSFER', labelKey: 'payment.methods.BANK_TRANSFER' },
  { value: 'CHECK', labelKey: 'payment.methods.CHECK' },
  { value: 'MOBILE_PAYMENT', labelKey: 'payment.methods.MOBILE_PAYMENT' },
];

/** Clé de traduction d'un mode de paiement reçu du serveur. */
export const paymentMethodLabelKey = (method) =>
  PAYMENT_METHODS.some((m) => m.value === method)
    ? `payment.methods.${method}`
    : 'payment.methods.unknown';

export default PAYMENT_METHODS;
