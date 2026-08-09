import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/useAuth';
import { LogIn, User, Lock, Globe } from 'lucide-react';
import { motion } from 'framer-motion';

const Login = () => {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { login } = useAuth();

  const [formData, setFormData] = useState({
    username: '',
    password: '',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
    setError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      await login(formData.username, formData.password);
      // Redirection confiée à HomeRedirect plutôt qu'écrite en dur ici : l'accueil dépend du
      // rôle, et une seule règle vaut mieux que deux à tenir d'accord. Viser /dashboard
      // fonctionnait par ricochet pour le caissier (AdminRoute le renvoyait sur sa caisse),
      // mais expédiait le propriétaire de la plateforme sur un écran de caisse dont l'API
      // lui refuse chaque appel.
      navigate('/');
    } catch (err) {
      const message = err.response?.data?.message || t('auth.loginError');
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const toggleLanguage = () => {
    const newLang = i18n.language === 'fr' ? 'en' : 'fr';
    i18n.changeLanguage(newLang);
    localStorage.setItem('language', newLang);
  };

  return (
    <div className="relative min-h-screen bg-gradient-to-br from-primary-50 via-white to-secondary-50 flex items-center justify-center p-4 overflow-hidden">
      {/* Halos décoratifs en arrière-plan */}
      <div className="pointer-events-none absolute -top-32 -left-24 w-96 h-96 bg-primary-300/30 rounded-full blur-3xl" />
      <div className="pointer-events-none absolute -bottom-32 -right-24 w-96 h-96 bg-secondary-300/30 rounded-full blur-3xl" />

      {/* Language Toggle */}
      <button
        onClick={toggleLanguage}
        className="absolute top-6 right-6 z-10 flex items-center gap-2 px-4 py-2 bg-white/80 backdrop-blur-sm rounded-lg shadow-soft ring-1 ring-gray-900/5 hover:shadow-card-hover transition-all duration-200"
      >
        <Globe className="w-4 h-4 text-primary-600" />
        <span className="text-sm font-medium text-gray-700">
          {i18n.language === 'fr' ? 'FR' : 'EN'}
        </span>
      </button>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="relative z-10 w-full max-w-md"
      >
        {/* Logo & Title */}
        <div className="text-center mb-8">
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ delay: 0.2, type: 'spring', stiffness: 200 }}
            className="inline-flex items-center justify-center w-20 h-20 bg-gradient-to-br from-primary-500 to-primary-700 rounded-2xl shadow-lg mb-4"
          >
            <LogIn className="w-10 h-10 text-white" />
          </motion.div>
          <motion.h1
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.3 }}
            className="text-4xl font-bold text-gray-900 mb-2"
          >
            {t('app.name')}
          </motion.h1>
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.4 }}
            className="text-gray-600"
          >
            {t('app.tagline')}
          </motion.p>
        </div>

        {/* Login Card */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
          className="bg-white/90 backdrop-blur-sm rounded-2xl shadow-elevated ring-1 ring-gray-900/5 p-8"
        >
          <div className="mb-6">
            <h2 className="text-2xl font-bold text-gray-900 mb-1">
              {t('auth.welcomeBack')}
            </h2>
            <p className="text-gray-600 text-sm">{t('auth.signInMessage')}</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Username Field */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                {t('auth.username')}
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <User className="h-5 w-5 text-gray-400" />
                </div>
                <input
                  type="text"
                  name="username"
                  value={formData.username}
                  onChange={handleChange}
                  className="input-field pl-10"
                  placeholder="admin"
                  required
                />
              </div>
            </div>

            {/* Password Field */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                {t('auth.password')}
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Lock className="h-5 w-5 text-gray-400" />
                </div>
                <input
                  type="password"
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  className="input-field pl-10"
                  placeholder="••••••••"
                  required
                />
              </div>
            </div>

            {/* Error Message */}
            {error && (
              <motion.div
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm"
              >
                {error}
              </motion.div>
            )}

            {/* Login Button */}
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              type="submit"
              disabled={loading}
              className="w-full btn-primary py-3 text-lg font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? (
                <div className="flex items-center justify-center gap-2">
                  <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                  {t('common.loading')}
                </div>
              ) : (
                <div className="flex items-center justify-center gap-2">
                  <LogIn className="w-5 h-5" />
                  {t('auth.login')}
                </div>
              )}
            </motion.button>
          </form>

          {/* Demo Credentials */}
         
        </motion.div>

        {/* Footer */}
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.8 }}
          className="text-center text-sm text-gray-600 mt-6"
        >
          {t('auth.copyright', { year: new Date().getFullYear() })}
        </motion.p>
      </motion.div>
    </div>
  );
};

export default Login;
