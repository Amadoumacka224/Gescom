import axios from './axios';

/**
 * Journal d'activité — le seul registre de l'application qui croît sans borne, donc le seul
 * dont les listes sont paginées côté serveur. Les réponses de liste ont la forme
 * `{ content, page, size, totalElements, totalPages }` et non un tableau brut.
 *
 * Corollaire : filtres, recherche et tri sont envoyés au serveur. Les appliquer sur les
 * lignes reçues ne porterait que sur la page affichée.
 */

// Taille de page maximale acceptée par le serveur (spring.data.web.pageable.max-page-size).
const MAX_PAGE_SIZE = 200;

const activityService = {
  /**
   * Page du journal. `params` accepte page, size, sort et les critères de filtrage
   * (userId, actionType, entity, start, end, search) — tous optionnels.
   */
  getActivities: (params = {}) => axios.get('/activities', { params }),

  // Indicateurs sur l'ensemble du journal : la page affichée ne permet pas de les déduire.
  getSummary: () => axios.get('/activities/summary'),

  getActivityById: (id) => axios.get(`/activities/${id}`),

  getActivitiesByUser: (userId, params = {}) =>
    axios.get(`/activities/user/${userId}`, { params }),

  getActivitiesByActionType: (actionType, params = {}) =>
    axios.get(`/activities/action/${actionType}`, { params }),

  getActivitiesByEntity: (entity, params = {}) =>
    axios.get(`/activities/entity/${entity}`, { params }),

  getActivitiesByDateRange: (startDate, endDate, params = {}) =>
    axios.get('/activities/date-range', {
      params: { start: startDate, end: endDate, ...params },
    }),

  getCaissierActivities: (params = {}) => axios.get('/activities/caissiers', { params }),

  /**
   * Toutes les lignes correspondant aux critères, page après page.
   * Réservé à l'export : l'écran, lui, ne charge jamais qu'une page. Sans cela un export
   * n'emporterait que les lignes visibles tout en s'annonçant comme le résultat filtré complet.
   */
  fetchAllMatching: async (params = {}) => {
    const rows = [];
    let page = 0;
    let totalPages = 1;
    do {
      const { data } = await axios.get('/activities', {
        params: { ...params, page, size: MAX_PAGE_SIZE },
      });
      rows.push(...(data.content || []));
      totalPages = data.totalPages ?? 1;
      page += 1;
    } while (page < totalPages);
    return rows;
  },
};

export default activityService;
