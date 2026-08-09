import { useContext } from 'react';
import AuthContext from './AuthContext';

/**
 * Accès au contexte d'authentification.
 *
 * Le hook vit dans son propre fichier, séparé d'AuthContext.jsx : le rafraîchissement à chaud
 * de Vite ne fonctionne que sur les modules qui n'exportent que des composants. Tant que ce
 * hook cohabitait avec AuthProvider, toute modification du fichier rechargeait la page entière
 * au lieu de remplacer le composant à la volée.
 */
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
