import { createContext, useState } from 'react';
import authService from '../services/authService';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  // La session est lue au premier rendu, pas dans un effet. Le faire dans un useEffect
  // imposait un rendu initial à `user = null, loading = true` suivi d'un second rendu en
  // cascade — soit un clignotement de l'écran de chargement à chaque montage, alors que
  // authService.getCurrentUser() ne fait que lire le stockage local, de façon synchrone.
  const [user, setUser] = useState(() => authService.getCurrentUser() || null);
  // Conservé dans le contexte : ProtectedRoute et consorts le lisent. Il n'y a plus rien
  // d'asynchrone à attendre au démarrage, la valeur reste donc false.
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
   * Met à jour l'utilisateur connecté après une modification de son propre profil.
   *
   * La barre latérale et l'en-tête lisent ce contexte : sans lui, la page Profil devait
   * recharger la fenêtre entière (`window.location.reload()`) pour que le nouveau nom
   * s'y affiche. Le token n'est jamais touché — seules les données d'affichage le sont.
   */
  const updateUser = (changes) => {
    setUser((prev) => {
      const next = { ...(prev || {}), ...changes };
      // Le stockage local reste la source consultée au prochain démarrage (cf. authService).
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
