import axios from './axios';

const clientService = {
  /**
   * Fichier clients intégral. Réservé aux écrans qui s'en servent de référentiel — le
   * sélecteur de client à la saisie d'une commande. Le tableau de l'écran Clients passe par
   * searchClients.
   */
  getAllClients: () => axios.get('/clients'),

  /**
   * Page du fichier, filtrée et triée par le serveur.
   *
   * `params` : { page, size, sort, search, type, active, city, country, company, withEmail,
   * createdFrom, createdTo }. `page` est l'index de Spring Data, donc à partir de 0.
   */
  searchClients: (params) => axios.get('/clients/search', { params }),

  /** Compteurs d'en-tête : ils portent sur le fichier entier, pas sur la page affichée. */
  getSummary: () => axios.get('/clients/summary'),

  /** Villes et pays réellement présents, pour les listes déroulantes des filtres. */
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
