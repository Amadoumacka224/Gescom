import axios from './axios';

const activityService = {
  // Récupérer toutes les activités
  getAllActivities: () => axios.get('/activities'),

  // Récupérer une activité par ID
  getActivityById: (id) => axios.get(`/activities/${id}`),

  // Récupérer les activités d'un utilisateur
  getActivitiesByUser: (userId) => axios.get(`/activities/user/${userId}`),

  // Récupérer les activités par type d'action
  getActivitiesByActionType: (actionType) => axios.get(`/activities/action/${actionType}`),

  // Récupérer les activités par entité
  getActivitiesByEntity: (entity) => axios.get(`/activities/entity/${entity}`),

  // Récupérer les activités dans une plage de dates
  getActivitiesByDateRange: (startDate, endDate) =>
    axios.get('/activities/date-range', {
      params: { start: startDate, end: endDate }
    }),

  // Récupérer les activités des caissiers
  getCaissierActivities: () => axios.get('/activities/caissiers'),

  // Créer une nouvelle activité
  createActivity: (activity) => axios.post('/activities', activity),

  // Supprimer une activité
  deleteActivity: (id) => axios.delete(`/activities/${id}`),
};

export default activityService;
