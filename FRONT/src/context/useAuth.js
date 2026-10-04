import { useContext } from 'react';
import AuthContext from './AuthContext';

/**
 * Hook pour accéder au contexte d'authentification.
 *
 * Il est dans son propre fichier, séparé d'AuthContext.jsx, car le hot reload de Vite ne
 * marche que pour les fichiers qui exportent uniquement des composants. Quand il était avec
 * AuthProvider, chaque modif rechargeait toute la page.
 */
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
