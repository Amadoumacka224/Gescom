import axios from './axios';

const clientService = {
  /**
   * Tous les clients. Seulement pour les écrans qui ont besoin de la liste complète (le
   * choix du client dans une commande). Le tableau de l'écran Clients utilise searchClients.
   */
  getAllClients: () => axios.get('/clients'),

  /**
   * Une page de clients, filtrée et triée par le serveur.
   *
   * `params` : { page, size, sort, search, type, active, city, country, company, withEmail,
   * createdFrom, createdTo }. `page` suit Spring Data, donc commence à 0.
   */
  searchClients: (params) => axios.get('/clients/search', { params }),

  /** Compteurs en haut de page : sur tous les clients, pas sur la page affichée. */
  getSummary: () => axios.get('/clients/summary'),

  /** Villes et pays qui existent, pour les listes des filtres. */
  getFilterOptions: () => axios.get('/clients/filter-options'),
  getActiveClients: () => axios.get('/clients/active'),
  getClientById: (id) => axios.get(`/clients/${id}`),
  createClient: (client) => axios.post('/clients', client),
  updateClient: (id, client) => axios.put(`/clients/${id}`, client),
  deleteClient: (id) => axios.delete(`/clients/${id}`),
  deactivateClient: (id) => axios.patch(`/clients/${id}/deactivate`),
  // Export CSV (réservé à l'ADMIN côté backend) : réponse binaire, d'où `responseType: 'blob'`.
  exportClients: () => axios.get('/clients/export', { responseType: 'blob' }),
};

export default clientService;
