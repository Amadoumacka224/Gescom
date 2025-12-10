import axios from './axios';

const clientService = {
  getAllClients: () => axios.get('/clients'),
  getActiveClients: () => axios.get('/clients/active'),
  getClientById: (id) => axios.get(`/clients/${id}`),
  createClient: (client) => axios.post('/clients', client),
  updateClient: (id, client) => axios.put(`/clients/${id}`, client),
  deleteClient: (id) => axios.delete(`/clients/${id}`),
  deactivateClient: (id) => axios.patch(`/clients/${id}/deactivate`),
};

export default clientService;
