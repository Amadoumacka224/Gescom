import { createContext, useState } from 'react';
import authService from '../services/authService';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  // La session est lue directement au premier rendu, pas dans un useEffect. Avec un effet,
  // on avait d'abord un rendu avec `user = null, loading = true` puis un deuxième, donc
  // l'écran de chargement clignotait. Or authService.getCurrentUser() lit juste le
  // localStorage, c'est synchrone.
  const [user, setUser] = useState(() => authService.getCurrentUser() || null);
  // Gardé dans le contexte car ProtectedRoute et d'autres le lisent. Comme il n'y a plus
  // rien d'asynchrone au démarrage, il reste à false.
  const [loading] = useState(false);

  const login = async (username, password) => {
    const userData = await authService.login(username, password);
    setUser(userData);
    return userData;
  };

  const logout = () => {
    authService.logout();
    setUser(null);
  };

  /**
   * Met à jour l'utilisateur connecté après qu'il a modifié son profil.
   *
   * Le menu et l'en-tête lisent ce contexte. Avant, la page Profil devait recharger toute la
   * page (`window.location.reload()`) pour afficher le nouveau nom. On ne touche pas au token,
   * seulement aux infos affichées.
   */
  const updateUser = (changes) => {
    setUser((prev) => {
      const next = { ...(prev || {}), ...changes };
      // On met aussi à jour le localStorage, relu au prochain démarrage (voir authService).
      localStorage.setItem('user', JSON.stringify(next));
      return next;
    });
  };

  const value = {
    user,
    login,
    logout,
    updateUser,
    isAuthenticated: !!user,
    loading,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export default AuthContext;
