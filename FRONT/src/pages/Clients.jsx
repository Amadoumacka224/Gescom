import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Plus, Edit, Trash2, Mail, Phone, MapPin, Building2, User, Users, UserCheck,
  Eye, RefreshCw, Download, Hash, CalendarClock, Globe, X,
} from 'lucide-react';
import { toast } from 'react-hot-toast';
import { useAuth } from '../context/AuthContext';
import clientService from '../services/clientService';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Pagination from '../components/Pagination';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import Button from '../components/Button';
import Table from '../components/Table';
import SearchBox from '../components/SearchBox';
import StatCard from '../components/StatCard';
import SegmentedFilter from '../components/SegmentedFilter';
import InfoRow from '../components/InfoRow';
import AdvancedFilters from '../components/AdvancedFilters';
import { rankSuggestions } from '../utils/searchSuggestions';
import { formatDate, formatPercent, safeRatio } from '../utils/format';

const EMPTY_FORM = {
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  address: '',
  city: '',
  postalCode: '',
  country: '',
  company: '',
  type: 'PARTICULIER',
  active: true,
};

/** Mémorise le mode d'affichage entre deux visites, comme la page Produits. */
const VIEW_MODE_KEY = 'clientsViewMode';

/** Nombre de clients mis en avant dans la vue d'aperçu (les derniers ajoutés). */
const RECENT_COUNT = 6;

/**
 * Critères de filtrage, à l'état neutre. Sert de valeur initiale, de cible du bouton
 * « Réinitialiser » et de référence pour savoir quels critères sont actifs.
 * Type et statut y figurent au même titre que les autres : ils étaient portés par des bandeaux
 * segmentés au-dessus de la liste, qui doublonnaient avec les tuiles d'indicateurs.
 */
const EMPTY_ADVANCED = {
  type: 'ALL',
  status: 'ALL',
  city: '',
  country: '',
  company: '',
  contact: 'ALL',
  createdFrom: '',
  createdTo: '',
};

const fullName = (client) => `${client?.firstName || ''} ${client?.lastName || ''}`.trim();

const initials = (client) =>
  `${(client?.firstName || '?').charAt(0)}${(client?.lastName || '').charAt(0)}`.toUpperCase();

const formatDateTime = (iso) =>
  iso
    ? new Date(iso).toLocaleString('fr-FR', {
        day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
      })
    : '—';

/**
 * Pastille d'identité. Le particulier porte ses initiales dans un disque, l'entreprise un
 * médaillon carré à l'icône d'immeuble : le type se lit avant même la colonne « Type », ce qui
 * évite d'avoir à parcourir la ligne entière pour savoir à qui l'on a affaire.
 */
const ClientAvatar = ({ client, size = 'md' }) => {
  const isCompany = client?.type === 'ENTREPRISE';
  const dimensions = size === 'lg' ? 'w-14 h-14 text-lg' : 'w-10 h-10 text-sm';

  return (
    <div
      aria-hidden="true"
      className={`${dimensions} flex items-center justify-center flex-shrink-0 font-semibold ${
        isCompany
          ? 'rounded-xl bg-secondary-100 text-secondary-700 dark:bg-secondary-400/20 dark:text-secondary-200'
          : 'rounded-full bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-200'
      }`}
    >
      {isCompany
        ? <Building2 className={size === 'lg' ? 'w-7 h-7' : 'w-5 h-5'} />
        : initials(client)}
    </div>
  );
};

const Clients = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  // Le backend réserve la suppression, la désactivation et l'export à l'ADMIN
  // (cf. ClientController) : on masque ces actions au caissier plutôt que de le laisser
  // déclencher un 403. La création et la modification lui restent ouvertes.
  const isAdmin = user?.role === 'ADMIN';

  const [clients, setClients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [searchTerm, setSearchTerm] = useState('');
  const [advanced, setAdvanced] = useState(EMPTY_ADVANCED);
  const [filtersExpanded, setFiltersExpanded] = useState(false);
  const [sortConfig, setSortConfig] = useState({ key: 'createdAt', direction: 'desc' });

  const [showModal, setShowModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [editingClient, setEditingClient] = useState(null);
  const [selectedClient, setSelectedClient] = useState(null);
  const [clientToDelete, setClientToDelete] = useState(null);

  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  // Vue par défaut : seul le dernier client ajouté est mis en avant ; la liste complète
  // s'obtient par la bascule d'affichage, ou dès qu'une recherche / un filtre est actif.
  const [viewMode, setViewMode] = useState(() => localStorage.getItem(VIEW_MODE_KEY) || 'recent');
  const [formData, setFormData] = useState(EMPTY_FORM);

  useEffect(() => {
    fetchClients();
  }, []);

  const fetchClients = async () => {
    setLoading(true);
    try {
      const response = await clientService.getAllClients();
      setClients(response.data);
    } catch (error) {
      console.error('Error fetching clients:', error);
      toast.error(t('clients.loadError'));
    } finally {
      setLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    setSaving(true);
    const toastId = 'client-save';
    toast.loading(editingClient ? t('clients.savingEdit') : t('clients.savingCreate'), { id: toastId });

    try {
      if (editingClient) {
        await clientService.updateClient(editingClient.id, formData);
        toast.success(t('clients.updatedSuccess'), { id: toastId });
      } else {
        await clientService.createClient(formData);
        toast.success(t('clients.createdSuccess'), { id: toastId });
      }

      await fetchClients();
      handleCloseModal();
    } catch (error) {
      console.error('Error saving client:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string' ? raw : (raw?.message || raw?.error || t('clients.saveError'));
      toast.error(`${t('common.errorPrefix')}${message}`, { id: toastId, duration: 6000 });
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (client) => {
    setEditingClient(client);
    // On ne reprend que les champs du formulaire : l'objet reçu de l'API porte aussi `id`,
    // `name`, `createdAt`… que le DTO de requête n'attend pas.
    setFormData({
      firstName: client.firstName || '',
      lastName: client.lastName || '',
      email: client.email || '',
      phone: client.phone || '',
      address: client.address || '',
      city: client.city || '',
      postalCode: client.postalCode || '',
      country: client.country || '',
      company: client.company || '',
      type: client.type || 'PARTICULIER',
      active: client.active !== false,
    });
    setSelectedClient(null);
    setShowModal(true);
  };

  const confirmDelete = async () => {
    if (!clientToDelete) return;
    const toastId = 'client-delete';
    toast.loading(t('clients.deleting'), { id: toastId });

    try {
      await clientService.deleteClient(clientToDelete.id);
      toast.success(t('clients.deleteSuccess'), { id: toastId });
      await fetchClients();
    } catch (error) {
      console.error('Error deleting client:', error);
      const raw = error.response?.data;
      const message = typeof raw === 'string' ? raw : (raw?.message || raw?.error || t('clients.deleteError'));
      toast.error(`${t('common.errorPrefix')}${message}`, { id: toastId, duration: 6000 });
    } finally {
      setClientToDelete(null);
    }
  };

  const handleExport = async () => {
    const toastId = 'client-export';
    toast.loading(t('clients.exporting'), { id: toastId });
    try {
      const response = await clientService.exportClients();
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `clients_${new Date().toISOString().split('T')[0]}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      toast.success(t('clients.exportSuccess'), { id: toastId });
    } catch (error) {
      console.error('Error exporting clients:', error);
      toast.error(t('clients.exportError'), { id: toastId });
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingClient(null);
    setFormData(EMPTY_FORM);
  };

  const stats = useMemo(() => ({
    total: clients.length,
    active: clients.filter((c) => c.active).length,
    individuals: clients.filter((c) => c.type === 'PARTICULIER').length,
    companies: clients.filter((c) => c.type === 'ENTREPRISE').length,
  }), [clients]);

  // Listes déduites des clients eux-mêmes : n'afficher que les valeurs réellement présentes
  // évite les critères qui ne rendent aucun résultat.
  const cityOptions = useMemo(() => {
    const set = new Set(clients.map((c) => c.city).filter(Boolean));
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [clients]);

  const countryOptions = useMemo(() => {
    const set = new Set(clients.map((c) => c.country).filter(Boolean));
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [clients]);

  const advancedFields = useMemo(() => [
    {
      key: 'type',
      label: t('clients.typeLabel'),
      type: 'select',
      options: [
        { value: 'ALL', label: t('clients.filterAll') },
        { value: 'PARTICULIER', label: t('clients.typeIndividual') },
        { value: 'ENTREPRISE', label: t('clients.typeBusiness') },
      ],
    },
    {
      key: 'status',
      label: t('clients.columnStatus'),
      type: 'select',
      options: [
        { value: 'ALL', label: t('clients.filterAll') },
        { value: 'ACTIVE', label: t('clients.active') },
        { value: 'INACTIVE', label: t('clients.inactive') },
      ],
    },
    {
      key: 'city',
      label: t('clients.city'),
      type: 'select',
      options: [{ value: '', label: t('clients.filterAll') }, ...cityOptions.map((c) => ({ value: c, label: c }))],
    },
    {
      key: 'country',
      label: t('clients.country'),
      type: 'select',
      options: [{ value: '', label: t('clients.filterAll') }, ...countryOptions.map((c) => ({ value: c, label: c }))],
    },
    { key: 'company', label: t('clients.companyLabel'), type: 'text', placeholder: t('clients.companyPlaceholder') },
    {
      key: 'contact',
      label: t('clients.sectionContact'),
      type: 'select',
      options: [
        { value: 'ALL', label: t('clients.filterAll') },
        { value: 'WITH_EMAIL', label: t('clients.withEmail') },
        { value: 'WITHOUT_EMAIL', label: t('clients.withoutEmail') },
      ],
    },
    { key: 'createdFrom', label: t('clients.createdFromLabel'), type: 'date' },
    { key: 'createdTo', label: t('clients.createdToLabel'), type: 'date' },
  ], [t, cityOptions, countryOptions]);

  const filteredClients = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return clients.filter((client) => {
      if (advanced.type !== 'ALL' && client.type !== advanced.type) return false;
      if (advanced.status === 'ACTIVE' && !client.active) return false;
      if (advanced.status === 'INACTIVE' && client.active) return false;

      if (advanced.city && client.city !== advanced.city) return false;
      if (advanced.country && client.country !== advanced.country) return false;
      if (advanced.company
        && !(client.company || '').toLowerCase().includes(advanced.company.trim().toLowerCase())) {
        return false;
      }
      if (advanced.contact === 'WITH_EMAIL' && !client.email) return false;
      if (advanced.contact === 'WITHOUT_EMAIL' && client.email) return false;

      // Bornes inclusives, comparées sur la partie `yyyy-MM-dd` de l'horodatage : comparer des
      // dates entières exclurait les clients créés le jour de fin passé minuit.
      if (advanced.createdFrom || advanced.createdTo) {
        const day = (client.createdAt || '').slice(0, 10);
        if (!day) return false;
        if (advanced.createdFrom && day < advanced.createdFrom) return false;
        if (advanced.createdTo && day > advanced.createdTo) return false;
      }

      if (!term) return true;
      return [fullName(client), client.company, client.email, client.phone, client.city]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(term);
    });
  }, [clients, searchTerm, advanced]);

  const sortedClients = useMemo(() => {
    const direction = sortConfig.direction === 'asc' ? 1 : -1;
    const valueOf = (client) => {
      switch (sortConfig.key) {
        case 'name': return fullName(client).toLowerCase();
        case 'location': return (client.city || '').toLowerCase();
        case 'type': return client.type || '';
        case 'status': return client.active ? 1 : 0;
        case 'createdAt': return new Date(client.createdAt || 0).getTime();
        default: return client.id;
      }
    };
    return [...filteredClients].sort((a, b) => {
      const left = valueOf(a);
      const right = valueOf(b);
      if (left < right) return -direction;
      if (left > right) return direction;
      // Départage stable : le plus récemment créé d'abord.
      return b.id - a.id;
    });
  }, [filteredClients, sortConfig]);

  // Suggestions d'autocomplétion classées par pertinence (nom complet et société
  // prioritaires sur e-mail / téléphone), distinctes du filtrage du tableau.
  const clientSuggestions = rankSuggestions(
    clients,
    searchTerm,
    (c) => [fullName(c), c.company, c.email, c.phone],
    8
  );

  const hasAdvancedFilters = Object.keys(EMPTY_ADVANCED)
    .some((key) => advanced[key] !== EMPTY_ADVANCED[key]);
  const hasActiveFilters = searchTerm.trim() !== '' || hasAdvancedFilters;
  // Un filtre actif force la liste complète : filtrer pour ne voir qu'une ligne n'aurait aucun sens.
  const showFullList = hasActiveFilters || viewMode === 'all';

  const totalPages = Math.ceil(sortedClients.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const paginatedClients = sortedClients.slice(startIndex, startIndex + itemsPerPage);

  // Les derniers clients ajoutés, du plus récent au plus ancien.
  // À défaut de date de création exploitable, on retombe sur l'id le plus élevé.
  const recentClients = useMemo(() => (
    [...clients]
      .sort((a, b) => (new Date(b.createdAt || 0) - new Date(a.createdAt || 0)) || (b.id - a.id))
      .slice(0, RECENT_COUNT)
  ), [clients]);

  const displayedClients = showFullList ? paginatedClients : recentClients;

  // Toute modification du périmètre ramène à la première page : rester en page 4 d'un
  // résultat qui n'en compte plus que 2 afficherait un tableau vide à tort.
  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, advanced, sortConfig, itemsPerPage, viewMode]);

  const handleSort = (key) => {
    setSortConfig((prev) => {
      if (prev.key === key) {
        return { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' };
      }
      // Une date s'ouvre du plus récent au plus ancien, un texte de A à Z.
      return { key, direction: key === 'createdAt' ? 'desc' : 'asc' };
    });
  };

  const handleViewModeChange = (mode) => {
    setViewMode(mode);
    localStorage.setItem(VIEW_MODE_KEY, mode);
  };

  const resetFilters = () => {
    setSearchTerm('');
    setAdvanced(EMPTY_ADVANCED);
  };

  const handleAdvancedChange = (field, value) => {
    setAdvanced((prev) => ({ ...prev, [field]: value }));
  };

  const typeBadge = (client) => (
    <span className={client.type === 'ENTREPRISE' ? 'badge-accent' : 'badge-info'}>
      {client.type === 'ENTREPRISE' ? t('clients.typeBusiness') : t('clients.typeIndividual')}
    </span>
  );

  const statusBadge = (client) => (
    <span className={client.active ? 'badge-success' : 'badge-neutral'}>
      {client.active ? t('clients.active') : t('clients.inactive')}
    </span>
  );

  /* Colonnes ordonnées par importance décroissante — identité, moyens de contact, puis contexte.
   * Les colonnes secondaires disparaissent sur écran étroit (`hidden … table-cell`) plutôt que
   * de pousser le tableau dans un défilement horizontal où la colonne d'actions devient
   * inatteignable. */
  const columns = [
    {
      key: 'name',
      label: t('clients.columnName'),
      sortable: true,
      render: (client) => (
        <div className="flex items-center gap-3">
          <ClientAvatar client={client} />
          <div className="min-w-0">
            <div className="font-semibold text-gray-900 dark:text-gray-100 truncate">
              {fullName(client)}
            </div>
            <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
              {client.company || t('clients.typeIndividual')}
            </div>
          </div>
        </div>
      ),
    },
    {
      key: 'contact',
      label: t('clients.columnContact'),
      nowrap: false,
      render: (client) => (
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <Mail className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" aria-hidden="true" />
            {client.email ? (
              <a
                href={`mailto:${client.email}`}
                onClick={(e) => e.stopPropagation()}
                className="text-gray-700 dark:text-gray-300 hover:text-primary-600 dark:hover:text-primary-400 hover:underline truncate"
              >
                {client.email}
              </a>
            ) : (
              <span className="text-gray-400">{t('clients.noEmail')}</span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Phone className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" aria-hidden="true" />
            <a
              href={`tel:${client.phone}`}
              onClick={(e) => e.stopPropagation()}
              className="text-gray-700 dark:text-gray-300 hover:text-primary-600 dark:hover:text-primary-400 hover:underline"
            >
              {client.phone}
            </a>
          </div>
        </div>
      ),
    },
    {
      key: 'location',
      label: t('clients.columnLocation'),
      sortable: true,
      className: 'hidden xl:table-cell',
      render: (client) => (
        client.city || client.country ? (
          <div>
            <div className="text-gray-700 dark:text-gray-300">
              {[client.postalCode, client.city].filter(Boolean).join(' ') || '—'}
            </div>
            <div className="text-xs text-gray-500 dark:text-gray-400">{client.country || '—'}</div>
          </div>
        ) : (
          <span className="text-gray-400 text-xs">{t('clients.noAddress')}</span>
        )
      ),
    },
    {
      key: 'type',
      label: t('clients.type'),
      sortable: true,
      className: 'hidden md:table-cell',
      render: typeBadge,
    },
    {
      key: 'status',
      label: t('clients.columnStatus'),
      sortable: true,
      className: 'hidden sm:table-cell',
      render: statusBadge,
    },
    {
      key: 'createdAt',
      label: t('clients.columnSince'),
      sortable: true,
      className: 'hidden lg:table-cell',
      render: (client) => (
        <span className="text-gray-600 dark:text-gray-400 tabular-nums">{formatDate(client.createdAt)}</span>
      ),
    },
  ];

  const emptyState = hasActiveFilters ? (
    <div className="flex flex-col items-center gap-3">
      <Users className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('clients.noResultsTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('clients.noResultsHint')}</p>
      </div>
      <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
        {t('clients.resetFilters')}
      </Button>
    </div>
  ) : (
    <div className="flex flex-col items-center gap-3">
      <Users className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('clients.emptyTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('clients.emptyHint')}</p>
      </div>
      <Button variant="primary" size="sm" icon={Plus} onClick={() => setShowModal(true)}>
        {t('clients.addClient')}
      </Button>
    </div>
  );

  return (
    <div className="space-y-6">
      {/* ---- En-tête ---- */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <Users aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('clients.title')}</h1>
            <p className="page-subtitle">{t('clients.subtitle')}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" icon={RefreshCw} onClick={fetchClients} loading={loading}>
            {t('common.refresh')}
          </Button>
          {isAdmin && (
            <Button variant="secondary" icon={Download} onClick={handleExport}>
              {t('common.export')}
            </Button>
          )}
          <Button variant="primary" icon={Plus} onClick={() => setShowModal(true)}>
            {t('clients.addClient')}
          </Button>
        </div>
      </div>

      {/* ---- Indicateurs ----
       * Les quatre tuiles partagent le composant StatCard : même échelle typographique, même
       * squelette de chargement et même comportement en tuile étroite que les autres écrans. */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 sm:gap-6">
        <StatCard
          title={t('clients.totalCount')}
          value={stats.total}
          subtitle={t('clients.portfolioHint')}
          icon={Users}
          tone="info"
          loading={loading}
        />
        <StatCard
          title={t('clients.activeCount')}
          value={stats.active}
          subtitle={t('clients.shareOfTotal', { percent: formatPercent(safeRatio(stats.active, stats.total)) })}
          icon={UserCheck}
          tone="success"
          loading={loading}
        />
        <StatCard
          title={t('clients.individualCount')}
          value={stats.individuals}
          subtitle={t('clients.shareOfTotal', { percent: formatPercent(safeRatio(stats.individuals, stats.total)) })}
          icon={User}
          tone="accent"
          loading={loading}
        />
        <StatCard
          title={t('clients.businessCount')}
          value={stats.companies}
          subtitle={t('clients.shareOfTotal', { percent: formatPercent(safeRatio(stats.companies, stats.total)) })}
          icon={Building2}
          tone="warning"
          loading={loading}
        />
      </div>

      {/* ---- Recherche et filtres ---- */}
      <div className="card space-y-4">
        <AdvancedFilters
          id="clients"
          fields={advancedFields}
          values={advanced}
          defaults={EMPTY_ADVANCED}
          onChange={handleAdvancedChange}
          onReset={resetFilters}
          resettable={hasActiveFilters}
          expanded={filtersExpanded}
          onToggleExpanded={() => setFiltersExpanded((v) => !v)}
          dateRange={{ fromKey: 'createdFrom', toKey: 'createdTo' }}
          search={(
            <SearchBox
              value={searchTerm}
              onChange={setSearchTerm}
              placeholder={t('clients.searchPlaceholder')}
              suggestions={clientSuggestions}
              getKey={(c) => c.id}
              onSelectSuggestion={(c) => setSearchTerm(fullName(c))}
              renderSuggestion={(c) => (
                <span className="flex items-center justify-between gap-2">
                  <span className="flex flex-col min-w-0">
                    <span className="font-medium truncate">{fullName(c)}</span>
                    <span className="text-xs text-gray-400 truncate">{c.email || c.phone || ''}</span>
                  </span>
                  {c.company && <span className="text-xs text-gray-500 shrink-0">{c.company}</span>}
                </span>
              )}
            />
          )}
        />
      </div>

      {/* ---- Répertoire ---- */}
      <div className="card overflow-hidden p-0">
        <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div>
            <h2 className="section-title">{t('clients.directory')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
              {showFullList ? t('clients.directoryHint') : t('clients.recentHint', { n: RECENT_COUNT })}
            </p>
          </div>
          {!hasActiveFilters && clients.length > 0 && (
            <SegmentedFilter
              label={t('clients.displayLabel')}
              value={viewMode}
              onChange={handleViewModeChange}
              options={[
                { value: 'recent', label: t('clients.viewRecent', { n: RECENT_COUNT }) },
                { value: 'all', label: t('clients.viewAll'), count: stats.total },
              ]}
            />
          )}
        </div>

        <Table
          columns={columns}
          data={displayedClients}
          loading={loading}
          emptyState={emptyState}
          sortKey={sortConfig.key}
          sortDirection={sortConfig.direction}
          onSort={handleSort}
          onRowClick={setSelectedClient}
          actions={(client) => (
            <>
              <button
                onClick={(e) => { e.stopPropagation(); setSelectedClient(client); }}
                className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                title={t('clients.viewProfile')}
                aria-label={`${t('clients.viewProfile')} — ${fullName(client)}`}
              >
                <Eye className="w-4 h-4" aria-hidden="true" />
              </button>
              <button
                onClick={(e) => { e.stopPropagation(); handleEdit(client); }}
                className="text-primary-600 hover:text-primary-900 dark:hover:text-primary-300 p-2 hover:bg-primary-50 dark:hover:bg-primary-500/10 rounded-lg transition-colors"
                title={t('common.edit')}
                aria-label={`${t('common.edit')} — ${fullName(client)}`}
              >
                <Edit className="w-4 h-4" aria-hidden="true" />
              </button>
              {isAdmin && (
                <button
                  onClick={(e) => { e.stopPropagation(); setClientToDelete(client); }}
                  className="text-red-600 hover:text-red-900 dark:hover:text-red-300 p-2 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors"
                  title={t('common.delete')}
                  aria-label={`${t('common.delete')} — ${fullName(client)}`}
                >
                  <Trash2 className="w-4 h-4" aria-hidden="true" />
                </button>
              )}
            </>
          )}
        />

        {/* Pagination : seulement quand la liste complète est affichée (la vue « dernier ajout »
            ne montre qu'une ligne, un pied de pagination y serait trompeur). */}
        {showFullList && !loading && sortedClients.length > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={sortedClients.length}
            itemsPerPage={itemsPerPage}
            onPageChange={setCurrentPage}
            onItemsPerPageChange={setItemsPerPage}
          />
        )}
      </div>

      {/* ---- Fiche client (lecture seule) ---- */}
      <Modal
        isOpen={!!selectedClient}
        onClose={() => setSelectedClient(null)}
        title={t('clients.clientDetails')}
        size="md"
      >
        {selectedClient && (
          <div className="space-y-6">
            <div className="flex items-start gap-4 pb-5 border-b border-gray-200 dark:border-gray-700">
              <ClientAvatar client={selectedClient} size="lg" />
              <div className="min-w-0">
                <h3 className="text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
                  {fullName(selectedClient)}
                </h3>
                {selectedClient.company && (
                  <p className="text-sm text-gray-500 dark:text-gray-400 truncate">{selectedClient.company}</p>
                )}
                <div className="flex flex-wrap items-center gap-2 mt-2">
                  {typeBadge(selectedClient)}
                  {statusBadge(selectedClient)}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              <section className="space-y-3">
                <h4 className="subsection-title">{t('clients.sectionContact')}</h4>
                <dl className="space-y-3">
                  <InfoRow
                    icon={Mail}
                    label={t('clients.email')}
                    value={selectedClient.email}
                    href={selectedClient.email ? `mailto:${selectedClient.email}` : undefined}
                  />
                  <InfoRow
                    icon={Phone}
                    label={t('clients.phone')}
                    value={selectedClient.phone}
                    href={selectedClient.phone ? `tel:${selectedClient.phone}` : undefined}
                  />
                </dl>
              </section>

              <section className="space-y-3">
                <h4 className="subsection-title">{t('clients.sectionAddress')}</h4>
                <dl className="space-y-3">
                  <InfoRow icon={MapPin} label={t('clients.address')} value={selectedClient.address} />
                  <InfoRow
                    icon={MapPin}
                    label={t('clients.city')}
                    value={[selectedClient.postalCode, selectedClient.city].filter(Boolean).join(' ')}
                  />
                  <InfoRow icon={Globe} label={t('clients.country')} value={selectedClient.country} />
                </dl>
              </section>
            </div>

            <section className="space-y-3 pt-2 border-t border-gray-200 dark:border-gray-700">
              <h4 className="subsection-title pt-3">{t('clients.sectionTracking')}</h4>
              <dl className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <InfoRow icon={Hash} label={t('clients.reference')} value={`#${selectedClient.id}`} />
                <InfoRow
                  icon={CalendarClock}
                  label={t('clients.createdAtLabel')}
                  value={formatDateTime(selectedClient.createdAt)}
                />
                <InfoRow
                  icon={CalendarClock}
                  label={t('clients.updatedAtLabel')}
                  value={formatDateTime(selectedClient.updatedAt)}
                />
              </dl>
            </section>

            <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
              <Button variant="secondary" onClick={() => setSelectedClient(null)}>
                {t('common.cancel')}
              </Button>
              <Button variant="primary" icon={Edit} onClick={() => handleEdit(selectedClient)}>
                {t('common.edit')}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* ---- Formulaire ---- */}
      <Modal
        isOpen={showModal}
        onClose={handleCloseModal}
        title={editingClient ? t('clients.editClient') : t('clients.newClient')}
        size="lg"
      >
        <form onSubmit={handleSubmit} className="space-y-8">
          {/* Le formulaire suit l'ordre de lecture de la fiche : qui, comment le joindre, où. */}
          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <User className="w-5 h-5 text-primary-600" aria-hidden="true" />
              <h3 className="subsection-title">{t('clients.columnName')}</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <FormInput
                label={t('clients.firstName')}
                name="firstName"
                value={formData.firstName}
                onChange={handleInputChange}
                placeholder={t('clients.firstNamePlaceholder')}
                required
                icon={User}
              />
              <FormInput
                label={t('clients.lastName')}
                name="lastName"
                value={formData.lastName}
                onChange={handleInputChange}
                placeholder={t('clients.lastNamePlaceholder')}
                required
                icon={User}
              />
              <FormSelect
                label={t('clients.typeLabel')}
                name="type"
                value={formData.type}
                onChange={handleInputChange}
                required
                options={[
                  { value: 'PARTICULIER', label: t('clients.typeIndividual') },
                  { value: 'ENTREPRISE', label: t('clients.typeBusiness') },
                ]}
              />
              <FormInput
                label={t('clients.companyLabel')}
                name="company"
                value={formData.company}
                onChange={handleInputChange}
                placeholder={t('clients.companyPlaceholder')}
                icon={Building2}
              />
            </div>
          </section>

          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <Mail className="w-5 h-5 text-primary-600" aria-hidden="true" />
              <h3 className="subsection-title">{t('clients.sectionContact')}</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <FormInput
                label={t('clients.email')}
                name="email"
                type="email"
                value={formData.email}
                onChange={handleInputChange}
                placeholder={t('clients.emailPlaceholder')}
                icon={Mail}
              />
              <FormInput
                label={t('clients.phone')}
                name="phone"
                type="tel"
                value={formData.phone}
                onChange={handleInputChange}
                placeholder={t('clients.phonePlaceholder')}
                required
                icon={Phone}
              />
            </div>
          </section>

          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <MapPin className="w-5 h-5 text-primary-600" aria-hidden="true" />
              <h3 className="subsection-title">{t('clients.sectionAddress')}</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <FormInput
                label={t('clients.address')}
                name="address"
                value={formData.address}
                onChange={handleInputChange}
                placeholder={t('clients.addressPlaceholder')}
                icon={MapPin}
              />
              <FormInput
                label={t('clients.city')}
                name="city"
                value={formData.city}
                onChange={handleInputChange}
                placeholder={t('clients.cityPlaceholder')}
                icon={MapPin}
              />
              <FormInput
                label={t('clients.postalCode')}
                name="postalCode"
                value={formData.postalCode}
                onChange={handleInputChange}
                placeholder={t('clients.postalCodePlaceholder')}
              />
              <FormInput
                label={t('clients.country')}
                name="country"
                value={formData.country}
                onChange={handleInputChange}
                placeholder={t('clients.countryPlaceholder')}
                icon={Globe}
              />
            </div>
          </section>

          {/* Interrupteur plutôt qu'une case à cocher : l'effet du réglage est écrit à côté,
              un client inactif restant invisible dans les sélecteurs de commande. */}
          <div className="flex items-center justify-between gap-4 p-4 rounded-lg bg-gray-50 dark:bg-gray-900/40 border border-gray-200 dark:border-gray-700">
            <div>
              <label htmlFor="active" className="font-medium text-gray-900 dark:text-gray-100 cursor-pointer">
                {t('clients.activeLabel')}
              </label>
              <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                {formData.active ? t('clients.active') : t('clients.inactive')}
              </p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                id="active"
                name="active"
                checked={formData.active}
                onChange={handleInputChange}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-gray-200 dark:bg-gray-600 peer-focus-visible:ring-2 peer-focus-visible:ring-primary-500 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"></div>
            </label>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
            <Button variant="secondary" onClick={handleCloseModal} type="button">
              {t('common.cancel')}
            </Button>
            <Button variant="primary" type="submit" loading={saving}>
              {editingClient ? t('common.saveChanges') : t('common.create')}
            </Button>
          </div>
        </form>
      </Modal>

      {/* ---- Confirmations ---- */}
      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmSubmit}
        title={editingClient ? t('clients.confirmEdit') : t('clients.confirmCreate')}
        message={editingClient
          ? t('clients.confirmEditMessage', { name: fullName(formData) })
          : t('clients.confirmCreateMessage', { name: fullName(formData) })}
        type="info"
      />

      <ConfirmModal
        isOpen={!!clientToDelete}
        onClose={() => setClientToDelete(null)}
        onConfirm={confirmDelete}
        title={t('clients.confirmDeleteTitle')}
        message={t('clients.confirmDeleteNamed', { name: fullName(clientToDelete) })}
        type="danger"
        confirmLabel={t('common.delete')}
        cancelLabel={t('common.cancel')}
      />
    </div>
  );
};

export default Clients;
