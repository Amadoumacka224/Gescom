import { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import {
  User,
  Mail,
  Phone,
  Lock,
  Eye,
  EyeOff,
  Check,
  Save,
  KeyRound,
  AtSign,
  CalendarDays,
  Clock,
  ShieldCheck,
  RotateCcw,
} from 'lucide-react';
import { toast } from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import Button from '../components/Button';
import FormInput from '../components/FormInput';
import InfoRow from '../components/InfoRow';
import { extractErrorMessage } from '../utils/apiError';
import { badgeClass } from '../constants/statusBadges';
import i18n from '../i18n';

/**
 * Compte de l'utilisateur connecté : ses coordonnées et son mot de passe.
 *
 * Disposition en deux colonnes, comme les écrans de compte des applications de gestion :
 * une carte d'identité de gauche, purement informative et toujours visible, et à droite les
 * deux formulaires dans des onglets. L'identité restait auparavant à l'intérieur du
 * formulaire — l'avatar et le rôle disparaissaient donc dès qu'on passait sur l'onglet
 * Sécurité, alors que ce sont les repères « sur quel compte suis-je en train d'agir ».
 */

const ROLE_LABEL_KEYS = { ADMIN: 'roles.ADMIN', CAISSIER: 'roles.CAISSIER' };

/** Le rôle n'est pas un état : il identifie le compte, d'où deux teintes neutres distinctes. */
const ROLE_TONES = { ADMIN: 'accent', CAISSIER: 'info' };

const EMPTY_PROFILE = {
  username: '',
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  role: '',
  active: true,
  createdAt: null,
  updatedAt: null,
};

const EMPTY_PASSWORD = { currentPassword: '', newPassword: '', confirmPassword: '' };

/**
 * Règles de robustesse, reprises telles quelles de `UserService.validatePassword` côté backend
 * (8 caractères, une majuscule, une minuscule, un chiffre). L'écran annonçait « au moins
 * 4 caractères » : la saisie passait la validation locale puis était rejetée par le serveur,
 * sans que l'utilisateur sache quelle règle il venait d'enfreindre.
 */
const PASSWORD_RULES = [
  { key: 'length', labelKey: 'profile.rules.length', test: (p) => p.length >= 8 },
  { key: 'upper', labelKey: 'profile.rules.upper', test: (p) => /[A-Z]/.test(p) },
  { key: 'lower', labelKey: 'profile.rules.lower', test: (p) => /[a-z]/.test(p) },
  { key: 'digit', labelKey: 'profile.rules.digit', test: (p) => /\d/.test(p) },
];

const TABS = [
  { id: 'info', labelKey: 'profile.tabs.info', icon: User },
  { id: 'security', labelKey: 'profile.tabs.security', icon: Lock },
];

/** Champs que l'utilisateur peut modifier lui-même (cf. `UserUpdateSelfRequest`). */
const EDITABLE_FIELDS = ['firstName', 'lastName', 'email', 'phone'];

const formatDateTime = (iso) =>
  iso
    ? new Date(iso).toLocaleString(i18n.t('export.locale'), {
        day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit',
      })
    : null;

/** Marqueur de règle satisfaite ou non. La coche ne porte pas la seule information : le
 *  libellé reste lisible dans les deux états, seule sa teinte change. */
const RuleCheck = ({ label, satisfied }) => (
  <li
    className={`flex items-center gap-2 text-sm ${
      satisfied ? 'text-green-700 dark:text-green-400' : 'text-gray-500 dark:text-gray-400'
    }`}
  >
    <span
      className={`w-4 h-4 rounded-full flex items-center justify-center flex-shrink-0 ${
        satisfied
          ? 'bg-green-100 text-green-700 dark:bg-green-500/20 dark:text-green-300'
          : 'bg-gray-100 text-transparent dark:bg-gray-700'
      }`}
      aria-hidden="true"
    >
      <Check className="w-3 h-3" />
    </span>
    {label}
    <span className="sr-only">{satisfied ? ' : règle respectée' : ' : règle non respectée'}</span>
  </li>
);

/** Champ de mot de passe avec bascule d'affichage. `FormInput` ne gère pas d'icône à droite. */
const PasswordField = ({ id, label, value, onChange, visible, onToggle, placeholder, autoComplete, children }) => (
  <div className="space-y-2">
    <label htmlFor={id} className="block text-sm font-medium text-gray-700 dark:text-gray-300">
      {label} <span className="text-red-500">*</span>
    </label>
    <div className="relative">
      <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
        <KeyRound className="h-5 w-5 text-gray-400" aria-hidden="true" />
      </div>
      <input
        id={id}
        name={id}
        type={visible ? 'text' : 'password'}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        autoComplete={autoComplete}
        required
        className="input-field pl-10 pr-11"
      />
      <button
        type="button"
        onClick={onToggle}
        aria-pressed={visible}
        aria-label={visible ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
        className="absolute inset-y-0 right-0 px-3 flex items-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors"
      >
        {visible ? <EyeOff className="w-5 h-5" aria-hidden="true" /> : <Eye className="w-5 h-5" aria-hidden="true" />}
      </button>
    </div>
    {children}
  </div>
);

const Profile = () => {
  const { t } = useTranslation();
  const { user: authUser, updateUser } = useAuth();

  const [activeTab, setActiveTab] = useState('info');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);

  const [profile, setProfile] = useState(EMPTY_PROFILE);
  // Dernier état enregistré : sert de point de comparaison pour « Annuler » et pour savoir
  // si le formulaire a réellement changé.
  const [savedProfile, setSavedProfile] = useState(EMPTY_PROFILE);

  const [passwordData, setPasswordData] = useState(EMPTY_PASSWORD);
  const [showPasswords, setShowPasswords] = useState({ current: false, next: false, confirm: false });

  useEffect(() => {
    fetchProfile();
  }, []);

  const fetchProfile = async () => {
    if (!authUser?.id) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const response = await api.get('/users/me');
      const data = { ...EMPTY_PROFILE, ...response.data, phone: response.data.phone || '' };
      setProfile(data);
      setSavedProfile(data);
    } catch (error) {
      console.error('Error fetching user profile:', error);
      // Un 401 est déjà traité par l'intercepteur axios (redirection vers la connexion).
      if (error.response?.status !== 401) {
        toast.error(extractErrorMessage(error));
      }
    } finally {
      setLoading(false);
    }
  };

  const handleProfileChange = (e) => {
    const { name, value } = e.target;
    setProfile((prev) => ({ ...prev, [name]: value }));
  };

  const handlePasswordChange = (e) => {
    const { name, value } = e.target;
    setPasswordData((prev) => ({ ...prev, [name]: value }));
  };

  const toggleVisibility = (field) => {
    setShowPasswords((prev) => ({ ...prev, [field]: !prev[field] }));
  };

  const isDirty = useMemo(
    () => EDITABLE_FIELDS.some((field) => (profile[field] || '') !== (savedProfile[field] || '')),
    [profile, savedProfile]
  );

  const handleSubmitProfile = async (e) => {
    e.preventDefault();

    if (!profile.firstName.trim() || !profile.lastName.trim() || !profile.email.trim()) {
      toast.error(t('profile.requiredFields'));
      return;
    }

    setSaving(true);
    try {
      const response = await api.put('/users/me', {
        firstName: profile.firstName.trim(),
        lastName: profile.lastName.trim(),
        email: profile.email.trim(),
        phone: profile.phone?.trim() || '',
      });

      const data = { ...EMPTY_PROFILE, ...response.data, phone: response.data.phone || '' };
      setProfile(data);
      setSavedProfile(data);

      // La barre latérale et l'en-tête lisent le contexte d'authentification : le mettre à jour
      // suffit à y répercuter le nouveau nom, là où la page rechargeait toute la fenêtre.
      updateUser({
        firstName: data.firstName,
        lastName: data.lastName,
        email: data.email,
        phone: data.phone,
      });

      toast.success(t('profile.updated'));
    } catch (error) {
      console.error('Error updating profile:', error);
      toast.error(extractErrorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const ruleStates = useMemo(
    () => PASSWORD_RULES.map((rule) => ({ ...rule, satisfied: rule.test(passwordData.newPassword) })),
    [passwordData.newPassword]
  );

  const allRulesSatisfied = ruleStates.every((rule) => rule.satisfied);
  const passwordsMatch =
    passwordData.confirmPassword !== '' && passwordData.newPassword === passwordData.confirmPassword;
  const isSamePassword =
    passwordData.newPassword !== '' && passwordData.newPassword === passwordData.currentPassword;

  // Le bouton reste actif tant qu'il manque quelque chose : c'est le message d'erreur qui
  // désigne la règle en défaut, un bouton grisé sans explication laisse l'utilisateur bloqué.
  const handleSubmitPassword = async (e) => {
    e.preventDefault();

    if (!passwordData.currentPassword) {
      toast.error(t('profile.currentPasswordRequired'));
      return;
    }
    if (!allRulesSatisfied) {
      toast.error(t('profile.rulesNotMet'));
      return;
    }
    if (isSamePassword) {
      toast.error(t('profile.mustDiffer'));
      return;
    }
    if (!passwordsMatch) {
      toast.error(t('profile.confirmMismatch'));
      return;
    }

    setChangingPassword(true);
    try {
      await api.post('/users/me/change-password', {
        currentPassword: passwordData.currentPassword,
        newPassword: passwordData.newPassword,
      });
      setPasswordData(EMPTY_PASSWORD);
      setShowPasswords({ current: false, next: false, confirm: false });
      toast.success(t('profile.passwordChanged'));
    } catch (error) {
      console.error('Error changing password:', error);
      toast.error(extractErrorMessage(error));
    } finally {
      setChangingPassword(false);
    }
  };

  const fullName = `${profile.firstName} ${profile.lastName}`.trim();
  const initials = `${profile.firstName?.charAt(0) || ''}${profile.lastName?.charAt(0) || ''}`.toUpperCase();
  const roleLabel = ROLE_LABEL_KEYS[profile.role] ? t(ROLE_LABEL_KEYS[profile.role]) : (profile.role || '—');

  return (
    <div className="space-y-6">
      {/* ---- En-tête ---- */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <User aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('nav.profile')}</h1>
            <p className="page-subtitle">{t('profile.subtitle')}</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* ---- Carte d'identité ----
         * Colonne de gauche informative, collante au défilement : elle reste le repère du
         * compte en cours quel que soit l'onglet ouvert à droite. */}
        <motion.aside
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          className="lg:col-span-1"
        >
          <div className="card lg:sticky lg:top-6">
            <div className="flex flex-col items-center text-center pb-6 border-b border-gray-200 dark:border-gray-700">
              {loading ? (
                <>
                  <div className="skeleton w-24 h-24 rounded-full" />
                  <div className="skeleton h-5 w-40 mt-4" />
                  <div className="skeleton h-4 w-24 mt-2" />
                </>
              ) : (
                <>
                  <div
                    className="w-24 h-24 rounded-full bg-primary-600 flex items-center justify-center text-white font-bold text-3xl select-none"
                    aria-hidden="true"
                  >
                    {initials || '—'}
                  </div>
                  <h2 className="section-title mt-4">{fullName || t('profile.fallbackName')}</h2>
                  <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                    @{profile.username}
                  </p>
                  <div className="flex flex-wrap items-center justify-center gap-2 mt-3">
                    <span className={badgeClass(ROLE_TONES[profile.role])}>
                      <ShieldCheck className="w-3 h-3" aria-hidden="true" />
                      {roleLabel}
                    </span>
                    <span className={badgeClass(profile.active ? 'success' : 'neutral')}>
                      {profile.active ? t('profile.accountActive') : t('profile.accountDisabled')}
                    </span>
                  </div>
                </>
              )}
            </div>

            {/* Rappel en lecture seule des coordonnées enregistrées : ce qui est affiché ici
             * est l'état côté serveur, indépendamment de la saisie en cours à droite. */}
            <dl className="space-y-4 pt-6">
              <InfoRow icon={AtSign} label={t('auth.username')} value={profile.username} />
              <InfoRow icon={Mail} label={t('common.email')} value={savedProfile.email} />
              <InfoRow icon={Phone} label={t('common.phone')} value={savedProfile.phone} />
              <InfoRow icon={CalendarDays} label={t('profile.createdOn')} value={formatDateTime(profile.createdAt)} />
              <InfoRow icon={Clock} label={t('profile.lastUpdated')} value={formatDateTime(profile.updatedAt)} />
            </dl>
          </div>
        </motion.aside>

        {/* ---- Onglets ---- */}
        <div className="lg:col-span-2 space-y-6">
          <div className="card p-0 overflow-hidden">
            <div role="tablist" aria-label={t('profile.tablistLabel')} className="flex overflow-x-auto">
              {TABS.map((tab) => {
                const Icon = tab.icon;
                const selected = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    id={`profile-tab-${tab.id}`}
                    role="tab"
                    type="button"
                    aria-selected={selected}
                    aria-controls={`profile-panel-${tab.id}`}
                    onClick={() => setActiveTab(tab.id)}
                    className={`flex-1 min-w-max flex items-center justify-center gap-2 px-6 py-4 text-sm font-medium whitespace-nowrap border-b-2 transition-colors ${
                      selected
                        ? 'border-primary-600 text-primary-600 bg-primary-50 dark:bg-primary-500/10 dark:text-primary-300'
                        : 'border-transparent text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-50 dark:hover:bg-gray-700/40'
                    }`}
                  >
                    <Icon className="w-5 h-5" aria-hidden="true" />
                    {t(tab.labelKey)}
                  </button>
                );
              })}
            </div>
          </div>

          {/* ---- Informations personnelles ---- */}
          {activeTab === 'info' && (
            <motion.section
              id="profile-panel-info"
              role="tabpanel"
              aria-labelledby="profile-tab-info"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              className="card"
            >
              <div className="pb-5 border-b border-gray-200 dark:border-gray-700">
                <h2 className="section-title">{t('profile.tabs.info')}</h2>
                <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                  {t('profile.infoHint')}
                </p>
              </div>

              <form onSubmit={handleSubmitProfile} className="space-y-6 pt-6">
                {/* Identifiant de connexion : affiché mais non modifiable, il sert de clé au
                 * compte. Le désactiver sans l'expliquer se lit comme un champ en panne. */}
                <div className="space-y-2">
                  <label htmlFor="username" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                    {t('auth.username')}
                  </label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                      <AtSign className="h-5 w-5 text-gray-400" aria-hidden="true" />
                    </div>
                    <input
                      id="username"
                      type="text"
                      value={profile.username}
                      disabled
                      className="input-field pl-10"
                    />
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    {t('profile.usernameHint')}
                  </p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <FormInput
                    label={t('clients.firstName')}
                    name="firstName"
                    value={profile.firstName}
                    onChange={handleProfileChange}
                    placeholder={t('profile.firstNamePlaceholder')}
                    required
                    disabled={loading}
                    autoComplete="given-name"
                  />
                  <FormInput
                    label={t('clients.lastName')}
                    name="lastName"
                    value={profile.lastName}
                    onChange={handleProfileChange}
                    placeholder={t('profile.lastNamePlaceholder')}
                    required
                    disabled={loading}
                    autoComplete="family-name"
                  />
                </div>

                <FormInput
                  label={t('common.email')}
                  name="email"
                  type="email"
                  value={profile.email}
                  onChange={handleProfileChange}
                  placeholder={t('profile.emailPlaceholder')}
                  required
                  disabled={loading}
                  icon={Mail}
                  autoComplete="email"
                />

                <div className="space-y-1">
                  <FormInput
                    label={t('common.phone')}
                    name="phone"
                    type="tel"
                    value={profile.phone}
                    onChange={handleProfileChange}
                    placeholder={t('profile.phonePlaceholder')}
                    disabled={loading}
                    icon={Phone}
                    autoComplete="tel"
                  />
                  {/* Reprend le motif accepté par le backend (`^$|^[0-9+\- ]{6,20}$`). */}
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    {t('profile.phoneHint')}
                  </p>
                </div>

                <div className="flex flex-wrap items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
                  {/* Repère explicite de modification non enregistrée : sans lui, on quitte la
                   * page en croyant avoir sauvegardé. */}
                  {isDirty && (
                    <p className="mr-auto text-sm text-amber-700 dark:text-amber-400">
                      {t('profile.unsavedChanges')}
                    </p>
                  )}
                  <Button
                    variant="secondary"
                    type="button"
                    icon={RotateCcw}
                    onClick={() => setProfile(savedProfile)}
                    disabled={!isDirty || saving}
                  >
                    {t('common.cancel')}
                  </Button>
                  <Button
                    variant="primary"
                    type="submit"
                    icon={Save}
                    loading={saving}
                    disabled={loading || !isDirty}
                  >
                    {t('common.save')}
                  </Button>
                </div>
              </form>
            </motion.section>
          )}

          {/* ---- Sécurité ---- */}
          {activeTab === 'security' && (
            <motion.section
              id="profile-panel-security"
              role="tabpanel"
              aria-labelledby="profile-tab-security"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              className="card"
            >
              <div className="pb-5 border-b border-gray-200 dark:border-gray-700">
                <h2 className="section-title">{t('settings.changePasswordButton')}</h2>
                <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                  {t('profile.passwordHint')}
                </p>
              </div>

              <form onSubmit={handleSubmitPassword} className="space-y-6 pt-6">
                <PasswordField
                  id="currentPassword"
                  label={t('profile.currentPassword')}
                  value={passwordData.currentPassword}
                  onChange={handlePasswordChange}
                  visible={showPasswords.current}
                  onToggle={() => toggleVisibility('current')}
                  placeholder={t('profile.currentPasswordPlaceholder')}
                  autoComplete="current-password"
                />

                <PasswordField
                  id="newPassword"
                  label={t('profile.newPassword')}
                  value={passwordData.newPassword}
                  onChange={handlePasswordChange}
                  visible={showPasswords.next}
                  onToggle={() => toggleVisibility('next')}
                  placeholder={t('profile.newPasswordPlaceholder')}
                  autoComplete="new-password"
                >
                  {/* Les règles sont affichées en permanence, et pas seulement une fois la
                   * saisie commencée : on doit pouvoir les lire avant de choisir. */}
                  <ul className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 pt-2">
                    {ruleStates.map((rule) => (
                      <RuleCheck key={rule.key} label={t(rule.labelKey)} satisfied={rule.satisfied} />
                    ))}
                  </ul>
                  {isSamePassword && (
                    <p className="text-sm text-red-600 dark:text-red-400 pt-1">
                      {t('profile.mustDiffer')}
                    </p>
                  )}
                </PasswordField>

                <PasswordField
                  id="confirmPassword"
                  label={t('profile.confirmPassword')}
                  value={passwordData.confirmPassword}
                  onChange={handlePasswordChange}
                  visible={showPasswords.confirm}
                  onToggle={() => toggleVisibility('confirm')}
                  placeholder={t('profile.confirmPasswordPlaceholder')}
                  autoComplete="new-password"
                >
                  {passwordData.confirmPassword !== '' && (
                    <p
                      className={`text-sm pt-1 ${
                        passwordsMatch ? 'text-green-700 dark:text-green-400' : 'text-red-600 dark:text-red-400'
                      }`}
                    >
                      {passwordsMatch
                        ? t('profile.passwordsMatch')
                        : t('profile.passwordsDiffer')}
                    </p>
                  )}
                </PasswordField>

                <div className="flex items-center justify-end pt-4 border-t border-gray-200 dark:border-gray-700">
                  <Button variant="primary" type="submit" icon={Lock} loading={changingPassword}>
                    {t('settings.changePasswordButton')}
                  </Button>
                </div>
              </form>
            </motion.section>
          )}
        </div>
      </div>
    </div>
  );
};

export default Profile;
