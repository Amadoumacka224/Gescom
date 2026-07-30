import axios from 'axios';

import i18n from '../i18n';

// En dev, '/api' est relayé vers le backend par le proxy Vite (voir vite.config.js).
// En prod, surcharger avec VITE_API_URL (URL absolue du backend).
const API_URL = import.meta.env.VITE_API_URL || '/api';

const axiosInstance = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

axiosInstance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Langue de l'interface : le backend s'en sert pour traduire ses messages d'erreur métier
    // (`AcceptHeaderLocaleResolver`, cf. LocaleConfiguration). Sans cet en-tête, il répondait
    // toujours en français — un utilisateur en anglais lisait des toasts français.
    config.headers['Accept-Language'] = i18n.language || 'fr';

    // Laisse le navigateur gérer le Content-Type pour les FormData (boundary multipart).
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    }

    return config;
  },
  (error) => Promise.reject(error)
);

axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const url = error.config?.url || '';
      // Sur /auth/login un 401 est légitime (mauvais identifiants) — ne pas déconnecter.
      const isLoginAttempt = url.includes('/auth/login');
      if (!isLoginAttempt) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
      }
    }
    return Promise.reject(error);
  }
);

export default axiosInstance;
