import axios from 'axios';

const API_URL = 'http://localhost:8085/api';

const axiosInstance = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Intercepteur pour ajouter le token JWT à chaque requête
axiosInstance.interceptors.request.use(
  (config) => {
    console.log('🚀 Axios Request Interceptor');
    console.log('URL:', config.url);
    console.log('Method:', config.method);
    console.log('Base URL:', config.baseURL);
    console.log('Full URL:', config.baseURL + config.url);

    const token = localStorage.getItem('token');
    console.log('Token exists:', !!token);

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
      console.log('Authorization header set');
    }

    // If data is FormData, remove Content-Type header to let browser set it with boundary
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
      console.log('FormData detected - Content-Type header removed');
    }

    console.log('Request data:', config.data);
    console.log('Request headers:', config.headers);

    return config;
  },
  (error) => {
    console.error('❌ Request Interceptor Error:', error);
    return Promise.reject(error);
  }
);

// Intercepteur pour gérer les erreurs d'authentification
axiosInstance.interceptors.response.use(
  (response) => {
    console.log('✅ Axios Response Success');
    console.log('Status:', response.status);
    console.log('Data:', response.data);
    return response;
  },
  (error) => {
    console.error('❌ Axios Response Error');
    console.error('Error:', error);
    console.error('Response:', error.response);

    if (error.response && error.response.status === 401) {
      // Don't logout for certain endpoints - let the page handle it gracefully
      const url = error.config?.url || '';
      if (url.includes('/settings') || url.includes('/import') || url.includes('/users') || url.includes('/profile')) {
        console.warn('⚠️ 401 on protected endpoint - not logging out');
        return Promise.reject(error);
      }

      console.warn('⚠️ 401 Unauthorized - Redirecting to login');
      // Token expiré ou invalide
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default axiosInstance;
