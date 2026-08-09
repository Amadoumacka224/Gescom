import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Plus,
  RefreshCw,
  UserCog,
  Shield,
  ShieldCheck,
  UserRound,
  UserX,
  Edit,
  Trash2,
  Mail,
  Phone,
  Activity,
  ToggleLeft,
  ToggleRight,
  X,
} from 'lucide-react';
import { toast } from 'react-hot-toast';
import api from '../services/api';
import { useAuth } from '../context/useAuth';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import Button from '../components/Button';
import Table from '../components/Table';
import Pagination from '../components/Pagination';
import SearchBox from '../components/SearchBox';
import SegmentedFilter from '../components/SegmentedFilter';
import StatCard from '../components/StatCard';
import { ACTIVITY_TONE, badgeClass } from '../constants/statusBadges';
import { actionLabelKey } from '../constants/activityActions';
import { rankSuggestions } from '../utils/searchSuggestions';
import { formatDate, formatTime, formatPercent, safeRatio } from '../utils/format';

const EMPTY_FORM = {
  username: '',
  email: '',
  password: '',
  firstName: '',
  lastName: '',
  phone: '',
  role: 'CAISSIER',
  active: true,
};

const fullName = (user) => `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.username;

const initials = (user) =>
  `${user.firstName?.charAt(0) || ''}${user.lastName?.charAt(0) || ''}`.toUpperCase() ||
  user.username?.charAt(0).toUpperCase() ||
  '?';

const Users = () => {
  const { t } = useTranslation();
  const { user: currentUser } = useAuth();

  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);

  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');
  const [statusFilter, setStatusFilter] = useState('all');
  const [sortConfig, setSortConfig] = useState({ key: 'name', direction: 'asc' });
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);

  const [showModal, setShowModal] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [formData, setFormData] = useState(EMPTY_FORM);
  const [userToDelete, setUserToDelete] = useState(null);

  const [activitiesUser, setActivitiesUser] = useState(null);
  const [activities, setActivities] = useState([]);
  const [activitiesLoading, setActivitiesLoading] = useState(false);

  useEffect(() => {
    fetchUsers();
  }, []);

  // Retour à la première page quand le contenu de la liste change : rester en page 3
  // d'un résultat qui n'en compte plus qu'une affiche un tableau vide.
  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, roleFilter, statusFilter, itemsPerPage]);

  const fetchUsers = async () => {
    try {
      setLoading(true);
      const response = await api.get('/users');
      setUsers(response.data || []);
    } catch (error) {
      console.error('Error fetching users:', error);
      toast.error(t('users.loadError'));
    } finally {
      setLoading(false);
    }
  };

  const isSelf = (user) => user.id === currentUser?.id;

  const stats = useMemo(() => {
    const total = users.length;
    return {
      total,
      admins: users.filter((u) => u.role === 'ADMIN').length,
      cashiers: users.filter((u) => u.role === 'CAISSIER').length,
      active: users.filter((u) => u.active).length,
      inactive: users.filter((u) => !u.active).length,
    };
  }, [users]);

  const filteredUsers = useMemo(() => {
    const query = searchTerm.trim().toLowerCase();
    return users.filter((user) => {
      if (roleFilter !== 'all' && user.role !== roleFilter) return false;
      if (statusFilter === 'active' && !user.active) return false;
      if (statusFilter === 'inactive' && user.active) return false;
      if (!query) return true;
      return [user.firstName, user.lastName, user.username, user.email, user.phone]
        .filter(Boolean)
        .some((field) => field.toLowerCase().includes(query));
    });
  }, [users, searchTerm, roleFilter, statusFilter]);

  const sortedUsers = useMemo(() => {
    const { key, direction } = sortConfig;
    const factor = direction === 'asc' ? 1 : -1;

    const valueOf = (user) => {
      if (key === 'name') return fullName(user);
      if (key === 'status') return user.active ? 1 : 0;
      if (key === 'createdAt') return new Date(user.createdAt || 0).getTime();
      return user[key] ?? '';
    };

    return [...filteredUsers].sort((a, b) => {
      const left = valueOf(a);
      const right = valueOf(b);
      if (typeof left === 'string' || typeof right === 'string') {
        return String(left).localeCompare(String(right), 'fr', { sensitivity: 'base' }) * factor;
      }
      return (left - right) * factor;
    });
  }, [filteredUsers, sortConfig]);

  const totalPages = Math.max(1, Math.ceil(sortedUsers.length / itemsPerPage));
  const displayedUsers = sortedUsers.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  const userSuggestions = useMemo(
    () => rankSuggestions(users, searchTerm, (u) => [fullName(u), u.username, u.email, u.phone], 6),
    [users, searchTerm]
  );

  const hasActiveFilters = searchTerm.trim() !== '' || roleFilter !== 'all' || statusFilter !== 'all';

  const resetFilters = () => {
    setSearchTerm('');
    setRoleFilter('all');
    setStatusFilter('all');
  };

  const handleSort = (key) => {
    setSortConfig((prev) => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
  };

  const handleOpenModal = (user = null) => {
    if (user) {
      setEditingUser(user);
      setFormData({
        username: user.username,
        email: user.email,
        password: '',
        firstName: user.firstName,
        lastName: user.lastName,
        phone: user.phone || '',
        role: user.role,
        active: user.active,
      });
    } else {
      setEditingUser(null);
      setFormData(EMPTY_FORM);
    }
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!editingUser && !formData.password.trim()) {
      toast.error(t('users.passwordRequired'));
      return;
    }

    const toastId = 'user-save';
    toast.loading(editingUser ? t('users.savingEdit') : t('users.savingCreate'), { id: toastId });

    try {
      if (editingUser) {
        // Charge utile explicite : `UserUpdateAdminRequest` ne connaît pas `username`
        // (non modifiable) et n'accepte le mot de passe que s'il change réellement.
        const payload = {
          email: formData.email,
          firstName: formData.firstName,
          lastName: formData.lastName,
          phone: formData.phone,
          role: formData.role,
          active: formData.active,
        };
        if (formData.password.trim()) payload.password = formData.password;
        await api.put(`/users/${editingUser.id}`, payload);
        toast.success(t('users.updatedSuccess'), { id: toastId });
      } else {
        await api.post('/users', formData);
        toast.success(t('users.createdSuccess'), { id: toastId });
      }
      setShowModal(false);
      await fetchUsers();
    } catch (error) {
      console.error('Error saving user:', error);
      const raw = error.response?.data;
      const message =
        typeof raw === 'string' ? raw : raw?.error || raw?.message || t('users.saveError');
      toast.error(message, { id: toastId });
    }
  };

  const confirmDelete = async () => {
    const toastId = 'user-delete';
    toast.loading(t('users.deleting'), { id: toastId });
    try {
      await api.delete(`/users/${userToDelete.id}`);
      toast.success(t('users.deleteSuccess'), { id: toastId });
      await fetchUsers();
    } catch (error) {
      console.error('Error deleting user:', error);
      toast.error(error.response?.data?.message || t('users.deleteError'), { id: toastId });
    } finally {
      setUserToDelete(null);
    }
  };

  const handleToggleActive = async (user) => {
    const toastId = 'user-toggle';
    toast.loading(t('users.statusChanging'), { id: toastId });
    try {
      if (user.active) {
        await api.patch(`/users/${user.id}/deactivate`);
      } else {
        // Il n'existe pas d'endpoint de réactivation : on repasse par la mise à jour
        // complète, avec les seuls champs attendus par UserUpdateAdminRequest.
        await api.put(`/users/${user.id}`, {
          email: user.email,
          firstName: user.firstName,
          lastName: user.lastName,
          phone: user.phone || '',
          role: user.role,
          active: true,
        });
      }
      toast.success(user.active ? t('users.deactivatedSuccess') : t('users.activatedSuccess'), {
        id: toastId,
      });
      await fetchUsers();
    } catch (error) {
      console.error('Error toggling user status:', error);
      toast.error(t('users.statusChangeError'), { id: toastId });
    }
  };

  const handleViewActivities = async (user) => {
    setActivitiesUser(user);
    setActivities([]);
    setActivitiesLoading(true);
    try {
      // Journal paginé côté serveur : on affiche l'activité récente de l'utilisateur,
      // l'écran Historique permettant d'en parcourir l'intégralité.
      const response = await api.get(`/activities/user/${user.id}`, {
        params: { page: 0, size: 50, sort: 'createdAt,desc' },
      });
      setActivities(response.data.content || []);
    } catch (error) {
      console.error('Error fetching activities:', error);
      toast.error(t('users.loadActivitiesError'));
      setActivitiesUser(null);
    } finally {
      setActivitiesLoading(false);
    }
  };

  /* Le rôle ADMIN portait un badge rouge, teinte réservée dans la charte aux états négatifs
   * ou bloquants (cf. section Badges de `index.css`) : un administrateur n'est pas une
   * anomalie. L'indigo `accent` le distingue sans lui donner ce sens. */
  const roleBadge = (user) =>
    user.role === 'ADMIN' ? (
      <span className="badge-accent">
        <Shield className="w-3 h-3" aria-hidden="true" />
        {t('users.roleAdmin')}
      </span>
    ) : (
      <span className="badge-info">{t('users.roleCashier')}</span>
    );

  const columns = [
    {
      key: 'name',
      label: t('users.columnUser'),
      sortable: true,
      render: (user) => (
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 shrink-0 rounded-full bg-primary-100 dark:bg-primary-500/15 flex items-center justify-center">
            <span className="text-sm font-semibold text-primary-700 dark:text-primary-300">
              {initials(user)}
            </span>
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="font-semibold text-gray-900 dark:text-gray-100 truncate">
                {fullName(user)}
              </span>
              {isSelf(user) && <span className="badge-neutral">{t('users.selfBadge')}</span>}
            </div>
            <div className="text-xs text-gray-500 dark:text-gray-400 truncate">@{user.username}</div>
          </div>
        </div>
      ),
    },
    {
      key: 'contact',
      label: t('users.columnContact'),
      nowrap: false,
      render: (user) => (
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <Mail className="w-3.5 h-3.5 text-gray-400 shrink-0" aria-hidden="true" />
            <a
              href={`mailto:${user.email}`}
              className="text-gray-700 dark:text-gray-300 hover:text-primary-600 dark:hover:text-primary-400 hover:underline truncate"
            >
              {user.email}
            </a>
          </div>
          <div className="flex items-center gap-2">
            <Phone className="w-3.5 h-3.5 text-gray-400 shrink-0" aria-hidden="true" />
            {user.phone ? (
              <a
                href={`tel:${user.phone}`}
                className="text-gray-700 dark:text-gray-300 hover:text-primary-600 dark:hover:text-primary-400 hover:underline"
              >
                {user.phone}
              </a>
            ) : (
              <span className="text-gray-400 dark:text-gray-500">{t('users.noPhone')}</span>
            )}
          </div>
        </div>
      ),
    },
    {
      key: 'role',
      label: t('users.columnRole'),
      sortable: true,
      className: 'hidden md:table-cell',
      render: roleBadge,
    },
    {
      key: 'status',
      label: t('users.columnStatus'),
      sortable: true,
      render: (user) => (
        <span className={user.active ? 'badge-success' : 'badge-neutral'}>
          {user.active ? t('users.active') : t('users.inactive')}
        </span>
      ),
    },
    {
      key: 'createdAt',
      label: t('users.columnCreated'),
      sortable: true,
      className: 'hidden lg:table-cell',
      render: (user) => (
        <span className="tabular-nums text-gray-600 dark:text-gray-400">
          {formatDate(user.createdAt)}
        </span>
      ),
    },
  ];

  const emptyState = hasActiveFilters ? (
    <div className="flex flex-col items-center gap-3">
      <UserCog className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('users.noResultsTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('users.noResultsHint')}</p>
      </div>
      <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
        {t('users.resetFilters')}
      </Button>
    </div>
  ) : (
    <div className="flex flex-col items-center gap-3">
      <UserCog className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('users.emptyTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('users.emptyHint')}</p>
      </div>
      <Button variant="primary" size="sm" icon={Plus} onClick={() => handleOpenModal()}>
        {t('users.addUser')}
      </Button>
    </div>
  );

  return (
    <div className="space-y-6">
      {/* ---- En-tête ---- */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <UserCog aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('users.title')}</h1>
            <p className="page-subtitle">{t('users.subtitle')}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" icon={RefreshCw} onClick={fetchUsers} loading={loading}>
            {t('common.refresh')}
          </Button>
          <Button variant="primary" icon={Plus} onClick={() => handleOpenModal()}>
            {t('users.addUser')}
          </Button>
        </div>
      </div>

      {/* ---- Indicateurs ---- */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 sm:gap-6">
        <StatCard
          title={t('users.totalCount')}
          value={stats.total}
          subtitle={t('users.totalHint')}
          icon={UserCog}
          tone="info"
          loading={loading}
        />
        <StatCard
          title={t('users.adminCount')}
          value={stats.admins}
          subtitle={t('users.shareOfTotal', {
            percent: formatPercent(safeRatio(stats.admins, stats.total)),
          })}
          icon={ShieldCheck}
          tone="accent"
          loading={loading}
        />
        <StatCard
          title={t('users.cashierCount')}
          value={stats.cashiers}
          subtitle={t('users.shareOfTotal', {
            percent: formatPercent(safeRatio(stats.cashiers, stats.total)),
          })}
          icon={UserRound}
          tone="success"
          loading={loading}
        />
        {/* Les comptes désactivés n'apparaissaient nulle part : ce sont pourtant eux qu'on
            vient vérifier après un départ ou une suspension. */}
        <StatCard
          title={t('users.inactiveCount')}
          value={stats.inactive}
          subtitle={t('users.inactiveHint')}
          icon={UserX}
          tone="warning"
          loading={loading}
        />
      </div>

      {/* ---- Recherche et filtres ---- */}
      <div className="card">
        <div className="flex flex-col xl:flex-row xl:items-center gap-4">
          <SearchBox
            className="flex-1"
            value={searchTerm}
            onChange={setSearchTerm}
            placeholder={t('users.searchPlaceholder')}
            suggestions={userSuggestions}
            getKey={(u) => u.id}
            onSelectSuggestion={(u) => setSearchTerm(fullName(u))}
            renderSuggestion={(u) => (
              <span className="flex items-center justify-between gap-2">
                <span className="flex flex-col min-w-0">
                  <span className="font-medium truncate">{fullName(u)}</span>
                  <span className="text-xs text-gray-400 truncate">@{u.username} · {u.email}</span>
                </span>
                <span className="text-xs text-gray-500 shrink-0">
                  {u.role === 'ADMIN' ? t('users.roleAdmin') : t('users.roleCashier')}
                </span>
              </span>
            )}
          />
          <div className="flex flex-wrap items-center gap-3">
            <SegmentedFilter
              label={t('users.filterRole')}
              value={roleFilter}
              onChange={setRoleFilter}
              options={[
                { value: 'all', label: t('users.filterAll'), count: stats.total },
                { value: 'ADMIN', label: t('users.roleAdmin'), count: stats.admins },
                { value: 'CAISSIER', label: t('users.roleCashier'), count: stats.cashiers },
              ]}
            />
            <SegmentedFilter
              label={t('users.filterStatus')}
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: 'all', label: t('users.filterAll') },
                { value: 'active', label: t('users.filterActive'), count: stats.active },
                { value: 'inactive', label: t('users.filterInactive'), count: stats.inactive },
              ]}
            />
            {hasActiveFilters && (
              <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
                {t('users.resetFilters')}
              </Button>
            )}
          </div>
        </div>
      </div>

      {/* ---- Liste ---- */}
      <div className="card overflow-hidden p-0">
        <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div>
            <h2 className="section-title">{t('users.listTitle')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{t('users.listHint')}</p>
          </div>
        </div>

        <Table
          columns={columns}
          data={displayedUsers}
          loading={loading}
          emptyState={emptyState}
          sortKey={sortConfig.key}
          sortDirection={sortConfig.direction}
          onSort={handleSort}
          actions={(user) => (
            <>
              <button
                onClick={() => handleViewActivities(user)}
                className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                title={t('users.viewActivitiesTooltip')}
                aria-label={`${t('users.viewActivitiesTooltip')} — ${fullName(user)}`}
              >
                <Activity className="w-4 h-4" aria-hidden="true" />
              </button>
              <button
                onClick={() => handleOpenModal(user)}
                className="text-primary-600 hover:text-primary-900 dark:hover:text-primary-300 p-2 hover:bg-primary-50 dark:hover:bg-primary-500/10 rounded-lg transition-colors"
                title={t('common.edit')}
                aria-label={`${t('common.edit')} — ${fullName(user)}`}
              >
                <Edit className="w-4 h-4" aria-hidden="true" />
              </button>
              {/* Se désactiver ou se supprimer soi-même coupe la session en cours sans
                  qu'aucune garde côté backend ne s'y oppose : les deux actions sont
                  neutralisées sur sa propre ligne. */}
              <button
                onClick={() => handleToggleActive(user)}
                disabled={isSelf(user)}
                className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                title={
                  isSelf(user)
                    ? t('users.selfActionHint')
                    : user.active
                      ? t('users.deactivate')
                      : t('users.activate')
                }
                aria-label={`${user.active ? t('users.deactivate') : t('users.activate')} — ${fullName(user)}`}
              >
                {user.active ? (
                  <ToggleRight className="w-4 h-4" aria-hidden="true" />
                ) : (
                  <ToggleLeft className="w-4 h-4" aria-hidden="true" />
                )}
              </button>
              <button
                onClick={() => setUserToDelete(user)}
                disabled={isSelf(user)}
                className="text-red-600 hover:text-red-900 dark:hover:text-red-300 p-2 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                title={isSelf(user) ? t('users.selfActionHint') : t('common.delete')}
                aria-label={`${t('common.delete')} — ${fullName(user)}`}
              >
                <Trash2 className="w-4 h-4" aria-hidden="true" />
              </button>
            </>
          )}
        />

        {!loading && sortedUsers.length > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={sortedUsers.length}
            itemsPerPage={itemsPerPage}
            onPageChange={setCurrentPage}
            onItemsPerPageChange={setItemsPerPage}
          />
        )}
      </div>

      {/* ---- Création / modification ---- */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        size="lg"
        title={editingUser ? t('users.editTitle') : t('users.newTitle')}
      >
        <form onSubmit={handleSubmit} className="space-y-6">
          <p className="text-sm text-gray-500 dark:text-gray-400 -mt-1">
            {editingUser ? t('users.editSubtitle') : t('users.newSubtitle')}
          </p>

          {/* Informations personnelles */}
          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <UserRound className="w-4 h-4 text-primary-600 dark:text-primary-400" aria-hidden="true" />
              <h3 className="subsection-title">{t('users.personalInfoSection')}</h3>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormInput
                label={t('users.firstNameLabel')}
                name="firstName"
                value={formData.firstName}
                onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                placeholder={t('users.firstNamePlaceholder')}
                maxLength={100}
                required
              />
              <FormInput
                label={t('users.lastNameLabel')}
                name="lastName"
                value={formData.lastName}
                onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                placeholder={t('users.lastNamePlaceholder')}
                maxLength={100}
                required
              />
            </div>

            <FormInput
              label={t('common.phone')}
              name="phone"
              type="tel"
              value={formData.phone}
              onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
              placeholder={t('users.phonePlaceholder')}
            />
          </section>

          {/* Informations de connexion */}
          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <Shield className="w-4 h-4 text-primary-600 dark:text-primary-400" aria-hidden="true" />
              <h3 className="subsection-title">{t('users.loginInfoSection')}</h3>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <FormInput
                  label={t('users.usernameLabel')}
                  name="username"
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  placeholder={t('users.usernamePlaceholder')}
                  disabled={!!editingUser}
                  minLength={3}
                  maxLength={50}
                  required
                />
                <p className="mt-1.5 text-xs text-gray-500 dark:text-gray-400">
                  {editingUser ? t('users.usernameImmutable') : t('users.usernameHint')}
                </p>
              </div>
              <FormInput
                label={t('common.email')}
                name="email"
                type="email"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                placeholder={t('users.emailPlaceholder')}
                maxLength={100}
                required
              />
            </div>

            <div>
              <FormInput
                label={editingUser ? t('users.newPasswordLabel') : t('users.passwordLabel')}
                name="password"
                type="password"
                value={formData.password}
                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                placeholder={
                  editingUser ? t('users.passwordPlaceholderEdit') : t('users.passwordPlaceholderNew')
                }
                autoComplete="new-password"
                required={!editingUser}
              />
              <p className="mt-1.5 text-xs text-gray-500 dark:text-gray-400">
                {t('users.passwordHint')}
              </p>
            </div>
          </section>

          {/* Rôle et statut */}
          <section className="space-y-4">
            <div className="flex items-center gap-2 pb-2 border-b border-gray-200 dark:border-gray-700">
              <ShieldCheck className="w-4 h-4 text-primary-600 dark:text-primary-400" aria-hidden="true" />
              <h3 className="subsection-title">{t('users.roleSectionTitle')}</h3>
            </div>

            <FormSelect
              label={t('users.roleLabel')}
              name="role"
              value={formData.role}
              onChange={(e) => setFormData({ ...formData, role: e.target.value })}
              options={[
                { value: 'CAISSIER', label: t('users.roleCashierOption') },
                { value: 'ADMIN', label: t('users.roleAdminOption') },
              ]}
              required
            />

            <div className="flex items-center justify-between gap-4 p-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40">
              <div className="min-w-0">
                <label htmlFor="active" className="font-medium text-gray-900 dark:text-gray-100 cursor-pointer">
                  {t('users.accountActiveLabel')}
                </label>
                <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                  {formData.active ? t('users.accountActiveOn') : t('users.accountActiveOff')}
                </p>
              </div>
              <label className="relative inline-flex items-center cursor-pointer shrink-0">
                <input
                  type="checkbox"
                  id="active"
                  checked={formData.active}
                  onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                  className="sr-only peer"
                />
                <div className="w-11 h-6 bg-gray-200 dark:bg-gray-600 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-primary-500/30 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"></div>
              </label>
            </div>
          </section>

          <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
            <Button type="button" variant="secondary" onClick={() => setShowModal(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" variant="primary">
              {editingUser ? t('common.saveChanges') : t('users.createButton')}
            </Button>
          </div>
        </form>
      </Modal>

      {/* ---- Journal d'activité d'un utilisateur ---- */}
      <Modal
        isOpen={Boolean(activitiesUser)}
        onClose={() => setActivitiesUser(null)}
        title={t('users.activitiesHistoryTitle')}
      >
        {activitiesUser && (
          <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
            {t('users.activitiesFor', { name: fullName(activitiesUser) })}
          </p>
        )}

        {activitiesLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 4 }).map((_, index) => (
              <div key={index} className="skeleton h-16 w-full rounded-lg" />
            ))}
          </div>
        ) : activities.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-10 text-center">
            <Activity className="empty-state-icon" aria-hidden="true" />
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('users.noActivities')}</p>
          </div>
        ) : (
          <ul className="space-y-2 max-h-[26rem] overflow-y-auto">
            {activities.map((activity) => (
              <li
                key={activity.id}
                className="p-4 rounded-lg bg-gray-50 dark:bg-gray-900/40 border border-gray-100 dark:border-gray-700"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2 mb-1">
                      <span className={badgeClass(ACTIVITY_TONE[activity.actionType])}>
                        {t(actionLabelKey(activity.actionType))}
                      </span>
                      {activity.entity && (
                        <span className="text-xs text-gray-500 dark:text-gray-400">
                          {activity.entity}
                          {activity.entityId ? ` #${activity.entityId}` : ''}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-gray-700 dark:text-gray-300">
                      {activity.description || '—'}
                    </p>
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 tabular-nums whitespace-nowrap shrink-0">
                    {formatDate(activity.createdAt)} · {formatTime(activity.createdAt)}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Modal>

      {/* ---- Confirmation de suppression ---- */}
      <ConfirmModal
        isOpen={Boolean(userToDelete)}
        onClose={() => setUserToDelete(null)}
        onConfirm={confirmDelete}
        title={t('users.deleteTitle')}
        message={t('users.deleteMessage', { name: userToDelete ? fullName(userToDelete) : '' })}
        type="danger"
        confirmLabel={t('common.delete')}
      />
    </div>
  );
};

export default Users;
