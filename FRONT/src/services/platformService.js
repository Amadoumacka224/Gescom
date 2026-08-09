import axios from './axios';

/**
 * Back-office du propriétaire de la plateforme.
 *
 * Toutes les routes vivent sous `/platform` et sont réservées au rôle SUPER_ADMIN : le
 * backend les refuse à tout autre rôle, y compris à l'ADMIN d'une entreprise cliente.
 * C'est un espace d'exploitation du SaaS, pas un niveau supérieur des écrans métier.
 *
 * Les listes sont paginées côté serveur et répondent `{ content, page, size, totalElements,
 * totalPages }`. La pagination est indexée à partir de zéro côté API, là où le composant
 * `Pagination` compte à partir de un — la conversion est faite dans les pages.
 */

const platformService = {
  // Indicateurs consolidés : entreprises, abonnements, MRR/ARR, paiements, churn,
  // alertes et santé. Une seule requête alimente tout le tableau de bord.
  getDashboard: () => axios.get('/platform/dashboard'),

  // --- Entreprises clientes -------------------------------------------------

  getCompanies: (params = {}) => axios.get('/platform/companies', { params }),

  getCompany: (id) => axios.get(`/platform/companies/${id}`),

  getCompanySubscriptions: (id) => axios.get(`/platform/companies/${id}/subscriptions`),

  /**
   * Ouvre un compte client complet : entreprise, administrateur initial et abonnement.
   * Le serveur traite les trois en une transaction — pas de compte à moitié créé.
   */
  provisionCompany: (payload) => axios.post('/platform/companies', payload),

  updateCompany: (id, payload) => axios.put(`/platform/companies/${id}`, payload),

  // Coupe l'accès de tous les utilisateurs de l'entreprise, sans rien supprimer.
  suspendCompany: (id, reason) => axios.patch(`/platform/companies/${id}/suspend`, { reason }),

  reactivateCompany: (id) => axios.patch(`/platform/companies/${id}/reactivate`),

  // Résilie : clôture l'abonnement et coupe l'accès ; les données sont conservées.
  cancelCompany: (id, reason) => axios.patch(`/platform/companies/${id}/cancel`, { reason }),

  // --- Abonnements ----------------------------------------------------------

  getSubscriptions: (params = {}) => axios.get('/platform/subscriptions', { params }),

  getPlans: () => axios.get('/platform/subscriptions/plans'),

  // Souscrire alors qu'un contrat court déjà vaut changement de formule : le serveur
  // clôture l'ancien et ouvre le nouveau, une entreprise n'ayant qu'un contrat vivant.
  subscribe: (payload) => axios.post('/platform/subscriptions', payload),

  renewSubscription: (id) => axios.patch(`/platform/subscriptions/${id}/renew`),

  cancelSubscription: (id, reason) =>
    axios.patch(`/platform/subscriptions/${id}/cancel`, { reason }),

  // --- Encaissements d'abonnement ------------------------------------------

  getPayments: (params = {}) => axios.get('/platform/payments', { params }),

  getPayment: (id) => axios.get(`/platform/payments/${id}`),

  // Un succès renouvelle la période de l'abonnement, un échec le passe en impayé.
  recordPayment: (payload) => axios.post('/platform/payments', payload),

  // --- Activité consolidée --------------------------------------------------

  getActivity: (params = {}) => axios.get('/platform/activity', { params }),
};

export default platformService;
