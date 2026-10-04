import axios from './axios';

/**
 * Terminal de paiement par carte (Stripe, mode test).
 *
 * Deux appels : on crée une intention pour une facture, puis on la confirme avec une carte
 * de test. C'est la confirmation qui fait enregistrer le paiement de la facture côté
 * serveur, le terminal ne change jamais un statut lui-même.
 */
const paymentService = {
  // Sans montant, le serveur prend le reste à payer de la facture.
  createIntent: (invoiceId, amount) =>
    axios.post('/payments/stripe/intents', { invoiceId, amount }),
  confirmIntent: (paymentId, paymentMethodId) =>
    axios.post(`/payments/stripe/intents/${paymentId}/confirm`, { paymentMethodId }),
  cancelIntent: (paymentId) => axios.post(`/payments/stripe/intents/${paymentId}/cancel`),
  getIntent: (paymentId) => axios.get(`/payments/stripe/intents/${paymentId}`),
  getByInvoice: (invoiceId) => axios.get(`/payments/invoice/${invoiceId}`),
};

/**
 * Cartes de test proposées par le terminal. Ce sont les tokens de test de Stripe : les mêmes
 * valeurs marchent en mode simulé et en vrai mode test.
 */
export const TEST_CARDS = [
  {
    id: 'pm_card_visa',
    labelKey: 'payment.testCards.accepted',
    hint: '4242 4242 4242 4242',
    outcome: 'success',
  },
  {
    id: 'pm_card_chargeDeclined',
    labelKey: 'payment.testCards.declined',
    hint: '4000 0000 0000 0002',
    outcome: 'decline',
  },
  {
    id: 'pm_card_chargeDeclinedInsufficientFunds',
    labelKey: 'payment.testCards.insufficientFunds',
    hint: '4000 0000 0000 9995',
    outcome: 'decline',
  },
];

export default paymentService;
