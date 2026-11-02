import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import axiosInstance from './axios';

// Remplace le transport HTTP : la réponse est fabriquée ici, aucune requête ne part.
const respondWith = (status) => (config) => {
  if (status < 400) return Promise.resolve({ data: {}, status, statusText: '', headers: {}, config });
  const error = new Error(`HTTP ${status}`);
  error.config = config;
  error.response = { status, data: {}, headers: {}, config };
  return Promise.reject(error);
};

describe('client axios', () => {
  const defaultAdapter = axiosInstance.defaults.adapter;

  beforeEach(() => {
    // Déjà sur /login : l'intercepteur ne tente pas de naviguer, ce que jsdom ne sait pas faire.
    window.history.pushState({}, '', '/login');
    localStorage.setItem('token', 'jeton-de-test');
    localStorage.setItem('user', '{"username":"anne"}');
  });

  afterEach(() => {
    axiosInstance.defaults.adapter = defaultAdapter;
    localStorage.clear();
  });

  it('joint le jeton et la langue à chaque requête', async () => {
    let sent;
    axiosInstance.defaults.adapter = (config) => {
      sent = config;
      return respondWith(200)(config);
    };

    await axiosInstance.get('/clients');

    expect(sent.headers.Authorization).toBe('Bearer jeton-de-test');
    expect(sent.headers['Accept-Language']).toBeTruthy();
  });

  it('efface la session sur un 401', async () => {
    axiosInstance.defaults.adapter = respondWith(401);

    await expect(axiosInstance.get('/clients')).rejects.toThrow();

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });

  it('garde la session sur un 401 de la connexion (mauvais mot de passe)', async () => {
    axiosInstance.defaults.adapter = respondWith(401);

    await expect(axiosInstance.post('/auth/login', {})).rejects.toThrow();

    expect(localStorage.getItem('token')).toBe('jeton-de-test');
  });

  it('garde la session sur un 403', async () => {
    axiosInstance.defaults.adapter = respondWith(403);

    await expect(axiosInstance.get('/users')).rejects.toThrow();

    expect(localStorage.getItem('token')).toBe('jeton-de-test');
  });
});
