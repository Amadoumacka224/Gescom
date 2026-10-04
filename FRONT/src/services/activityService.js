import axios from './axios';

/**
 * Journal d'activité. C'est la seule table de l'appli qui grossit sans limite, donc la seule
 * dont les listes sont paginées côté serveur. Les réponses ont la forme
 * `{ content, page, size, totalElements, totalPages }` et pas un simple tableau.
 *
 * Du coup, les filtres, la recherche et le tri sont envoyés au serveur : les faire sur les
 * lignes reçues ne porterait que sur la page affichée.
 */

// Taille de page max acceptée par le serveur (spring.data.web.pageable.max-page-size).
const MAX_PAGE_SIZE = 200;

const activityService = {
  /**
   * Une page du journal. `params` accepte page, size, sort et les filtres
   * (userId, actionType, entity, start, end, search), tous optionnels.
   */
  getActivities: (params = {}) => axios.get('/activities', { params }),

  // Stats sur tout le journal : on ne peut pas les calculer à partir de la page affichée.
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
   * Toutes les lignes qui correspondent aux filtres, page par page.
   * Seulement pour l'export : l'écran ne charge qu'une page. Sinon l'export ne contiendrait
   * que les lignes visibles.
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
