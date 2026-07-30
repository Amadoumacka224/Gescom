import axios from './axios';

/**
 * Terminal de paiement carte (Stripe, mode test).
 *
 * Le parcours se joue en deux appels : on ouvre une intention pour une facture, puis on la
 * confirme avec un moyen de paiement de test. C'est la confirmation qui déclenche, côté
 * serveur, l'encaissement de la facture — le terminal ne met jamais un statut à jour lui-même.
 */
const paymentService = {
  // Montant omis : le serveur propose le reste dû de la facture.
  createIntent: (invoiceId, amount) =>
    axios.post('/payments/stripe/intents', { invoiceId, amount }),
  confirmIntent: (paymentId, paymentMethodId) =>
    axios.post(`/payments/stripe/intents/${paymentId}/confirm`, { paymentMethodId }),
  cancelIntent: (paymentId) => axios.post(`/payments/stripe/intents/${paymentId}/cancel`),
  getIntent: (paymentId) => axios.get(`/payments/stripe/intents/${paymentId}`),
  getByInvoice: (invoiceId) => axios.get(`/payments/invoice/${invoiceId}`),
};

/**
 * Cartes de test proposées par le terminal. Ce sont les jetons du bac à sable de Stripe :
 * les mêmes valeurs fonctionnent en mode simulé et en mode test réel.
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
