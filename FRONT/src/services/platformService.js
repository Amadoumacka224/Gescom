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

  // --- Utilisateurs du parc -------------------------------------------------

  /** Liste consolidée, filtrable par entreprise, rôle, statut et recherche libre. */
  getUsers: (params = {}) => axios.get('/platform/users', { params }),

  /**
   * Coupe ou rétablit l'accès d'un compte précis — le levier du support, là où suspendre
   * l'entreprise couperait tous ses utilisateurs d'un coup.
   */
  setUserActive: (id, active) => axios.patch(`/platform/users/${id}/active`, { active }),

  // --- Catalogue des formules -----------------------------------------------

  getPlans: () => axios.get('/platform/plans'),

  createPlan: (payload) => axios.post('/platform/plans', payload),

  updatePlan: (id, payload) => axios.put(`/platform/plans/${id}`, payload),

  /** Retire ou remet une formule au catalogue. Sans effet sur les contrats en cours. */
  setPlanActive: (id, active) => axios.patch(`/platform/plans/${id}/active`, { active }),

  /** Refusé par le serveur dès qu'un abonnement, même résilié, s'y rattache. */
  deletePlan: (id) => axios.delete(`/platform/plans/${id}`),

  // --- Activité consolidée --------------------------------------------------

  /**
   * Page du journal du parc. `params` accepte companyId, platformScope, actionType, entity,
   * start, end et search — tous optionnels et cumulables. Le filtrage est fait en base : sur
   * une liste paginée, filtrer les lignes reçues ne porterait que sur la page affichée.
   */
  getActivity: (params = {}) => axios.get('/platform/activity', { params }),

  /** Valeurs présentes au journal (types d'action, entités), pour les listes de filtres. */
  getActivityFilters: () => axios.get('/platform/activity/filters'),

  /**
   * Export CSV du résultat filtré complet, assemblé par le serveur — réponse binaire, d'où
   * `responseType: 'blob'`. Passer les mêmes critères que `getActivity` : c'est le périmètre
   * affiché qui est exporté, pas la seule page visible.
   */
  exportActivity: (params = {}) =>
    axios.get('/platform/activity/export', { params, responseType: 'blob' }),

  // --- Support --------------------------------------------------------------

  getTickets: (params = {}) => axios.get('/platform/support', { params }),

  getTicket: (id) => axios.get(`/platform/support/${id}`),

  /** Compteur du badge : tickets encore à traiter. */
  getOpenTicketCount: () => axios.get('/platform/support/open-count'),

  /** La description devient le premier message du fil, pas un champ séparé. */
  openTicket: (payload) => axios.post('/platform/support', payload),

  /** `internal: true` marque une note de service, jamais destinée au client. */
  addTicketMessage: (id, payload) => axios.post(`/platform/support/${id}/messages`, payload),

  setTicketStatus: (id, status) => axios.patch(`/platform/support/${id}/status`, { status }),

  setTicketPriority: (id, priority) =>
    axios.patch(`/platform/support/${id}/priority`, { priority }),

  // --- Notifications --------------------------------------------------------

  getNotifications: (params = {}) => axios.get('/platform/notifications', { params }),

  getUnreadCount: () => axios.get('/platform/notifications/unread-count'),

  markNotificationRead: (id) => axios.patch(`/platform/notifications/${id}/read`),

  markAllNotificationsRead: () => axios.patch('/platform/notifications/read-all'),

  // --- Paramètres de la plateforme ------------------------------------------

  /** Seuils du tableau de bord + identité du compte propriétaire connecté. */
  getSettings: () => axios.get('/platform/settings'),

  updateSettings: (payload) => axios.put('/platform/settings', payload),

  /**
   * Modifie l'email ou le mot de passe du propriétaire. Le mot de passe actuel est exigé
   * dans les deux cas — c'est le seul chemin pour faire tourner ce secret, le bootstrap ne
   * réécrivant jamais un compte existant.
   */
  updateAccount: (payload) => axios.patch('/platform/settings/account', payload),
};

export default platformService;
