import axios from './axios';

const authService = {
  login: async (username, password) => {
    const response = await axios.post('/auth/login', { username, password });

    if (response.data && response.data.token) {
      // Enregistre le token
      localStorage.setItem('token', response.data.token);

      // Objet utilisateur avec toutes ses infos
      const userData = {
        id: response.data.id,
        username: response.data.username,
        firstName: response.data.firstName,
        lastName: response.data.lastName,
        email: response.data.email,
        phone: response.data.phone,
        role: response.data.role,
        token: response.data.token
      };

      // Enregistre les infos de l'utilisateur
      localStorage.setItem('user', JSON.stringify(userData));

      return userData;
    }

    throw new Error('Invalid response from server');
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  },

  getCurrentUser: () => {
    try {
      const user = localStorage.getItem('user');
      return user ? JSON.parse(user) : null;
    } catch (error) {
      console.error('Error parsing user data:', error);
      return null;
    }
  },

  getToken: () => {
    return localStorage.getItem('token');
  },

  isAuthenticated: () => {
    const token = localStorage.getItem('token');
    const user = localStorage.getItem('user');
    return !!(token && user);
  },
};

export default authService;
