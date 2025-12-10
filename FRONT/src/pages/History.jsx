import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  History as HistoryIcon,
  Search,
  Filter,
  Calendar,
  User,
  RefreshCw,
  Download,
  ChevronDown,
  X
} from 'lucide-react';
import { toast } from 'react-hot-toast';
import api from '../services/api';
import Pagination from '../components/Pagination';
import Button from '../components/Button';

const History = () => {
  const { t } = useTranslation();
  const [activities, setActivities] = useState([]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(20);

  // Filtres
  const [showFilters, setShowFilters] = useState(false);
  const [selectedUser, setSelectedUser] = useState('');
  const [selectedActionType, setSelectedActionType] = useState('');
  const [selectedEntity, setSelectedEntity] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const actionTypes = [
    { value: 'CREATE', label: 'Création', color: 'bg-green-100 text-green-700' },
    { value: 'UPDATE', label: 'Modification', color: 'bg-blue-100 text-blue-700' },
    { value: 'DELETE', label: 'Suppression', color: 'bg-red-100 text-red-700' },
    { value: 'VIEW', label: 'Consultation', color: 'bg-gray-100 text-gray-700' },
    { value: 'LOGIN', label: 'Connexion', color: 'bg-purple-100 text-purple-700' },
    { value: 'LOGOUT', label: 'Déconnexion', color: 'bg-orange-100 text-orange-700' },
    { value: 'SALE', label: 'Vente', color: 'bg-emerald-100 text-emerald-700' },
    { value: 'PAYMENT', label: 'Paiement', color: 'bg-teal-100 text-teal-700' },
    { value: 'STOCK_IN', label: 'Entrée stock', color: 'bg-cyan-100 text-cyan-700' },
    { value: 'STOCK_OUT', label: 'Sortie stock', color: 'bg-amber-100 text-amber-700' },
    { value: 'EXPORT', label: 'Export', color: 'bg-indigo-100 text-indigo-700' },
    { value: 'IMPORT', label: 'Import', color: 'bg-violet-100 text-violet-700' },
  ];

  const entities = [
    'Client', 'Product', 'Category', 'Order', 'Delivery', 'Invoice', 'Stock', 'User', 'Settings'
  ];

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [activitiesRes, usersRes] = await Promise.all([
        api.get('/activities'),
        api.get('/users')
      ]);
      setActivities(activitiesRes.data);
      setUsers(usersRes.data);
    } catch (error) {
      console.error('Error fetching data:', error);
      toast.error('Erreur lors du chargement des données');
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = () => {
    fetchData();
    toast.success('Données actualisées');
  };

  const clearFilters = () => {
    setSelectedUser('');
    setSelectedActionType('');
    setSelectedEntity('');
    setStartDate('');
    setEndDate('');
    setSearchTerm('');
    setCurrentPage(1);
  };

  const hasActiveFilters = selectedUser || selectedActionType || selectedEntity || startDate || endDate || searchTerm;

  const getActionTypeBadge = (actionType) => {
    const type = actionTypes.find(t => t.value === actionType);
    if (type) {
      return <span className={`px-2 py-1 rounded text-xs font-medium ${type.color}`}>{type.label}</span>;
    }
    return <span className="px-2 py-1 rounded text-xs font-medium bg-gray-100 text-gray-700">{actionType}</span>;
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString('fr-FR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  const formatDateShort = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('fr-FR', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  };

  // Filtrage des activités
  const filteredActivities = activities.filter(activity => {
    // Filtre par recherche textuelle
    const searchMatch = !searchTerm ||
      activity.description?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      activity.entity?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      activity.user?.username?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      activity.user?.firstName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      activity.user?.lastName?.toLowerCase().includes(searchTerm.toLowerCase());

    // Filtre par utilisateur
    const userMatch = !selectedUser || activity.user?.id?.toString() === selectedUser;

    // Filtre par type d'action
    const actionMatch = !selectedActionType || activity.actionType === selectedActionType;

    // Filtre par entité
    const entityMatch = !selectedEntity || activity.entity === selectedEntity;

    // Filtre par date de début
    const startDateMatch = !startDate || new Date(activity.createdAt) >= new Date(startDate);

    // Filtre par date de fin
    const endDateMatch = !endDate || new Date(activity.createdAt) <= new Date(endDate + 'T23:59:59');

    return searchMatch && userMatch && actionMatch && entityMatch && startDateMatch && endDateMatch;
  });

  // Pagination
  const totalPages = Math.ceil(filteredActivities.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const currentActivities = filteredActivities.slice(startIndex, endIndex);

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const handleItemsPerPageChange = (newItemsPerPage) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1);
  };

  // Export CSV
  const exportToCSV = () => {
    const headers = ['Date', 'Utilisateur', 'Action', 'Entité', 'ID Entité', 'Description', 'Adresse IP'];
    const rows = filteredActivities.map(activity => [
      formatDate(activity.createdAt),
      activity.user ? `${activity.user.firstName} ${activity.user.lastName}` : 'Système',
      actionTypes.find(t => t.value === activity.actionType)?.label || activity.actionType,
      activity.entity || '-',
      activity.entityId || '-',
      activity.description || '-',
      activity.ipAddress || '-'
    ]);

    const csvContent = [headers, ...rows]
      .map(row => row.map(cell => `"${cell}"`).join(','))
      .join('\n');

    const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `historique_${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    toast.success('Export CSV téléchargé');
  };

  // Stats
  const todayActivities = activities.filter(a => {
    const today = new Date();
    const activityDate = new Date(a.createdAt);
    return activityDate.toDateString() === today.toDateString();
  }).length;

  const uniqueUsersToday = new Set(
    activities
      .filter(a => {
        const today = new Date();
        const activityDate = new Date(a.createdAt);
        return activityDate.toDateString() === today.toDateString();
      })
      .map(a => a.user?.id)
  ).size;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Historique des activités</h1>
          <p className="text-gray-600 mt-1">Suivez toutes les actions effectuées dans l'application</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            icon={RefreshCw}
            onClick={handleRefresh}
            disabled={loading}
          >
            Actualiser
          </Button>
          <Button
            variant="secondary"
            icon={Download}
            onClick={exportToCSV}
            disabled={filteredActivities.length === 0}
          >
            Exporter CSV
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="card bg-gradient-to-br from-blue-50 to-cyan-50 border-blue-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-blue-600 font-medium">Total actions</p>
              <p className="text-3xl font-bold text-blue-700">{activities.length}</p>
            </div>
            <HistoryIcon className="w-10 h-10 text-blue-600 opacity-50" />
          </div>
        </div>
        <div className="card bg-gradient-to-br from-green-50 to-emerald-50 border-green-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-green-600 font-medium">Actions aujourd'hui</p>
              <p className="text-3xl font-bold text-green-700">{todayActivities}</p>
            </div>
            <Calendar className="w-10 h-10 text-green-600 opacity-50" />
          </div>
        </div>
        <div className="card bg-gradient-to-br from-purple-50 to-pink-50 border-purple-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-purple-600 font-medium">Utilisateurs actifs</p>
              <p className="text-3xl font-bold text-purple-700">{uniqueUsersToday}</p>
            </div>
            <User className="w-10 h-10 text-purple-600 opacity-50" />
          </div>
        </div>
        <div className="card bg-gradient-to-br from-orange-50 to-amber-50 border-orange-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-orange-600 font-medium">Résultats filtrés</p>
              <p className="text-3xl font-bold text-orange-700">{filteredActivities.length}</p>
            </div>
            <Filter className="w-10 h-10 text-orange-600 opacity-50" />
          </div>
        </div>
      </div>

      {/* Search and Filters */}
      <div className="card space-y-4">
        <div className="flex flex-col md:flex-row gap-4">
          {/* Search */}
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              placeholder="Rechercher dans l'historique..."
              value={searchTerm}
              onChange={(e) => {
                setSearchTerm(e.target.value);
                setCurrentPage(1);
              }}
              className="input pl-10 w-full"
            />
          </div>

          {/* Toggle Filters Button */}
          <Button
            variant={showFilters ? 'primary' : 'secondary'}
            icon={Filter}
            onClick={() => setShowFilters(!showFilters)}
          >
            Filtres {hasActiveFilters && <span className="ml-1 px-2 py-0.5 bg-white/20 rounded-full text-xs">{
              [selectedUser, selectedActionType, selectedEntity, startDate, endDate].filter(Boolean).length
            }</span>}
            <ChevronDown className={`w-4 h-4 ml-1 transition-transform ${showFilters ? 'rotate-180' : ''}`} />
          </Button>

          {/* Clear Filters */}
          {hasActiveFilters && (
            <Button
              variant="secondary"
              icon={X}
              onClick={clearFilters}
            >
              Effacer
            </Button>
          )}
        </div>

        {/* Expandable Filters */}
        {showFilters && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 pt-4 border-t border-gray-200">
            {/* User Filter */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Utilisateur</label>
              <select
                value={selectedUser}
                onChange={(e) => {
                  setSelectedUser(e.target.value);
                  setCurrentPage(1);
                }}
                className="input w-full"
              >
                <option value="">Tous les utilisateurs</option>
                {users.map(user => (
                  <option key={user.id} value={user.id}>
                    {user.firstName} {user.lastName} (@{user.username})
                  </option>
                ))}
              </select>
            </div>

            {/* Action Type Filter */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Type d'action</label>
              <select
                value={selectedActionType}
                onChange={(e) => {
                  setSelectedActionType(e.target.value);
                  setCurrentPage(1);
                }}
                className="input w-full"
              >
                <option value="">Toutes les actions</option>
                {actionTypes.map(type => (
                  <option key={type.value} value={type.value}>{type.label}</option>
                ))}
              </select>
            </div>

            {/* Entity Filter */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Entité</label>
              <select
                value={selectedEntity}
                onChange={(e) => {
                  setSelectedEntity(e.target.value);
                  setCurrentPage(1);
                }}
                className="input w-full"
              >
                <option value="">Toutes les entités</option>
                {entities.map(entity => (
                  <option key={entity} value={entity}>{entity}</option>
                ))}
              </select>
            </div>

            {/* Start Date */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Date début</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => {
                  setStartDate(e.target.value);
                  setCurrentPage(1);
                }}
                className="input w-full"
              />
            </div>

            {/* End Date */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Date fin</label>
              <input
                type="date"
                value={endDate}
                onChange={(e) => {
                  setEndDate(e.target.value);
                  setCurrentPage(1);
                }}
                className="input w-full"
              />
            </div>
          </div>
        )}
      </div>

      {/* Activities Table */}
      <div className="card overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
            <span className="ml-3 text-gray-600">Chargement...</span>
          </div>
        ) : filteredActivities.length === 0 ? (
          <div className="text-center py-12">
            <HistoryIcon className="w-16 h-16 text-gray-300 mx-auto mb-4" />
            <h3 className="text-lg font-medium text-gray-900 mb-2">Aucune activité trouvée</h3>
            <p className="text-gray-500">
              {hasActiveFilters
                ? 'Essayez de modifier vos critères de recherche'
                : 'L\'historique est vide pour le moment'}
            </p>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Utilisateur</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Action</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Entité</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Description</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">IP</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {currentActivities.map((activity) => (
                    <tr
                      key={activity.id}
                      className="hover:bg-gray-50"
                    >
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
                        <div className="flex flex-col">
                          <span className="font-medium">{formatDateShort(activity.createdAt)}</span>
                          <span className="text-xs text-gray-400">
                            {new Date(activity.createdAt).toLocaleTimeString('fr-FR')}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        {activity.user ? (
                          <div className="flex items-center gap-2">
                            <div className="w-8 h-8 rounded-full bg-primary-100 flex items-center justify-center">
                              <span className="text-primary-700 font-semibold text-xs">
                                {activity.user.firstName?.charAt(0)}{activity.user.lastName?.charAt(0)}
                              </span>
                            </div>
                            <div>
                              <p className="font-medium text-gray-900 text-sm">
                                {activity.user.firstName} {activity.user.lastName}
                              </p>
                              <p className="text-xs text-gray-500">@{activity.user.username}</p>
                            </div>
                          </div>
                        ) : (
                          <span className="text-gray-400 italic text-sm">Système</span>
                        )}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        {getActionTypeBadge(activity.actionType)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex flex-col">
                          <span className="text-sm text-gray-900">{activity.entity || '-'}</span>
                          {activity.entityId && (
                            <span className="text-xs text-gray-400">ID: {activity.entityId}</span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-600 max-w-xs truncate" title={activity.description}>
                        {activity.description || '-'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-400">
                        {activity.ipAddress || '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalItems={filteredActivities.length}
              itemsPerPage={itemsPerPage}
              onPageChange={handlePageChange}
              onItemsPerPageChange={handleItemsPerPageChange}
            />
          </>
        )}
      </div>
    </div>
  );
};

export default History;
