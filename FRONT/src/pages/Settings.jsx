import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Save, Globe, Bell, Lock, Database, Palette, Download, Upload,
  FileText, Package, ShoppingCart, Truck, Warehouse, Users, Settings as SettingsIcon,
  Building2, Mail, Phone, MapPin, CreditCard, AlertCircle, Check, Info, X
} from 'lucide-react';
import { motion } from 'framer-motion';
import { toast } from 'react-hot-toast';
import api from '../services/api';
import Modal from '../components/Modal';

const Settings = () => {
  const { t, i18n } = useTranslation();
  const [activeTab, setActiveTab] = useState('general');
  const [loading, setLoading] = useState(true);
  const [showImportModal, setShowImportModal] = useState(false);
  const [importData, setImportData] = useState({ type: '', file: null });
  const [settings, setSettings] = useState({
    // Général
    language: i18n.language,
    currency: 'EUR',
    timezone: 'Europe/Paris',
    dateFormat: 'DD/MM/YYYY',

    // Entreprise
    companyName: 'GESCOM',
    companyEmail: 'contact@gescom.com',
    companyPhone: '+33 1 23 45 67 89',
    companyAddress: '123 Rue de Commerce',
    companyCity: 'Paris',
    companyPostalCode: '75001',
    companyCountry: 'France',
    companyTaxId: 'FR12345678901',

    // Facturation
    taxRate: 20,
    invoicePrefix: 'INV',
    invoiceNumberStart: 1000,
    paymentTerms: 30,
    footerText: 'Merci pour votre confiance',

    // Notifications
    notifications: true,
    emailNotifications: true,
    orderNotifications: true,
    stockAlerts: true,
    lowStockThreshold: 10,

    // Apparence
    theme: 'light'
  });

  useEffect(() => {
    fetchSettings();
  }, []);

  const fileInputRefs = {
    clients: useRef(null),
    products: useRef(null),
    orders: useRef(null),
    deliveries: useRef(null),
    stock: useRef(null)
  };

  const fetchSettings = async () => {
    setLoading(true);
    try {
      const response = await api.get('/settings');
      setSettings(response.data);
      // Apply language setting
      i18n.changeLanguage(response.data.language);
      localStorage.setItem('language', response.data.language);
    } catch (error) {
      console.error('Error fetching settings:', error);
      // Don't show error toast if it's a 401 - axios interceptor will handle it
      if (error.response?.status !== 401) {
        toast.error('❌ Erreur lors du chargement des paramètres');
      }
      // Keep using default settings from state
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    try {
      toast.loading('Enregistrement en cours...');

      // Check if language changed
      const currentLanguage = i18n.language;
      const languageChanged = currentLanguage !== settings.language;

      const response = await api.put('/settings', settings);

      // Apply language setting immediately
      i18n.changeLanguage(settings.language);
      localStorage.setItem('language', settings.language);

      toast.dismiss();
      toast.success('✅ Paramètres sauvegardés avec succès !');

      // Refresh settings to get updated data
      setSettings(response.data);

      // Reload page if language changed to ensure all components update
      if (languageChanged) {
        setTimeout(() => {
          window.location.reload();
        }, 1000);
      }
    } catch (error) {
      toast.dismiss();
      console.error('Error saving settings:', error);
      const errorMessage = error.response?.data?.message || 'Erreur lors de la sauvegarde des paramètres';
      toast.error('❌ ' + errorMessage);
    }
  };

  const handleExport = async (type) => {
    try {
      toast.loading(`Export des ${type} en cours...`);

      const response = await api.get(`/${type}/export`, {
        responseType: 'blob'
      });

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${type}_${new Date().toISOString().split('T')[0]}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();

      toast.dismiss();
      toast.success(`✅ ${type.charAt(0).toUpperCase() + type.slice(1)} exportés avec succès !`);
    } catch (error) {
      toast.dismiss();
      console.error(`Error exporting ${type}:`, error);
      toast.error(`❌ Erreur lors de l'export des ${type}`);
    }
  };

  const handleImport = async () => {
    const { type, file } = importData;
    if (!file) return;

    setShowImportModal(false);

    const formData = new FormData();
    formData.append('file', file);

    try {
      toast.loading(`Import des ${type} en cours...`);

      // Debug: Check if token exists
      const token = localStorage.getItem('token');
      console.log('Token exists in localStorage:', !!token);
      console.log('Token preview:', token ? token.substring(0, 20) + '...' : 'null');

      // Let axios interceptor add the Authorization header automatically
      const response = await api.post(`/${type}/import`, formData);

      toast.dismiss();
      const message = response.data.message || `${type.charAt(0).toUpperCase() + type.slice(1)} importés avec succès !`;
      toast.success(`✅ ${message}`);

      if (fileInputRefs[type].current) {
        fileInputRefs[type].current.value = '';
      }
    } catch (error) {
      toast.dismiss();
      console.error(`Error importing ${type}:`, error);
      const errorMsg = error.response?.data?.message || error.message || 'Erreur inconnue';
      toast.error(`❌ Erreur lors de l'import des ${type}: ${errorMsg}`);
    }
  };

  const handleFileChange = (type, event) => {
    const file = event.target.files[0];
    if (file) {
      setImportData({ type, file });
      setShowImportModal(true);
    }
  };

  const handleCancelImport = () => {
    setShowImportModal(false);
    const type = importData.type;
    if (fileInputRefs[type]?.current) {
      fileInputRefs[type].current.value = '';
    }
    setImportData({ type: '', file: null });
  };

  const tabs = [
    { id: 'general', label: 'Général', icon: Globe },
    { id: 'company', label: 'Entreprise', icon: Building2 },
    { id: 'billing', label: 'Facturation', icon: CreditCard },
    { id: 'notifications', label: 'Notifications', icon: Bell },
    { id: 'data', label: 'Données', icon: Database },
    { id: 'security', label: 'Sécurité', icon: Lock },
    { id: 'appearance', label: 'Apparence', icon: Palette }
  ];

  const InputField = ({ label, value, onChange, type = 'text', placeholder, icon: Icon, required = false }) => (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-2">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      <div className="relative">
        {Icon && (
          <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
            <Icon className="h-5 w-5 text-gray-400" />
          </div>
        )}
        <input
          type={type}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          className={`input-field ${Icon ? 'pl-10' : ''}`}
        />
      </div>
    </div>
  );

  const SelectField = ({ label, value, onChange, options, required = false }) => (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-2">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      <select value={value} onChange={onChange} className="input-field">
        {options.map(opt => (
          <option key={opt.value} value={opt.value}>{opt.label}</option>
        ))}
      </select>
    </div>
  );

  const ToggleField = ({ label, description, checked, onChange }) => (
    <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
      <div className="flex-1">
        <p className="font-medium text-gray-900">{label}</p>
        {description && <p className="text-sm text-gray-600 mt-1">{description}</p>}
      </div>
      <label className="relative inline-flex items-center cursor-pointer">
        <input
          type="checkbox"
          checked={checked}
          onChange={onChange}
          className="sr-only peer"
        />
        <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-primary-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"></div>
      </label>
    </div>
  );

  const DataExportCard = ({ icon: Icon, iconColor, title, description, type }) => (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="border border-gray-200 rounded-xl p-6 hover:shadow-lg transition-all"
    >
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-start gap-4">
          <div className={`w-12 h-12 ${iconColor} rounded-xl flex items-center justify-center flex-shrink-0`}>
            <Icon className="w-6 h-6 text-white" />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900 text-lg">{title}</h3>
            <p className="text-sm text-gray-600 mt-1">{description}</p>
          </div>
        </div>
      </div>
      <div className="flex flex-wrap gap-3">
        <button
          onClick={() => handleExport(type)}
          className="flex-1 sm:flex-none btn-secondary flex items-center justify-center gap-2"
        >
          <Download className="w-4 h-4" />
          Exporter
        </button>
        <button
          onClick={() => fileInputRefs[type].current?.click()}
          className="flex-1 sm:flex-none btn-primary flex items-center justify-center gap-2"
        >
          <Upload className="w-4 h-4" />
          Importer
        </button>
        <input
          ref={fileInputRefs[type]}
          type="file"
          accept=".csv,.xlsx"
          onChange={(e) => handleFileChange(type, e)}
          className="hidden"
        />
      </div>
    </motion.div>
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
          <p className="text-gray-600 mt-4">Chargement des paramètres...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-3">
            <SettingsIcon className="w-8 h-8 text-primary-600" />
            {t('nav.settings')}
          </h1>
          <p className="text-gray-600 mt-2">Gérez tous les paramètres de votre application</p>
        </div>
        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          onClick={handleSave}
          className="btn-primary px-6 py-3 flex items-center gap-2"
        >
          <Save className="w-5 h-5" />
          Enregistrer
        </motion.button>
      </div>

      {/* Tabs Navigation */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <div className="flex overflow-x-auto scrollbar-hide">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center gap-2 px-6 py-4 font-medium whitespace-nowrap border-b-2 transition-colors ${
                  activeTab === tab.id
                    ? 'border-primary-600 text-primary-600 bg-primary-50'
                    : 'border-transparent text-gray-600 hover:text-gray-900 hover:bg-gray-50'
                }`}
              >
                <Icon className="w-5 h-5" />
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Tab Content */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        {/* Général */}
        {activeTab === 'general' && (
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            className="space-y-6"
          >
            <div>
              <h2 className="text-xl font-bold text-gray-900 mb-1">Paramètres généraux</h2>
              <p className="text-gray-600">Configurez les paramètres de base de l'application</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <SelectField
                label="Langue de l'interface"
                value={settings.language}
                onChange={(e) => setSettings({ ...settings, language: e.target.value })}
                options={[
                  { value: 'fr', label: '🇫🇷 Français' },
                  { value: 'en', label: '🇬🇧 English' },
                  { value: 'nl', label: '🇳🇱 Nederlands' }
                ]}
                required
              />

              <SelectField
                label="Devise par défaut"
                value={settings.currency}
                onChange={(e) => setSettings({ ...settings, currency: e.target.value })}
                options={[
                  { value: 'EUR', label: 'Euro (€)' },
                  { value: 'USD', label: 'Dollar ($)' },
                  { value: 'GBP', label: 'Livre (£)' }
                ]}
                required
              />

              <SelectField
                label="Fuseau horaire"
                value={settings.timezone}
                onChange={(e) => setSettings({ ...settings, timezone: e.target.value })}
                options={[
                  { value: 'Europe/Paris', label: 'Europe/Paris (GMT+1)' },
                  { value: 'Europe/London', label: 'Europe/London (GMT+0)' },
                  { value: 'America/New_York', label: 'America/New_York (GMT-5)' }
                ]}
              />

              <SelectField
                label="Format de date"
                value={settings.dateFormat}
                onChange={(e) => setSettings({ ...settings, dateFormat: e.target.value })}
                options={[
                  { value: 'DD/MM/YYYY', label: 'JJ/MM/AAAA' },
                  { value: 'MM/DD/YYYY', label: 'MM/JJ/AAAA' },
                  { value: 'YYYY-MM-DD', label: 'AAAA-MM-JJ' }
                ]}
              />
            </div>
          </motion.div>
        )}

        {/* Entreprise */}
        {activeTab === 'company' && (
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            className="space-y-6"
          >
            <div>
              <h2 className="text-xl font-bold text-gray-900 mb-1">Informations de l'entreprise</h2>
              <p className="text-gray-600">Ces informations apparaîtront sur vos factures et documents</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <InputField
                label="Nom de l'entreprise"
                value={settings.companyName}
                onChange={(e) => setSettings({ ...settings, companyName: e.target.value })}
                placeholder="GESCOM"
                icon={Building2}
                required
              />

              <InputField
                label="Email de l'entreprise"
                type="email"
                value={settings.companyEmail}
                onChange={(e) => setSettings({ ...settings, companyEmail: e.target.value })}
                placeholder="contact@gescom.com"
                icon={Mail}
                required
              />

              <InputField
                label="Téléphone"
                type="tel"
                value={settings.companyPhone}
                onChange={(e) => setSettings({ ...settings, companyPhone: e.target.value })}
                placeholder="+33 1 23 45 67 89"
                icon={Phone}
              />

              <InputField
                label="Numéro TVA"
                value={settings.companyTaxId}
                onChange={(e) => setSettings({ ...settings, companyTaxId: e.target.value })}
                placeholder="FR12345678901"
                icon={FileText}
              />

              <div className="md:col-span-2">
                <InputField
                  label="Adresse"
                  value={settings.companyAddress}
                  onChange={(e) => setSettings({ ...settings, companyAddress: e.target.value })}
                  placeholder="123 Rue de Commerce"
                  icon={MapPin}
                />
              </div>

              <InputField
                label="Ville"
                value={settings.companyCity}
                onChange={(e) => setSettings({ ...settings, companyCity: e.target.value })}
                placeholder="Paris"
              />

              <InputField
                label="Code postal"
                value={settings.companyPostalCode}
                onChange={(e) => setSettings({ ...settings, companyPostalCode: e.target.value })}
                placeholder="75001"
              />

              <InputField
                label="Pays"
                value={settings.companyCountry}
                onChange={(e) => setSettings({ ...settings, companyCountry: e.target.value })}
                placeholder="France"
              />
            </div>
          </motion.div>
        )}

        {/* Facturation */}
        {activeTab === 'billing' && (
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            className="space-y-6"
          >
            <div>
              <h2 className="text-xl font-bold text-gray-900 mb-1">Paramètres de facturation</h2>
              <p className="text-gray-600">Configurez vos factures et conditions de paiement</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <InputField
                label="Taux de TVA par défaut (%)"
                type="number"
                value={settings.taxRate}
                onChange={(e) => setSettings({ ...settings, taxRate: e.target.value })}
                placeholder="20"
                required
              />

              <InputField
                label="Préfixe des factures"
                value={settings.invoicePrefix}
                onChange={(e) => setSettings({ ...settings, invoicePrefix: e.target.value })}
                placeholder="INV"
              />

              <InputField
                label="Numéro de départ"
                type="number"
                value={settings.invoiceNumberStart}
                onChange={(e) => setSettings({ ...settings, invoiceNumberStart: e.target.value })}
                placeholder="1000"
              />

              <InputField
                label="Délai de paiement (jours)"
                type="number"
                value={settings.paymentTerms}
                onChange={(e) => setSettings({ ...settings, paymentTerms: e.target.value })}
                placeholder="30"
              />

              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Texte de pied de page des factures
                </label>
                <textarea
                  value={settings.footerText}
                  onChange={(e) => setSettings({ ...settings, footerText: e.target.value })}
                  placeholder="Merci pour votre confiance"
                  rows="3"
                  className="input-field"
                />
              </div>
            </div>

            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 flex items-start gap-3">
              <Info className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-medium text-blue-900 mb-1">Numérotation des factures</p>
                <p className="text-sm text-blue-800">
                  Les factures seront numérotées comme: {settings.invoicePrefix}-{settings.invoiceNumberStart}
                </p>
              </div>
            </div>
          </motion.div>
        )}

        {/* Notifications */}
        {activeTab === 'notifications' && (
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            className="space-y-6"
          >
            <div>
              <h2 className="text-xl font-bold text-gray-900 mb-1">Préférences de notification</h2>
              <p className="text-gray-600">Choisissez comment vous souhaitez être notifié</p>
            </div>

            <div className="space-y-3">
              <ToggleField
                label="Notifications push"
                description="Recevoir les notifications dans l'application"
                checked={settings.notifications}
                onChange={(e) => setSettings({ ...settings, notifications: e.target.checked })}
              />

              <ToggleField
                label="Notifications par email"
                description="Recevoir les notifications importantes par email"
                checked={settings.emailNotifications}
                onChange={(e) => setSettings({ ...settings, emailNotifications: e.target.checked })}
              />

              <ToggleField
                label="Notifications de commandes"
                description="Être notifié lors de nouvelles commandes ou modifications"
                checked={settings.orderNotifications}
                onChange={(e) => setSettings({ ...settings, orderNotifications: e.target.checked })}
              />

              <ToggleField
                label="Alertes de stock"
                description="Recevoir des alertes lorsque le stock est faible"
                checked={settings.stockAlerts}
                onChange={(e) => setSettings({ ...settings, stockAlerts: e.target.checked })}
              />
            </div>

            <div className="pt-4 border-t border-gray-200">
              <InputField
                label="Seuil d'alerte de stock bas"
                type="number"
                value={settings.lowStockThreshold}
                onChange={(e) => setSettings({ ...settings, lowStockThreshold: e.target.value })}
                placeholder="10"
              />
              <p className="text-sm text-gray-600 mt-2">
                Vous serez alerté lorsqu'un produit atteint ce niveau de stock
              </p>
            </div>
          </motion.div>
        )}

        {/* Données */}
        {activeTab === 'data' && (
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            className="space-y-6"
          >
            <div>
              <h2 className="text-xl font-bold text-gray-900 mb-1">Import / Export de données</h2>
              <p className="text-gray-600">Gérez vos données au format CSV ou Excel</p>
            </div>

            <div className="bg-gradient-to-r from-blue-50 to-cyan-50 border border-blue-200 rounded-xl p-4 flex items-start gap-3">
              <Info className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm font-medium text-blue-900 mb-2">Informations importantes</p>
                <ul className="text-sm text-blue-800 space-y-1">
                  <li className="flex items-center gap-2">
                    <Check className="w-4 h-4" />
                    Les fichiers doivent être au format CSV ou Excel (.csv, .xlsx)
                  </li>
                  <li className="flex items-center gap-2">
                    <Check className="w-4 h-4" />
                    Les exports sont horodatés automatiquement
                  </li>
                  <li className="flex items-center gap-2">
                    <AlertCircle className="w-4 h-4" />
                    L'import écrase les données existantes avec le même identifiant
                  </li>
                </ul>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4">
              <DataExportCard
                icon={Users}
                iconColor="bg-gradient-to-br from-blue-500 to-blue-600"
                title="Clients"
                description="Gérez votre base de données clients"
                type="clients"
              />

              <DataExportCard
                icon={Package}
                iconColor="bg-gradient-to-br from-purple-500 to-purple-600"
                title="Produits"
                description="Importez et exportez votre catalogue de produits"
                type="products"
              />

              <DataExportCard
                icon={ShoppingCart}
                iconColor="bg-gradient-to-br from-green-500 to-green-600"
                title="Commandes"
                description="Gérez l'historique de vos commandes"
                type="orders"
              />

              <DataExportCard
                icon={Truck}
                iconColor="bg-gradient-to-br from-orange-500 to-orange-600"
                title="Livraisons"
                description="Exportez vos données de livraison"
                type="deliveries"
              />

              <DataExportCard
                icon={Warehouse}
                iconColor="bg-gradient-to-br from-red-500 to-red-600"
                title="Stock"
                description="Gérez les mouvements de stock"
                type="stock"
              />
            </div>
          </motion.div>
        )}

        {/* Sécurité */}
        {activeTab === 'security' && (
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            className="space-y-6"
          >
            <div>
              <h2 className="text-xl font-bold text-gray-900 mb-1">Sécurité du compte</h2>
              <p className="text-gray-600">Protégez votre compte et vos données</p>
            </div>

            <div className="space-y-6">
              <div className="border border-gray-200 rounded-xl p-6">
                <div className="flex items-start gap-4 mb-4">
                  <div className="w-12 h-12 bg-red-100 rounded-xl flex items-center justify-center">
                    <Lock className="w-6 h-6 text-red-600" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900 text-lg">Mot de passe</h3>
                    <p className="text-sm text-gray-600 mt-1">
                      Dernière modification: il y a 30 jours
                    </p>
                  </div>
                </div>
                <button className="btn-secondary w-full sm:w-auto">
                  Changer le mot de passe
                </button>
              </div>

              <div className="bg-gradient-to-r from-yellow-50 to-orange-50 border border-yellow-200 rounded-xl p-4">
                <div className="flex items-start gap-3">
                  <AlertCircle className="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="text-sm font-medium text-yellow-900 mb-2">Recommandations de sécurité</p>
                    <ul className="text-sm text-yellow-800 space-y-1 list-disc list-inside">
                      <li>Changez votre mot de passe régulièrement (tous les 3 mois)</li>
                      <li>Utilisez un mot de passe complexe avec au moins 8 caractères</li>
                      <li>Ne partagez jamais vos identifiants</li>
                      <li>Déconnectez-vous après chaque session sur un ordinateur partagé</li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>
          </motion.div>
        )}

        {/* Apparence */}
        {activeTab === 'appearance' && (
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            className="space-y-6"
          >
            <div>
              <h2 className="text-xl font-bold text-gray-900 mb-1">Personnalisation de l'interface</h2>
              <p className="text-gray-600">Adaptez l'apparence selon vos préférences</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-4">
                Thème de l'application
              </label>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <motion.div
                  whileHover={{ scale: 1.02 }}
                  onClick={() => setSettings({ ...settings, theme: 'light' })}
                  className={`relative p-6 border-2 rounded-xl cursor-pointer bg-gradient-to-br from-white to-gray-50 ${
                    settings.theme === 'light' ? 'border-primary-600' : 'border-gray-300'
                  }`}
                >
                  {settings.theme === 'light' && (
                    <div className="absolute top-4 right-4">
                      <div className="w-6 h-6 bg-primary-600 rounded-full flex items-center justify-center">
                        <Check className="w-4 h-4 text-white" />
                      </div>
                    </div>
                  )}
                  <div className="w-12 h-12 bg-gradient-to-br from-primary-500 to-secondary-500 rounded-xl mb-4"></div>
                  <p className="font-semibold text-gray-900 text-lg">Thème Clair</p>
                  <p className="text-sm text-gray-600 mt-1">Interface lumineuse et moderne</p>
                </motion.div>

                <motion.div
                  whileHover={{ scale: 1.02 }}
                  onClick={() => setSettings({ ...settings, theme: 'dark' })}
                  className={`relative p-6 border-2 rounded-xl cursor-pointer bg-gradient-to-br from-gray-900 to-gray-800 ${
                    settings.theme === 'dark' ? 'border-primary-600' : 'border-gray-300 opacity-60'
                  }`}
                >
                  {settings.theme === 'dark' && (
                    <div className="absolute top-4 right-4">
                      <div className="w-6 h-6 bg-primary-600 rounded-full flex items-center justify-center">
                        <Check className="w-4 h-4 text-white" />
                      </div>
                    </div>
                  )}
                  <div className="w-12 h-12 bg-gradient-to-br from-gray-700 to-gray-600 rounded-xl mb-4"></div>
                  <p className="font-semibold text-white text-lg">Thème Sombre</p>
                  <p className="text-sm text-gray-300 mt-1">Bientôt disponible</p>
                  {settings.theme !== 'dark' && (
                    <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                      <span className="bg-gray-700 text-white px-4 py-2 rounded-full text-sm font-medium">
                        Prochainement
                      </span>
                    </div>
                  )}
                </motion.div>
              </div>
            </div>
          </motion.div>
        )}
      </div>

      {/* Import Confirmation Modal */}
      <Modal isOpen={showImportModal} onClose={handleCancelImport} title="Confirmer l'import">
        <div>
              <div className="flex items-center gap-4 mb-4">
                <div className="w-12 h-12 bg-yellow-100 rounded-full flex items-center justify-center">
                  <AlertCircle className="w-6 h-6 text-yellow-600" />
                </div>
                <div>
                  <p className="text-gray-600">Vérifiez les informations avant de continuer</p>
                </div>
              </div>

              <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6">
                <div className="flex items-start gap-3">
                  <Info className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="text-sm font-medium text-blue-900 mb-2">Informations sur le fichier</p>
                    <div className="space-y-1 text-sm text-blue-800">
                      <p><strong>Type:</strong> {importData.type}</p>
                      <p><strong>Nom du fichier:</strong> {importData.file?.name}</p>
                      <p><strong>Taille:</strong> {(importData.file?.size / 1024).toFixed(2)} KB</p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-6">
                <div className="flex items-start gap-3">
                  <AlertCircle className="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="text-sm font-medium text-yellow-900 mb-2">Avertissement</p>
                    <ul className="text-sm text-yellow-800 space-y-1 list-disc list-inside">
                      <li>L'import peut prendre quelques instants</li>
                      <li>Les données existantes avec le même identifiant seront écrasées</li>
                      <li>Assurez-vous que le format du fichier est correct</li>
                    </ul>
                  </div>
                </div>
              </div>

              <div className="flex gap-3">
                <button
                  onClick={handleCancelImport}
                  className="flex-1 px-4 py-3 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors font-medium"
                >
                  Annuler
                </button>
                <button
                  onClick={handleImport}
                  className="flex-1 px-4 py-3 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors font-medium"
                >
                  Confirmer l'import
                </button>
              </div>
            </div>
      </Modal>
    </div>
  );
};

export default Settings;
