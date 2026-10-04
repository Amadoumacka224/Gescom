import axios from './axios';

/**
 * Back-office du super admin.
 *
 * Toutes les routes sont sous `/platform` et réservées à SUPER_ADMIN : le backend les refuse
 * à tous les autres rôles, même à l'ADMIN d'une entreprise. C'est l'espace de gestion du SaaS,
 * pas un niveau au-dessus des écrans métier.
 *
 * Les listes sont paginées côté serveur et renvoient `{ content, page, size, totalElements,
 * totalPages }`. Les pages commencent à 0 dans l'API mais à 1 dans le composant `Pagination`,
 * la conversion est faite dans les pages.
 */

const platformService = {
  // Stats globales : entreprises, abonnements, MRR/ARR, paiements, churn, alertes et
  // santé. Une seule requête pour tout le tableau de bord.
  getDashboard: () => axios.get('/platform/dashboard'),

  // --- Entreprises clientes -------------------------------------------------

  getCompanies: (params = {}) => axios.get('/platform/companies', { params }),

  getCompany: (id) => axios.get(`/platform/companies/${id}`),

  getCompanySubscriptions: (id) => axios.get(`/platform/companies/${id}/subscriptions`),

  /**
   * Crée un compte client complet : entreprise, premier admin et abonnement.
   * Le serveur fait les trois dans une transaction, pas de compte à moitié créé.
   */
  provisionCompany: (payload) => axios.post('/platform/companies', payload),

  updateCompany: (id, payload) => axios.put(`/platform/companies/${id}`, payload),

  // Bloque l'accès de tous les utilisateurs de l'entreprise, sans rien supprimer.
  suspendCompany: (id, reason) => axios.patch(`/platform/companies/${id}/suspend`, { reason }),

  reactivateCompany: (id) => axios.patch(`/platform/companies/${id}/reactivate`),

  // Résilie : ferme l'abonnement et bloque l'accès, mais on garde les données.
  cancelCompany: (id, reason) => axios.patch(`/platform/companies/${id}/cancel`, { reason }),

  // --- Abonnements ----------------------------------------------------------

  getSubscriptions: (params = {}) => axios.get('/platform/subscriptions', { params }),

  // Souscrire alors qu'il y a déjà un contrat en cours = changement de formule : le serveur
  // ferme l'ancien et crée le nouveau (une entreprise n'a qu'un contrat actif).
  subscribe: (payload) => axios.post('/platform/subscriptions', payload),

  renewSubscription: (id) => axios.patch(`/platform/subscriptions/${id}/renew`),

  cancelSubscription: (id, reason) =>
    axios.patch(`/platform/subscriptions/${id}/cancel`, { reason }),

  // --- Paiements des abonnements --------------------------------------------

  getPayments: (params = {}) => axios.get('/platform/payments', { params }),

  getPayment: (id) => axios.get(`/platform/payments/${id}`),

  // Un succès renouvelle la période de l'abonnement, un échec le met en impayé.
  recordPayment: (payload) => axios.post('/platform/payments', payload),

  // --- Utilisateurs de toutes les entreprises -------------------------------

  /** Liste globale, filtrable par entreprise, rôle, statut et recherche. */
  getUsers: (params = {}) => axios.get('/platform/users', { params }),

  /**
   * Bloque ou débloque un seul compte. Pratique pour le support, alors que suspendre
   * l'entreprise bloquerait tous ses utilisateurs.
   */
  setUserActive: (id, active) => axios.patch(`/platform/users/${id}/active`, { active }),

  // --- Formules -------------------------------------------------------------

  getPlans: () => axios.get('/platform/plans'),

  createPlan: (payload) => axios.post('/platform/plans', payload),

  updatePlan: (id, payload) => axios.put(`/platform/plans/${id}`, payload),

  /** Active ou désactive une formule. Ne change rien aux contrats en cours. */
  setPlanActive: (id, active) => axios.patch(`/platform/plans/${id}/active`, { active }),

  /** Refusé par le serveur si un abonnement (même résilié) utilise cette formule. */
  deletePlan: (id) => axios.delete(`/platform/plans/${id}`),

  // --- Activité globale -----------------------------------------------------

  /**
   * Une page du journal global. `params` accepte companyId, platformScope, actionType,
   * entity, start, end et search, tous optionnels et combinables. Le filtrage est fait en
   * base : sur une liste paginée, filtrer les lignes reçues ne porterait que sur la page.
   */
  getActivity: (params = {}) => axios.get('/platform/activity', { params }),

  /** Valeurs qui existent dans le journal (types d'action, entités), pour les filtres. */
  getActivityFilters: () => axios.get('/platform/activity/filters'),

  /**
   * Export CSV de tout le résultat filtré, généré par le serveur. Réponse binaire, d'où
   * `responseType: 'blob'`. Il faut passer les mêmes filtres que `getActivity` : on exporte
   * tout le résultat, pas seulement la page visible.
   */
  exportActivity: (params = {}) =>
    axios.get('/platform/activity/export', { params, responseType: 'blob' }),

  // --- Support --------------------------------------------------------------

  getTickets: (params = {}) => axios.get('/platform/support', { params }),

  getTicket: (id) => axios.get(`/platform/support/${id}`),

  /** Nombre affiché sur le badge : tickets encore à traiter. */
  getOpenTicketCount: () => axios.get('/platform/support/open-count'),

  /** La description devient le premier message du fil, ce n'est pas un champ à part. */
  openTicket: (payload) => axios.post('/platform/support', payload),

  /** `internal: true` pour une note interne, jamais envoyée au client. */
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

  /** Seuils du tableau de bord + infos du compte super admin connecté. */
  getSettings: () => axios.get('/platform/settings'),

  updateSettings: (payload) => axios.put('/platform/settings', payload),

  /**
   * Change l'email ou le mot de passe du super admin. Le mot de passe actuel est demandé dans
   * les deux cas. C'est le seul moyen de changer ce mot de passe, car le bootstrap ne modifie
   * jamais un compte existant.
   */
  updateAccount: (payload) => axios.patch('/platform/settings/account', payload),
};

export default platformService;
