import { createContext, useState, useContext, useEffect } from 'react';
import authService from '../services/authService';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const currentUser = authService.getCurrentUser();
    if (currentUser) {
      setUser(currentUser);
    }
    setLoading(false);
  }, []);

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

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default AuthContext;
