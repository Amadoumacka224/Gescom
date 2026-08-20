import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../context/useAuth';
import {
  LogIn,
  User,
  Lock,
  Globe,
  ShoppingCart,
  Boxes,
  ReceiptText,
  BarChart3,
  Mail,
  Phone,
  Eye,
  EyeOff,
  ShieldCheck,
  AlertCircle,
} from 'lucide-react';
import { motion } from 'framer-motion';

// Les quatre arguments de vente, dans l'ordre du flux métier : commande → stock →
// facturation → pilotage. Les libellés vivent dans les catalogues i18n, seuls l'icône
// et le ton restent ici.
const REMEMBERED_USERNAME_KEY = 'rememberedUsername';

const BENEFITS = [
  { key: 'sales', Icon: ShoppingCart, tone: 'panel-tone-info' },
  { key: 'stock', Icon: Boxes, tone: 'panel-tone-success' },
  { key: 'billing', Icon: ReceiptText, tone: 'panel-tone-accent' },
  { key: 'insights', Icon: BarChart3, tone: 'panel-tone-warning' },
];

const Login = () => {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { login } = useAuth();

  // Seul l'identifiant est mémorisé, jamais le mot de passe : le stockage local est
  // lisible par tout script de la page, un mot de passe y serait à découvert. Le
  // retenir est le métier du gestionnaire du navigateur, à qui les attributs
  // autoComplete des champs ci-dessous donnent ce qu'il attend.
  const rememberedUsername = localStorage.getItem(REMEMBERED_USERNAME_KEY) || '';

  const [formData, setFormData] = useState({
    username: rememberedUsername,
    password: '',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(Boolean(rememberedUsername));

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
      // Écrit après coup : mémoriser un identifiant refusé le ferait réapparaître
      // à chaque visite sans qu'il ait jamais ouvert de session.
      if (rememberMe) {
        localStorage.setItem(REMEMBERED_USERNAME_KEY, formData.username);
      } else {
        localStorage.removeItem(REMEMBERED_USERNAME_KEY);
      }
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

  // Même cycle que le sélecteur de l'en-tête : les trois langues sont traduites, un
  // bascule fr/en enfermait le visiteur néerlandophone hors de sa langue.
  const toggleLanguage = () => {
    const languages = ['fr', 'en', 'nl'];
    const currentIndex = languages.indexOf(i18n.language);
    const nextIndex = (currentIndex + 1) % languages.length;
    const newLang = languages[nextIndex];
    i18n.changeLanguage(newLang);
    localStorage.setItem('language', newLang);
  };

  return (
    // Deux panneaux pleine hauteur plutôt qu'une grille centrée : la vitrine tient la
    // moitié gauche en aplat bleu, la connexion la moitié droite sur fond clair. Sous
    // `lg` les deux s'empilent — la vitrine reste affichée, simplement plus compacte.
    <div className="min-h-screen lg:grid lg:grid-cols-2">
      {/* ---- Vitrine ---- */}
      <section className="relative overflow-hidden bg-gradient-to-br from-primary-600 via-primary-700 to-secondary-800 px-8 py-14 sm:px-12 lg:flex lg:flex-col lg:justify-center lg:px-16 lg:py-20">
        {/* Halos décoratifs en arrière-plan */}
        <div className="pointer-events-none absolute -top-32 -left-24 w-96 h-96 bg-primary-400/25 rounded-full blur-3xl" />
        <div className="pointer-events-none absolute -bottom-40 -right-24 w-[28rem] h-[28rem] bg-secondary-500/30 rounded-full blur-3xl" />

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="relative z-10 w-full max-w-xl mx-auto lg:mx-0"
        >
          <h1 className="text-4xl sm:text-5xl font-bold text-white leading-tight">
            {t('app.name')}
          </h1>
          <p className="mt-4 max-w-md text-lg text-primary-100">
            {t('app.tagline')}
          </p>

          <ul className="mt-10 space-y-5">
            {BENEFITS.map(({ key, Icon, tone }) => (
              <li key={key} className="flex items-center gap-4">
                {/* Médaillon posé sur l'aplat bleu : le jeton de domaine garde sa place dans
                    la classe, mais fond et icône repassent en blanc — les nuances -500/-700
                    de la charte disparaîtraient sur un fond de la même famille. */}
                <span
                  className={`panel-icon ${tone} shrink-0 !bg-white/15 !text-white ring-1 ring-white/25`}
                >
                  <Icon />
                </span>
                <h3 className="text-base font-semibold text-white">
                  {t(`landing.benefits.${key}Title`)}
                </h3>
              </li>
            ))}
          </ul>

          {/* Contact — coordonnées de l'éditeur, reprises des paramètres de la société */}
          <div className="mt-12 rounded-2xl bg-white/10 backdrop-blur-sm ring-1 ring-white/20 p-5">
            <h3 className="text-sm font-semibold text-white mb-4">
              {t('landing.contact.title')}
            </h3>
            <div className="flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-6">
              <a
                href={`mailto:${t('landing.contact.email')}`}
                className="flex items-center gap-2 text-sm font-medium text-primary-100 hover:text-white transition-colors"
              >
                <Mail className="w-4 h-4 shrink-0" />
                {t('landing.contact.email')}
              </a>
              <a
                href={`tel:${t('landing.contact.phone').replace(/\s/g, '')}`}
                className="flex items-center gap-2 text-sm font-medium text-primary-100 hover:text-white transition-colors"
              >
                <Phone className="w-4 h-4 shrink-0" />
                {t('landing.contact.phone')}
              </a>
            </div>
          </div>
        </motion.div>
      </section>

      {/* ---- Espace de connexion ---- */}
      <section className="relative flex flex-col justify-center bg-gray-50 px-6 py-14 sm:px-10 lg:px-16">
        {/* Language Toggle */}
        <button
          onClick={toggleLanguage}
          title={t('common.changeLanguage')}
          className="absolute top-6 right-6 z-20 flex items-center gap-2 px-4 py-2 bg-white rounded-lg shadow-soft ring-1 ring-gray-900/5 hover:shadow-card-hover transition-all duration-200"
        >
          <Globe className="w-4 h-4 text-primary-600" />
          <span className="text-sm font-medium text-gray-700 uppercase">
            {i18n.language}
          </span>
        </button>

        <div className="w-full max-w-md mx-auto">
          {/* Marque, au-dessus de la carte : le médaillon reprend le bleu de l'aplat de gauche */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 bg-gradient-to-br from-primary-500 to-primary-700 rounded-2xl shadow-lg">
              <LogIn className="w-8 h-8 text-white" />
            </div>
            <h2 className="mt-5 text-3xl font-bold text-gray-900 leading-tight">
              {t('app.name')}
            </h2>
            <p className="mt-1 text-sm text-gray-600">{t('app.tagline')}</p>
          </div>

          {/* Login Card */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.15 }}
            className="bg-white rounded-2xl shadow-elevated ring-1 ring-gray-900/5 p-8"
          >
            <div className="text-center mb-7">
              <span className="inline-flex items-center gap-1.5 text-xs font-medium text-primary-700 mb-3">
                <ShieldCheck className="w-4 h-4" />
                {t('auth.secureAccess')}
              </span>
              <h3 className="text-2xl font-bold text-gray-900 mb-1">
                {t('auth.welcomeBack')}
              </h3>
              <p className="text-gray-600 text-sm">{t('auth.signInMessage')}</p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-5">
              {/* Username Field */}
              <div>
                <label
                  htmlFor="username"
                  className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2"
                >
                  <User className="w-4 h-4 text-primary-600" />
                  {t('auth.username')}
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <User className="h-5 w-5 text-gray-400" />
                  </div>
                  <input
                    id="username"
                    type="text"
                    name="username"
                    value={formData.username}
                    onChange={handleChange}
                    className="input-field pl-10"
                    autoComplete="username"
                    required
                  />
                </div>
              </div>

              {/* Password Field */}
              <div>
                <label
                  htmlFor="password"
                  className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2"
                >
                  <Lock className="w-4 h-4 text-primary-600" />
                  {t('auth.password')}
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <Lock className="h-5 w-5 text-gray-400" />
                  </div>
                  <input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    name="password"
                    value={formData.password}
                    onChange={handleChange}
                    className="input-field pl-10 pr-10"
                    autoComplete="current-password"
                    required
                  />
                  {/* Affichage du mot de passe : le champ reste maître, seul son type change */}
                  <button
                    type="button"
                    onClick={() => setShowPassword((visible) => !visible)}
                    aria-label={t(showPassword ? 'auth.hidePassword' : 'auth.showPassword')}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600 transition-colors"
                  >
                    {showPassword ? (
                      <EyeOff className="h-5 w-5" />
                    ) : (
                      <Eye className="h-5 w-5" />
                    )}
                  </button>
                </div>
              </div>

              {/* Se souvenir de moi */}
              <label
                htmlFor="rememberMe"
                className="flex items-center gap-2.5 cursor-pointer select-none w-fit"
              >
                <input
                  id="rememberMe"
                  type="checkbox"
                  name="rememberMe"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  className="w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 cursor-pointer"
                />
                <span className="text-sm text-gray-700">
                  {t('auth.rememberMe')}
                </span>
              </label>

              {/* Error Message */}
              {error && (
                <motion.div
                  role="alert"
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="flex items-start gap-2 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm"
                >
                  <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                  <span>{error}</span>
                </motion.div>
              )}

              {/* Login Button */}
              <motion.button
                whileHover={{ scale: 1.01 }}
                whileTap={{ scale: 0.99 }}
                type="submit"
                disabled={loading}
                className="w-full btn-primary py-3 text-base font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
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
          </motion.div>

          {/* Footer */}
          <p className="text-center text-sm text-gray-600 mt-6">
            {t('auth.copyright', { year: new Date().getFullYear() })}
          </p>
        </div>
      </section>
    </div>
  );
};

export default Login;
