import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import '../i18n';
import AuthContext from '../context/AuthContext';
import ProtectedRoute from './ProtectedRoute';

const renderAt = (auth) =>
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/orders']}>
        <Routes>
          <Route path="/login" element={<p>Page de connexion</p>} />
          <Route
            path="/orders"
            element={(
              <ProtectedRoute>
                <p>Commandes</p>
              </ProtectedRoute>
            )}
          />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );

describe('ProtectedRoute', () => {
  afterEach(cleanup);

  it('affiche la page à un utilisateur connecté', () => {
    renderAt({ isAuthenticated: true, loading: false });
    expect(screen.getByText('Commandes')).toBeTruthy();
  });

  it('renvoie vers la connexion sans session', () => {
    renderAt({ isAuthenticated: false, loading: false });
    expect(screen.getByText('Page de connexion')).toBeTruthy();
    expect(screen.queryByText('Commandes')).toBeNull();
  });

  it('n’affiche pas la page pendant le chargement de la session', () => {
    renderAt({ isAuthenticated: false, loading: true });
    expect(screen.queryByText('Commandes')).toBeNull();
    expect(screen.queryByText('Page de connexion')).toBeNull();
  });
});
