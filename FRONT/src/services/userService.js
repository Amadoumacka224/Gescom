import axios from './axios';

const userService = {
  getAllUsers: () => axios.get('/users'),
  getUserById: (id) => axios.get(`/users/${id}`),
  createUser: (user) => axios.post('/users', user),
  updateUser: (id, user) => axios.put(`/users/${id}`, user),
  deleteUser: (id) => axios.delete(`/users/${id}`),
  deactivateUser: (id) => axios.patch(`/users/${id}/deactivate`),
};

export default userService;
