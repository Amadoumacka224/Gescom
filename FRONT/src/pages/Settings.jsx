import { useEffect, useMemo, useRef, useState } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  Save,
  Globe,
  Bell,
  Lock,
  Database,
  Download,
  Upload,
  FileText,
  Package,
  ShoppingCart,
  Truck,
  Warehouse,
  Users,
  Settings as SettingsIcon,
  Building2,
  Mail,
  Phone,
  MapPin,
  CreditCard,
  AlertTriangle,
  Check,
  Info,
  RotateCcw,
  KeyRound,
  Sun,
  Moon,
} from 'lucide-react';
import { motion } from 'framer-motion';
import { toast } from 'react-hot-toast';
import api from '../services/api';
import { invalidateSettingsCache } from '../hooks/useSettings';
import Modal from '../components/Modal';
import Button from '../components/Button';
import { extractErrorMessage } from '../utils/apiError';

/**
 * Réglages de l'application. Les réglages sont un singleton côté serveur (une seule ligne,
 * cf. `SettingsService`) : un unique `PUT /settings` enregistre l'ensemble des rubriques.
 *
 * D'où la disposition retenue, celle des écrans de configuration des applications de gestion :
 * une navigation verticale à gauche, groupée par domaine, et une seule barre d'enregistrement
 * qui n'apparaît qu'en cas de modification. L'ancien bouton « Enregistrer » de l'en-tête
 * disparaissait dès qu'on descendait dans la rubrique Entreprise, longue de dix champs, et
 * rien n'indiquait qu'une modification faite dans un onglet restait à enregistrer en passant
 * dans un autre.
 */

/**
 * Rubriques, groupées par domaine. « Apparence » a rejoint « Général » : elle ne contenait
 * qu'un choix de thème, et une entrée de navigation pour un seul champ coûte plus qu'elle
 * ne rapporte.
 */
const SECTIONS = [
  { id: 'company', icon: Building2, group: 'organisation' },
  { id: 'billing', icon: CreditCard, group: 'organisation' },
  { id: 'general', icon: Globe, group: 'application' },
  { id: 'notifications', icon: Bell, group: 'application' },
  { id: 'data', icon: Database, group: 'administration' },
  { id: 'security', icon: Lock, group: 'administration' },
];

const GROUPS = ['organisation', 'application', 'administration'];

/**
 * Champs réellement enregistrés, dans l'ordre de `SettingsRequest`. Sert à composer le corps
 * du PUT et à détecter les modifications : l'écran envoyait jusqu'ici l'objet complet reçu du
 * serveur, `id` et horodatages compris, que le DTO ignore.
 */
const EDITABLE_KEYS = [
  'language', 'currency', 'timezone', 'dateFormat',
  'companyName', 'companyEmail', 'companyPhone', 'companyAddress', 'companyCity',
  'companyPostalCode', 'companyCountry', 'companyTaxId', 'companyIban', 'companyBic',
  'taxRate', 'invoicePrefix', 'invoiceNumberStart', 'paymentTerms', 'footerText',
  'notifications', 'emailNotifications', 'orderNotifications', 'stockAlerts', 'lowStockThreshold',
  'theme',
];

/** Champs numériques : contrainte `@PositiveOrZero` côté backend. */
const NUMERIC_KEYS = ['taxRate', 'invoiceNumberStart', 'paymentTerms', 'lowStockThreshold'];

/**
 * État initial neutre. L'écran partait auparavant d'un jeu de valeurs de démonstration
 * (« GESCOM », « contact@gescom.be », un IBAN fictif) : quand le chargement échouait, ces
 * valeurs s'affichaient comme si elles étaient enregistrées, et un simple « Enregistrer »
 * les écrivait en base par-dessus les vraies.
 */
const EMPTY_SETTINGS = {
  language: 'fr',
  currency: 'EUR',
  timezone: 'Europe/Brussels',
  dateFormat: 'DD/MM/YYYY',
  companyName: '',
  companyEmail: '',
  companyPhone: '',
  companyAddress: '',
  companyCity: '',
  companyPostalCode: '',
  companyCountry: '',
  companyTaxId: '',
  companyIban: '',
  companyBic: '',
  taxRate: 21,
  invoicePrefix: 'INV',
  invoiceNumberStart: 1000,
  paymentTerms: 30,
  footerText: '',
  notifications: true,
  emailNotifications: true,
  orderNotifications: true,
  stockAlerts: true,
  lowStockThreshold: 10,
  theme: 'light',
};

const LANGUAGES = [
  { value: 'fr', label: 'Français' },
  { value: 'en', label: 'English' },
  { value: 'nl', label: 'Nederlands' },
];

const CURRENCIES = [
  { value: 'EUR', labelKey: 'settings.currencies.EUR' },
  { value: 'USD', labelKey: 'settings.currencies.USD' },
  { value: 'GBP', labelKey: 'settings.currencies.GBP' },
];

/** Europe/Brussels est la valeur par défaut du backend : sans elle dans la liste, le champ
 *  s'affichait vide sur une base fraîche et réécrivait Europe/Paris au premier enregistrement. */
const TIMEZONES = [
  { value: 'Europe/Brussels', labelKey: 'settings.timezones.brussels' },
  { value: 'Europe/Paris', labelKey: 'settings.timezones.paris' },
  { value: 'Europe/London', labelKey: 'settings.timezones.london' },
  { value: 'America/New_York', labelKey: 'settings.timezones.newYork' },
];

const DATE_FORMATS = [
  { value: 'DD/MM/YYYY', labelKey: 'settings.dateFormats.dmy' },
  { value: 'MM/DD/YYYY', labelKey: 'settings.dateFormats.mdy' },
  { value: 'YYYY-MM-DD', labelKey: 'settings.dateFormats.ymd' },
];

/**
 * Jeux de données exportables. `importable` reflète l'API : seul `POST /products/import`
 * existe. Les quatre autres boutons « Importer » appelaient une route inexistante et
 * échouaient en 404 après que l'utilisateur ait choisi son fichier.
 */
const DATASETS = [
  { type: 'clients', icon: Users, importable: false },
  { type: 'products', icon: Package, importable: true },
  { type: 'orders', icon: ShoppingCart, importable: false },
  { type: 'deliveries', icon: Truck, importable: false },
  { type: 'stock', icon: Warehouse, importable: false },
];

/* ------------------------------------------------------------------------------------------
 * Champs de formulaire.
 *
 * Définis au niveau du module et non dans le corps de `Settings` : une fonction déclarée dans
 * le rendu change d'identité à chaque frappe, React démontait donc l'`<input>` et le
 * remontait à chaque caractère — le champ perdait le focus à chaque lettre saisie.
 * ---------------------------------------------------------------------------------------- */

const Field = ({ label, htmlFor, hint, required = false, children }) => (
  <div className="space-y-2">
    <label htmlFor={htmlFor} className="block text-sm font-medium text-gray-700 dark:text-gray-300">
      {label} {required && <span className="text-red-500">*</span>}
    </label>
    {children}
    {hint && <p className="text-xs text-gray-500 dark:text-gray-400">{hint}</p>}
  </div>
);

const TextField = ({ id, label, value, onChange, type = 'text', placeholder, icon: Icon, required, hint, ...props }) => (
  <Field label={label} htmlFor={id} hint={hint} required={required}>
    <div className="relative">
      {Icon && (
        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
          <Icon className="h-5 w-5 text-gray-400" aria-hidden="true" />
        </div>
      )}
      <input
        id={id}
        name={id}
        type={type}
        value={value ?? ''}
        onChange={onChange}
        placeholder={placeholder}
        className={`input-field ${Icon ? 'pl-10' : ''}`}
        {...props}
      />
    </div>
  </Field>
);

const SelectField = ({ id, label, value, onChange, options, required, hint }) => (
  <Field label={label} htmlFor={id} hint={hint} required={required}>
    <select id={id} name={id} value={value ?? ''} onChange={onChange} className="input-field">
      {options.map((option) => (
        <option key={option.value} value={option.value}>{option.label}</option>
      ))}
    </select>
  </Field>
);

/**
 * Interrupteur d'un réglage booléen. `role="switch"` + `aria-checked` sur un vrai `<button>` :
 * l'ancienne version reposait sur une case à cocher masquée dont l'état n'était porté que par
 * la couleur du curseur.
 */
const ToggleField = ({ id, label, description, checked, onChange, disabled = false }) => (
  <div className="flex items-start justify-between gap-4 p-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/30">
    <div className="min-w-0">
      <label htmlFor={id} className="font-medium text-gray-900 dark:text-gray-100 cursor-pointer">
        {label}
      </label>
      {description && <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{description}</p>}
    </div>
    <button
      id={id}
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative shrink-0 w-11 h-6 rounded-full transition-colors focus:outline-none focus:ring-4 focus:ring-primary-500/25 disabled:opacity-50 disabled:cursor-not-allowed ${
        checked ? 'bg-primary-600' : 'bg-gray-300 dark:bg-gray-600'
      }`}
    >
      <span
        className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${
          checked ? 'translate-x-5' : 'translate-x-0'
        }`}
      />
    </button>
  </div>
);

/** Bandeau d'information ou d'avertissement, aux teintes sémantiques de la charte. */
const Callout = ({ tone = 'info', icon: Icon = Info, title, children }) => {
  const tones = {
    info: 'bg-blue-50 border-blue-200 text-blue-900 dark:bg-blue-500/10 dark:border-blue-500/20 dark:text-blue-200',
    warning: 'bg-amber-50 border-amber-200 text-amber-900 dark:bg-amber-500/10 dark:border-amber-500/20 dark:text-amber-200',
  };
  return (
    <div className={`flex items-start gap-3 rounded-xl border p-4 ${tones[tone] || tones.info}`}>
      <Icon className="w-5 h-5 flex-shrink-0 mt-0.5" aria-hidden="true" />
      <div className="min-w-0 text-sm">
        {title && <p className="font-semibold mb-1">{title}</p>}
        {children}
      </div>
    </div>
  );
};

/** En-tête d'une rubrique : un seul niveau de titre pour les six panneaux. */
const SectionHeader = ({ title, description }) => (
  <div className="pb-5 border-b border-gray-200 dark:border-gray-700">
    <h2 className="section-title">{title}</h2>
    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{description}</p>
  </div>
);

const Settings = () => {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();

  const [activeSection, setActiveSection] = useState('company');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [settings, setSettings] = useState(EMPTY_SETTINGS);
  // Dernier état enregistré, référence du bandeau « modifications non enregistrées ».
  const [savedSettings, setSavedSettings] = useState(EMPTY_SETTINGS);

  const [pendingImport, setPendingImport] = useState(null);
  const [importing, setImporting] = useState(false);
  const importInputRef = useRef(null);

  useEffect(() => {
    fetchSettings();
  }, []);

  const fetchSettings = async () => {
    setLoading(true);
    try {
      const { data } = await api.get('/settings');
      const loaded = { ...EMPTY_SETTINGS, ...data };
      setSettings(loaded);
      setSavedSettings(loaded);
      i18n.changeLanguage(loaded.language);
      localStorage.setItem('language', loaded.language);
    } catch (error) {
      console.error('Error fetching settings:', error);
      // Un 401 est déjà traité par l'intercepteur axios.
      if (error.response?.status !== 401) {
        toast.error(extractErrorMessage(error));
      }
    } finally {
      setLoading(false);
    }
  };

  const setValue = (key, value) => setSettings((prev) => ({ ...prev, [key]: value }));

  const handleText = (key) => (e) => setValue(key, e.target.value);

  const isDirty = useMemo(
    () => EDITABLE_KEYS.some((key) => String(settings[key] ?? '') !== String(savedSettings[key] ?? '')),
    [settings, savedSettings]
  );

  /**
   * Contrôles repris des contraintes de `SettingsRequest`. Le premier défaut rencontré ouvre
   * sa rubrique : sans cela, un 400 renvoyé par le serveur désignait un champ situé dans un
   * panneau fermé, que l'utilisateur devait retrouver lui-même.
   */
  const validate = () => {
    const required = [
      ['companyName', 'company', "Le nom de l'entreprise"],
      ['invoicePrefix', 'billing', 'Le préfixe des factures'],
    ];
    for (const [key, section, label] of required) {
      if (!String(settings[key] ?? '').trim()) {
        return { section, message: `${label} est obligatoire` };
      }
    }

    const numbers = [
      ['taxRate', 'billing', 'Le taux de TVA'],
      ['invoiceNumberStart', 'billing', 'Le numéro de départ des factures'],
      ['paymentTerms', 'billing', 'Le délai de paiement'],
      ['lowStockThreshold', 'notifications', "Le seuil d'alerte de stock"],
    ];
    for (const [key, section, label] of numbers) {
      const value = Number(settings[key]);
      if (String(settings[key] ?? '').trim() === '' || !Number.isFinite(value) || value < 0) {
        return { section, message: `${label} doit être un nombre positif ou nul` };
      }
    }
    return null;
  };

  const buildPayload = () => {
    const payload = {};
    EDITABLE_KEYS.forEach((key) => {
      payload[key] = NUMERIC_KEYS.includes(key) ? Number(settings[key]) : settings[key];
    });
    return payload;
  };

  const handleSave = async () => {
    const invalid = validate();
    if (invalid) {
      setActiveSection(invalid.section);
      toast.error(invalid.message);
      return;
    }

    const toastId = 'settings-save';
    setSaving(true);
    toast.loading(t('orders.steps.saving'), { id: toastId });
    try {
      const { data } = await api.put('/settings', buildPayload());
      const saved = { ...EMPTY_SETTINGS, ...data };
      setSettings(saved);
      setSavedSettings(saved);

      // react-i18next rerend les composants abonnés : la langue change sans recharger la
      // fenêtre, ce que faisait l'écran après une temporisation d'une seconde.
      i18n.changeLanguage(saved.language);
      localStorage.setItem('language', saved.language);

      // Les écrans de facturation lisent les réglages via un cache de session : sans cette
      // invalidation, ils continueraient de proposer l'ancien taux de TVA jusqu'au rechargement.
      invalidateSettingsCache();

      toast.success(t('settings.saved'), { id: toastId });
    } catch (error) {
      console.error('Error saving settings:', error);
      toast.error(extractErrorMessage(error), { id: toastId });
    } finally {
      setSaving(false);
    }
  };

  const handleDiscard = () => setSettings(savedSettings);

  /* ---- Données ---- */

  const handleExport = async (dataset) => {
    const toastId = `export-${dataset.type}`;
    toast.loading(t('settings.exporting', { dataset: t(`settings.datasets.${dataset.type}.label`) }), { id: toastId });
    try {
      const response = await api.get(`/${dataset.type}/export`, { responseType: 'blob' });
      const url = URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.download = `${dataset.type}_${new Date().toISOString().split('T')[0]}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      toast.success(t('settings.exported', { dataset: t(`settings.datasets.${dataset.type}.label`) }), { id: toastId });
    } catch (error) {
      console.error(`Error exporting ${dataset.type}:`, error);
      toast.error(extractErrorMessage(error), { id: toastId });
    }
  };

  const handleFilePicked = (event) => {
    const file = event.target.files?.[0];
    if (file) setPendingImport({ type: 'products', file });
  };

  const closeImportModal = () => {
    setPendingImport(null);
    if (importInputRef.current) importInputRef.current.value = '';
  };

  const handleImport = async () => {
    if (!pendingImport) return;
    const { type, file } = pendingImport;
    const toastId = `import-${type}`;
    const label = t(`settings.datasets.${type}.label`);

    setImporting(true);
    toast.loading(t('settings.importing', { dataset: label }), { id: toastId });
    try {
      const formData = new FormData();
      formData.append('file', file);
      // L'intercepteur axios ajoute l'en-tête d'autorisation.
      const { data } = await api.post(`/${type}/import`, formData);
      toast.success(data?.message || t('settings.imported', { dataset: label }), { id: toastId });
      closeImportModal();
    } catch (error) {
      console.error(`Error importing ${type}:`, error);
      toast.error(extractErrorMessage(error), { id: toastId });
    } finally {
      setImporting(false);
    }
  };

  /* ---- Panneaux ---- */

  const renderCompany = () => (
    <>
      <SectionHeader
        title={t('settings.companyInfoTitle')}
        description={t('settings.companyInfoHint')}
      />
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-6">
        <TextField
          id="companyName"
          label={t('settings.companyNameLabel')}
          value={settings.companyName}
          onChange={handleText('companyName')}
          placeholder={t('settings.companyNamePlaceholder')}
          icon={Building2}
          required
          maxLength={200}
        />
        <TextField
          id="companyTaxId"
          label={t('settings.taxIdLabel')}
          value={settings.companyTaxId}
          onChange={handleText('companyTaxId')}
          placeholder="BE0123456789"
          icon={FileText}
          maxLength={50}
        />
        <TextField
          id="companyEmail"
          label={t('settings.companyEmailLabel')}
          type="email"
          value={settings.companyEmail}
          onChange={handleText('companyEmail')}
          placeholder={t('settings.companyEmailPlaceholder')}
          icon={Mail}
          maxLength={100}
        />
        <TextField
          id="companyPhone"
          label={t('common.phone')}
          type="tel"
          value={settings.companyPhone}
          onChange={handleText('companyPhone')}
          placeholder={t('settings.companyPhonePlaceholder')}
          icon={Phone}
          maxLength={30}
        />
        <div className="md:col-span-2">
          <TextField
            id="companyAddress"
            label={t('common.address')}
            value={settings.companyAddress}
            onChange={handleText('companyAddress')}
            placeholder={t('settings.companyAddressPlaceholder')}
            icon={MapPin}
            maxLength={255}
          />
        </div>
        <TextField
          id="companyPostalCode"
          label={t('settings.postalCodeLabel')}
          value={settings.companyPostalCode}
          onChange={handleText('companyPostalCode')}
          placeholder="1000"
          maxLength={20}
        />
        <TextField
          id="companyCity"
          label={t('settings.cityLabel')}
          value={settings.companyCity}
          onChange={handleText('companyCity')}
          placeholder={t('clients.cityPlaceholder')}
          maxLength={100}
        />
        <TextField
          id="companyCountry"
          label={t('settings.countryLabel')}
          value={settings.companyCountry}
          onChange={handleText('companyCountry')}
          placeholder={t('clients.countryPlaceholder')}
          maxLength={100}
        />
      </div>

      <div className="pt-8 space-y-4">
        <h3 className="subsection-title">{t('settings.bankDetailsTitle')}</h3>
        <p className="text-sm text-gray-500 dark:text-gray-400 -mt-2">
          {t('settings.bankDetailsHint')}
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <TextField
            id="companyIban"
            label="IBAN"
            value={settings.companyIban}
            onChange={handleText('companyIban')}
            placeholder="BE68 5390 0754 7034"
            icon={CreditCard}
            maxLength={50}
          />
          <TextField
            id="companyBic"
            label="BIC"
            value={settings.companyBic}
            onChange={handleText('companyBic')}
            placeholder="GKCCBEBB"
            maxLength={20}
          />
        </div>
      </div>
    </>
  );

  const renderBilling = () => (
    <>
      <SectionHeader
        title={t('settings.billingSettingsTitle')}
        description={t('settings.billingSettingsHint')}
      />
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-6">
        <TextField
          id="taxRate"
          label={t('settings.taxRateLabel')}
          type="number"
          value={settings.taxRate}
          onChange={handleText('taxRate')}
          placeholder="21"
          min="0"
          step="0.01"
          required
          hint={t('settings.taxRateHint')}
        />
        <TextField
          id="paymentTerms"
          label={t('settings.paymentTermsLabel')}
          type="number"
          value={settings.paymentTerms}
          onChange={handleText('paymentTerms')}
          placeholder="30"
          min="0"
          required
          hint={t('settings.paymentTermsHint')}
        />
        <TextField
          id="invoicePrefix"
          label={t('settings.invoicePrefixLabel')}
          value={settings.invoicePrefix}
          onChange={handleText('invoicePrefix')}
          placeholder="INV"
          maxLength={10}
          required
        />
        <TextField
          id="invoiceNumberStart"
          label={t('settings.invoiceStartNumberLabel')}
          type="number"
          value={settings.invoiceNumberStart}
          onChange={handleText('invoiceNumberStart')}
          placeholder="1000"
          min="0"
          required
        />
        <div className="md:col-span-2">
          <Field label={t('settings.invoiceFooterLabel')} htmlFor="footerText">
            <textarea
              id="footerText"
              name="footerText"
              rows={3}
              value={settings.footerText ?? ''}
              onChange={handleText('footerText')}
              placeholder={t('settings.footerPlaceholder')}
              className="input-field"
            />
          </Field>
        </div>
      </div>

      <div className="pt-6">
        <Callout title={t('settings.numberingPreviewTitle')}>
          <Trans
            i18nKey="settings.numberingPreviewText"
            values={{
              number: `${settings.invoicePrefix || 'INV'}-${settings.invoiceNumberStart || 0}`,
            }}
            components={{ b: <span className="font-semibold tabular-nums" /> }}
          />
        </Callout>
      </div>
    </>
  );

  const renderGeneral = () => (
    <>
      <SectionHeader
        title={t('settings.generalSettingsTitle')}
        description={t('settings.generalSettingsHint')}
      />
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-6">
        <SelectField
          id="language"
          label={t('settings.languageLabel')}
          value={settings.language}
          onChange={handleText('language')}
          options={LANGUAGES}
          required
          hint={t('settings.languageHint')}
        />
        <SelectField
          id="currency"
          label={t('settings.currencyLabel')}
          value={settings.currency}
          onChange={handleText('currency')}
          options={CURRENCIES.map((c) => ({ ...c, label: t(c.labelKey) }))}
          required
        />
        <SelectField
          id="timezone"
          label={t('settings.timezoneLabel')}
          value={settings.timezone}
          onChange={handleText('timezone')}
          options={TIMEZONES.map((z) => ({ ...z, label: t(z.labelKey) }))}
          required
        />
        <SelectField
          id="dateFormat"
          label={t('settings.dateFormatLabel')}
          value={settings.dateFormat}
          onChange={handleText('dateFormat')}
          options={DATE_FORMATS.map((f) => ({ ...f, label: t(f.labelKey) }))}
          required
        />
      </div>

      <div className="pt-8 space-y-4">
        <h3 className="subsection-title">{t('settings.themeLabel')}</h3>
        <div role="radiogroup" aria-label={t('settings.themeLabel')} className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <button
            type="button"
            role="radio"
            aria-checked={settings.theme !== 'dark'}
            onClick={() => setValue('theme', 'light')}
            className={`flex items-start gap-3 p-4 rounded-xl border-2 text-left transition-colors ${
              settings.theme !== 'dark'
                ? 'border-primary-600 bg-primary-50 dark:bg-primary-500/10'
                : 'border-gray-200 dark:border-gray-700 hover:border-gray-300'
            }`}
          >
            <Sun className="w-5 h-5 text-primary-600 flex-shrink-0 mt-0.5" aria-hidden="true" />
            <span className="min-w-0">
              <span className="block font-medium text-gray-900 dark:text-gray-100">{t('settings.lightThemeLabel')}</span>
              <span className="block text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                {t('settings.lightThemeHint')}
              </span>
            </span>
            {settings.theme !== 'dark' && (
              <Check className="w-5 h-5 text-primary-600 ml-auto flex-shrink-0" aria-hidden="true" />
            )}
          </button>

          {/* Le réglage est bien persisté côté serveur, mais aucun composant ne l'applique
              encore (le hook `useTheme` n'est branché nulle part) : le proposer comme un choix
              actif afficherait « sombre » sans que rien ne change à l'écran. */}
          <div className="flex items-start gap-3 p-4 rounded-xl border-2 border-dashed border-gray-200 dark:border-gray-700 opacity-70">
            <Moon className="w-5 h-5 text-gray-400 flex-shrink-0 mt-0.5" aria-hidden="true" />
            <div className="min-w-0">
              <p className="font-medium text-gray-700 dark:text-gray-300">{t('settings.darkThemeLabel')}</p>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                {t('settings.darkThemeHint')}
              </p>
            </div>
          </div>
        </div>
      </div>
    </>
  );

  const renderNotifications = () => (
    <>
      <SectionHeader
        title={t('notifications.title')}
        description={t('settings.notificationsHint')}
      />
      <div className="space-y-3 pt-6">
        <ToggleField
          id="notifications"
          label={t('settings.pushNotificationsLabel')}
          description={t('settings.pushNotificationsHint')}
          checked={!!settings.notifications}
          onChange={(v) => setValue('notifications', v)}
        />
        <ToggleField
          id="emailNotifications"
          label={t('settings.emailNotificationsLabel')}
          description={t('settings.emailNotificationsHint')}
          checked={!!settings.emailNotifications}
          onChange={(v) => setValue('emailNotifications', v)}
        />
        <ToggleField
          id="orderNotifications"
          label={t('nav.orders')}
          description={t('settings.orderNotificationsHint')}
          checked={!!settings.orderNotifications}
          onChange={(v) => setValue('orderNotifications', v)}
        />
        <ToggleField
          id="stockAlerts"
          label={t('settings.stockAlertsLabel')}
          description={t('settings.stockAlertsHint')}
          checked={!!settings.stockAlerts}
          onChange={(v) => setValue('stockAlerts', v)}
        />
      </div>

      <div className="pt-8 border-t border-gray-200 dark:border-gray-700 mt-8">
        <div className="max-w-xs">
          <TextField
            id="lowStockThreshold"
            label={t('settings.lowStockThresholdLabel')}
            type="number"
            value={settings.lowStockThreshold}
            onChange={handleText('lowStockThreshold')}
            placeholder="10"
            min="0"
            required
            hint={t('settings.lowStockThresholdHint')}
          />
        </div>
      </div>
    </>
  );

  const renderData = () => (
    <>
      <SectionHeader
        title={t('settings.dataTitle')}
        description={t('settings.dataHint')}
      />

      <div className="pt-6 space-y-4">
        <Callout title={t('settings.beforeImportTitle')}>
          <ul className="space-y-1">
            <li>{t('settings.beforeImport.format')}</li>
            <li>{t('settings.beforeImport.overwrite')}</li>
            <li>{t('settings.beforeImport.template')}</li>
          </ul>
        </Callout>

        <ul className="divide-y divide-gray-100 dark:divide-gray-700/60 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          {DATASETS.map((dataset) => {
            const Icon = dataset.icon;
            return (
              <li
                key={dataset.type}
                className="flex flex-wrap items-center justify-between gap-4 p-4 bg-white dark:bg-gray-800"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-10 h-10 rounded-xl bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-200 flex items-center justify-center flex-shrink-0">
                    <Icon className="w-5 h-5" aria-hidden="true" />
                  </div>
                  <div className="min-w-0">
                    <p className="font-medium text-gray-900 dark:text-gray-100">
                      {t(`settings.datasets.${dataset.type}.label`)}
                    </p>
                    <p className="text-sm text-gray-500 dark:text-gray-400 truncate">
                      {t(`settings.datasets.${dataset.type}.description`)}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2 ml-auto">
                  <Button variant="secondary" size="sm" icon={Download} onClick={() => handleExport(dataset)}>
                    {t('common.export')}
                  </Button>
                  {/* Seuls les produits disposent d'une route d'import côté API. */}
                  {dataset.importable && (
                    <Button
                      variant="primary"
                      size="sm"
                      icon={Upload}
                      onClick={() => importInputRef.current?.click()}
                    >
                      {t('common.import')}
                    </Button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>

        <p className="text-xs text-gray-500 dark:text-gray-400">
          {t('settings.importProductsOnly')}
        </p>

        <input
          ref={importInputRef}
          type="file"
          accept=".csv"
          onChange={handleFilePicked}
          className="hidden"
        />
      </div>
    </>
  );

  const renderSecurity = () => (
    <>
      <SectionHeader
        title={t('profile.tabs.security')}
        description={t('settings.securityHint')}
      />

      <div className="pt-6 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-4 p-5 rounded-xl border border-gray-200 dark:border-gray-700">
          <div className="flex items-start gap-3 min-w-0">
            <div className="w-10 h-10 rounded-xl bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-200 flex items-center justify-center flex-shrink-0">
              <KeyRound className="w-5 h-5" aria-hidden="true" />
            </div>
            <div className="min-w-0">
              <p className="font-medium text-gray-900 dark:text-gray-100">{t('auth.password')}</p>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                {t('settings.passwordHint')}
              </p>
            </div>
          </div>
          {/* Le bouton n'avait aucune action associée : il mène désormais à l'écran qui
              effectue réellement le changement. */}
          <Button variant="secondary" onClick={() => navigate('/profile')}>
            {t('settings.openProfile')}
          </Button>
        </div>

        {/* Règles reprises de `UserService.validatePassword` : ce sont celles que le serveur
            applique réellement à l'enregistrement. */}
        <div className="p-5 rounded-xl border border-gray-200 dark:border-gray-700">
          <h3 className="subsection-title">{t('settings.passwordRulesTitle')}</h3>
          <ul className="mt-3 space-y-2">
            {[
              t('profile.rules.length'),
              t('settings.passwordRules.upper'),
              t('settings.passwordRules.lower'),
              t('settings.passwordRules.digit'),
            ].map((rule) => (
              <li key={rule} className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                <Check className="w-4 h-4 text-green-600 dark:text-green-400 flex-shrink-0" aria-hidden="true" />
                {rule}
              </li>
            ))}
          </ul>
        </div>

        <Callout tone="warning" icon={AlertTriangle} title={t('settings.bestPracticesTitle')}>
          <ul className="space-y-1">
            <li>{t('settings.bestPractices.noSharing')}</li>
            <li>{t('settings.bestPractices.namedAccounts')}</li>
            <li>{t('settings.bestPractices.signOut')}</li>
          </ul>
        </Callout>
      </div>
    </>
  );

  const PANELS = {
    company: renderCompany,
    billing: renderBilling,
    general: renderGeneral,
    notifications: renderNotifications,
    data: renderData,
    security: renderSecurity,
  };

  return (
    <div className="space-y-6">
      {/* ---- En-tête ---- */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <SettingsIcon aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('nav.settings')}</h1>
            <p className="page-subtitle">{t('settings.subtitle')}</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* ---- Navigation des rubriques ----
         * Verticale et groupée sur grand écran, défilante horizontalement en dessous : sept
         * onglets alignés débordaient de la largeur utile dès que la barre latérale était
         * ouverte, et les derniers n'étaient atteignables qu'en faisant défiler à l'aveugle. */}
        <nav className="lg:col-span-1" aria-label={t('settings.sectionsNavLabel')}>
          <div className="card p-3 lg:sticky lg:top-6">
            <div
              role="tablist"
              aria-orientation="vertical"
              className="flex lg:flex-col gap-1 overflow-x-auto lg:overflow-visible"
            >
              {GROUPS.map((group) => (
                // `presentation` : le regroupement est visuel, seuls les boutons doivent être
                // exposés comme onglets de la liste.
                <div key={group} role="presentation" className="contents lg:block">
                  <p className="hidden lg:block px-3 pt-3 pb-1 text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                    {t(`settings.groups.${group}`)}
                  </p>
                  {SECTIONS.filter((section) => section.group === group).map((section) => {
                    const Icon = section.icon;
                    const selected = activeSection === section.id;
                    return (
                      <button
                        key={section.id}
                        id={`settings-tab-${section.id}`}
                        role="tab"
                        type="button"
                        aria-selected={selected}
                        aria-controls={`settings-panel-${section.id}`}
                        onClick={() => setActiveSection(section.id)}
                        className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium whitespace-nowrap transition-colors ${
                          selected
                            ? 'bg-primary-50 text-primary-700 dark:bg-primary-500/15 dark:text-primary-200'
                            : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700/40 hover:text-gray-900 dark:hover:text-gray-100'
                        }`}
                      >
                        <Icon className="w-5 h-5 flex-shrink-0" aria-hidden="true" />
                        <span className="min-w-0 text-left">
                          <span className="block">{t(`settings.sections.${section.id}.label`)}</span>
                          <span className="hidden lg:block text-xs font-normal text-gray-500 dark:text-gray-400 truncate">
                            {t(`settings.sections.${section.id}.hint`)}
                          </span>
                        </span>
                      </button>
                    );
                  })}
                </div>
              ))}
            </div>
          </div>
        </nav>

        {/* ---- Panneau de la rubrique ---- */}
        <div className="lg:col-span-3">
          {loading ? (
            <div className="card space-y-4">
              <div className="skeleton h-6 w-56" />
              <div className="skeleton h-4 w-80" />
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4">
                {Array.from({ length: 6 }).map((_, index) => (
                  <div key={index} className="space-y-2">
                    <div className="skeleton h-4 w-32" />
                    <div className="skeleton h-11 w-full" />
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <motion.section
              key={activeSection}
              id={`settings-panel-${activeSection}`}
              role="tabpanel"
              aria-labelledby={`settings-tab-${activeSection}`}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              className="card"
            >
              {PANELS[activeSection]()}
            </motion.section>
          )}
        </div>
      </div>

      {/* ---- Barre d'enregistrement ----
       * Un seul PUT enregistre toutes les rubriques : l'action est donc globale et non propre
       * à un panneau. La barre colle au bas de la fenêtre et n'apparaît qu'en cas de
       * modification, ce qui la rend atteignable au bas d'un formulaire long. */}
      {isDirty && !loading && (
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          className="sticky bottom-0 z-20 -mx-6 px-6 py-4 bg-white/95 dark:bg-gray-800/95 backdrop-blur border-t border-gray-200 dark:border-gray-700 flex flex-wrap items-center justify-between gap-3"
          role="status"
        >
          <p className="text-sm font-medium text-amber-700 dark:text-amber-400">
            {t('profile.unsavedChanges')}
          </p>
          <div className="flex items-center gap-3">
            <Button variant="secondary" icon={RotateCcw} onClick={handleDiscard} disabled={saving}>
              {t('common.cancel')}
            </Button>
            <Button variant="primary" icon={Save} onClick={handleSave} loading={saving}>
              {t('common.save')}
            </Button>
          </div>
        </motion.div>
      )}

      {/* ---- Confirmation d'import ---- */}
      <Modal
        isOpen={!!pendingImport}
        onClose={closeImportModal}
        title={t('settings.confirmImportTitle')}
        size="md"
      >
        {pendingImport && (
          <div className="space-y-6">
            <dl className="grid grid-cols-1 sm:grid-cols-3 gap-4 p-4 rounded-xl bg-gray-50 dark:bg-gray-900/30">
              <div>
                <dt className="text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">{t('settings.datasetLabel')}</dt>
                <dd className="text-sm font-medium text-gray-900 dark:text-gray-100 mt-0.5">
                  {t(`settings.datasets.${pendingImport.type}.label`)}
                </dd>
              </div>
              <div className="min-w-0">
                <dt className="text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">{t('settings.fileLabel')}</dt>
                <dd className="text-sm font-medium text-gray-900 dark:text-gray-100 mt-0.5 truncate">
                  {pendingImport.file?.name}
                </dd>
              </div>
              <div>
                <dt className="text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">{t('settings.sizeLabel')}</dt>
                <dd className="text-sm font-medium text-gray-900 dark:text-gray-100 mt-0.5 tabular-nums">
                  {t('settings.sizeInKb', { size: (pendingImport.file?.size / 1024).toFixed(1) })}
                </dd>
              </div>
            </dl>

            <Callout tone="warning" icon={AlertTriangle} title={t('settings.importWarningTitle')}>
              <ul className="space-y-1">
                <li>{t('settings.importWarning.overwrite')}</li>
                <li>{t('settings.importWarning.irreversible')}</li>
              </ul>
            </Callout>

            <div className="flex flex-wrap items-center justify-end gap-3 pt-2 border-t border-gray-200 dark:border-gray-700">
              <Button variant="secondary" onClick={closeImportModal} disabled={importing}>
                {t('common.cancel')}
              </Button>
              <Button variant="primary" icon={Upload} onClick={handleImport} loading={importing}>
                {t('settings.startImport')}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default Settings;
