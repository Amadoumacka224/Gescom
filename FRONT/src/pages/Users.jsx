import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, UserCircle, Shield, Edit, Trash2, Search, Eye, EyeOff, Activity } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { toast } from 'react-hot-toast';
import api from '../services/api';
import Modal from '../components/Modal';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import Button from '../components/Button';
import Pagination from '../components/Pagination';

const Users = () => {
  const { t } = useTranslation();
  const [users, setUsers] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [showActivitiesModal, setShowActivitiesModal] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [selectedUserActivities, setSelectedUserActivities] = useState([]);
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    phone: '',
    role: 'CAISSIER',
    active: true
  });
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    try {
      setLoading(true);
      const response = await api.get('/users');
      setUsers(response.data);
    } catch (error) {
      console.error('Error fetching users:', error);
    } finally {
      setLoading(false);
    }
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
        active: user.active
      });
    } else {
      setEditingUser(null);
      setFormData({
        username: '',
        email: '',
        password: '',
        firstName: '',
        lastName: '',
        phone: '',
        role: 'CAISSIER',
        active: true
      });
    }
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {
      toast.loading(editingUser ? 'Modification en cours...' : 'Création en cours...', { id: 'user-save' });

      if (editingUser) {
        // Update user
        const dataToSend = { ...formData };
        if (!dataToSend.password) {
          delete dataToSend.password; // Don't send empty password
        } else {
          // For update, send as rawPassword if password is provided
          dataToSend.rawPassword = dataToSend.password;
          delete dataToSend.password;
        }
        await api.put(`/users/${editingUser.id}`, dataToSend);
        toast.success('✅ Utilisateur modifié avec succès!', { id: 'user-save', duration: 4000 });
      } else {
        // Create user - validate password
        if (!formData.password || formData.password.trim() === '') {
          toast.error('❌ Le mot de passe est obligatoire pour créer un utilisateur', { id: 'user-save', duration: 4000 });
          return;
        }
        // Send as rawPassword for creation
        const dataToSend = {
          ...formData,
          rawPassword: formData.password
        };
        delete dataToSend.password;
        console.log('Data being sent to backend:', dataToSend);
        await api.post('/users', dataToSend);
        toast.success('✅ Utilisateur créé avec succès!', { id: 'user-save', duration: 4000 });
      }
      setShowModal(false);
      fetchUsers();
    } catch (error) {
      console.error('Error saving user:', error);
      const errorMessage = error.response?.data || 'Erreur lors de l\'enregistrement';
      toast.error(`❌ ${errorMessage}`, {
        id: 'user-save',
        duration: 6000,
        style: {
          background: '#FEE2E2',
          color: '#991B1B',
          border: '1px solid #FCA5A5',
          padding: '16px',
          fontSize: '14px',
          fontWeight: '500'
        }
      });
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Êtes-vous sûr de vouloir supprimer cet utilisateur ?')) {
      return;
    }

    try {
      toast.loading('Suppression en cours...', { id: 'user-delete' });
      await api.delete(`/users/${id}`);
      fetchUsers();
      toast.success('✅ Utilisateur supprimé avec succès!', { id: 'user-delete', duration: 4000 });
    } catch (error) {
      console.error('Error deleting user:', error);
      toast.error('❌ Erreur lors de la suppression', { id: 'user-delete', duration: 4000 });
    }
  };

  const handleToggleActive = async (user) => {
    try {
      toast.loading('Modification du statut...', { id: 'user-toggle' });
      if (user.active) {
        await api.patch(`/users/${user.id}/deactivate`);
      } else {
        // Reactivate by updating with active: true
        await api.put(`/users/${user.id}`, { ...user, active: true });
      }
      fetchUsers();
      toast.success(`✅ Utilisateur ${user.active ? 'désactivé' : 'activé'} avec succès!`, { id: 'user-toggle', duration: 4000 });
    } catch (error) {
      console.error('Error toggling user status:', error);
      toast.error('❌ Erreur lors de la modification du statut', { id: 'user-toggle', duration: 4000 });
    }
  };

  const handleViewActivities = async (user) => {
    try {
      const response = await api.get(`/activities/user/${user.id}`);
      setSelectedUserActivities(response.data);
      setShowActivitiesModal(true);
    } catch (error) {
      console.error('Error fetching activities:', error);
      toast.error('❌ Erreur lors du chargement des activités', { duration: 4000 });
    }
  };

  const getRoleBadge = (role) => {
    return role === 'ADMIN'
      ? <span className="badge bg-red-100 text-red-700 border-red-200 flex items-center gap-1 w-fit"><Shield className="w-3 h-3" />Admin</span>
      : <span className="badge bg-blue-100 text-blue-700 border-blue-200 w-fit">Caissier</span>;
  };

  const getActionTypeBadge = (actionType) => {
    const types = {
      CREATE: { class: 'bg-green-100 text-green-700', text: 'Création' },
      UPDATE: { class: 'bg-blue-100 text-blue-700', text: 'Modification' },
      DELETE: { class: 'bg-red-100 text-red-700', text: 'Suppression' },
      VIEW: { class: 'bg-gray-100 text-gray-700', text: 'Consultation' },
      LOGIN: { class: 'bg-purple-100 text-purple-700', text: 'Connexion' },
      LOGOUT: { class: 'bg-orange-100 text-orange-700', text: 'Déconnexion' },
      SALE: { class: 'bg-green-100 text-green-700', text: 'Vente' },
      PAYMENT: { class: 'bg-emerald-100 text-emerald-700', text: 'Paiement' },
      STOCK_IN: { class: 'bg-blue-100 text-blue-700', text: 'Entrée stock' },
      STOCK_OUT: { class: 'bg-red-100 text-red-700', text: 'Sortie stock' }
    };
    const type = types[actionType] || { class: 'bg-gray-100 text-gray-700', text: actionType };
    return <span className={`px-2 py-1 rounded text-xs font-medium ${type.class}`}>{type.text}</span>;
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString('fr-FR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const filteredUsers = users.filter(user =>
    `${user.username} ${user.email} ${user.firstName} ${user.lastName}`
      .toLowerCase()
      .includes(searchTerm.toLowerCase())
  );

  // Pagination
  const totalPages = Math.ceil(filteredUsers.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const currentUsers = filteredUsers.slice(startIndex, endIndex);

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const handleItemsPerPageChange = (newItemsPerPage) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">{t('users.title')}</h1>
          <p className="text-gray-600 mt-1">Gérez les utilisateurs et leurs permissions</p>
        </div>
        <Button
          variant="primary"
          icon={Plus}
          onClick={() => handleOpenModal()}
        >
          {t('users.addUser')}
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="card bg-gradient-to-br from-blue-50 to-cyan-50 border-blue-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-blue-600 font-medium">Total utilisateurs</p>
              <p className="text-3xl font-bold text-blue-700">{users.length}</p>
            </div>
            <UserCircle className="w-12 h-12 text-blue-600 opacity-50" />
          </div>
        </div>
        <div className="card bg-gradient-to-br from-red-50 to-pink-50 border-red-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-red-600 font-medium">Administrateurs</p>
              <p className="text-3xl font-bold text-red-700">
                {users.filter(u => u.role === 'ADMIN').length}
              </p>
            </div>
            <Shield className="w-12 h-12 text-red-600 opacity-50" />
          </div>
        </div>
        <div className="card bg-gradient-to-br from-green-50 to-emerald-50 border-green-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-green-600 font-medium">Caissiers</p>
              <p className="text-3xl font-bold text-green-700">
                {users.filter(u => u.role === 'CAISSIER').length}
              </p>
            </div>
            <UserCircle className="w-12 h-12 text-green-600 opacity-50" />
          </div>
        </div>
      </div>

      {/* Search */}
      <div className="card">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="text"
            placeholder="Rechercher un utilisateur..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="input pl-10 w-full"
          />
        </div>
      </div>

      {/* Users Table */}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Utilisateur</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Email</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Téléphone</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Rôle</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Statut</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {currentUsers.map((user) => (
                <motion.tr
                  key={user.id}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  className="hover:bg-gray-50"
                >
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-primary-100 flex items-center justify-center">
                        <span className="text-primary-700 font-semibold">
                          {user.firstName?.charAt(0)}{user.lastName?.charAt(0)}
                        </span>
                      </div>
                      <div>
                        <p className="font-medium text-gray-900">{user.firstName} {user.lastName}</p>
                        <p className="text-sm text-gray-500">@{user.username}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">{user.email}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600">{user.phone || '-'}</td>
                  <td className="px-6 py-4 whitespace-nowrap">{getRoleBadge(user.role)}</td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`badge ${user.active ? 'badge-success' : 'badge-secondary'}`}>
                      {user.active ? 'Actif' : 'Inactif'}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    <div className="flex items-center gap-2">
                      {user.role === 'CAISSIER' && (
                        <button
                          onClick={() => handleViewActivities(user)}
                          className="text-purple-600 hover:text-purple-900"
                          title="Voir les activités"
                        >
                          <Activity className="w-4 h-4" />
                        </button>
                      )}
                      <button
                        onClick={() => handleOpenModal(user)}
                        className="text-blue-600 hover:text-blue-900"
                        title="Modifier"
                      >
                        <Edit className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleToggleActive(user)}
                        className={user.active ? 'text-orange-600 hover:text-orange-900' : 'text-green-600 hover:text-green-900'}
                        title={user.active ? 'Désactiver' : 'Activer'}
                      >
                        {user.active ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </button>
                      <button
                        onClick={() => handleDelete(user.id)}
                        className="text-red-600 hover:text-red-900"
                        title="Supprimer"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </motion.tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {filteredUsers.length > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={filteredUsers.length}
            itemsPerPage={itemsPerPage}
            onPageChange={handlePageChange}
            onItemsPerPageChange={handleItemsPerPageChange}
          />
        )}
      </div>

      {/* Create/Edit Modal */}
      <AnimatePresence>
        {showModal && (
          <Modal
            isOpen={showModal}
            onClose={() => setShowModal(false)}
            size="lg"
            title={
              <div className="flex items-center gap-3">
                <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${
                  editingUser ? 'bg-blue-100' : 'bg-green-100'
                }`}>
                  <UserCircle className={`w-6 h-6 ${editingUser ? 'text-blue-600' : 'text-green-600'}`} />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-gray-900">
                    {editingUser ? 'Modifier l\'utilisateur' : 'Nouvel utilisateur'}
                  </h3>
                  <p className="text-sm text-gray-500">
                    {editingUser ? 'Mettez à jour les informations' : 'Remplissez les informations'}
                  </p>
                </div>
              </div>
            }
          >
            <form onSubmit={handleSubmit} className="space-y-6">
              {/* Informations personnelles */}
              <div className="space-y-4">
                <div className="flex items-center gap-2 pb-2 border-b border-gray-200">
                  <UserCircle className="w-5 h-5 text-primary-600" />
                  <h4 className="font-semibold text-gray-900">Informations personnelles</h4>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <FormInput
                    label="Prénom"
                    value={formData.firstName}
                    onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                    required
                    placeholder="Ex: Jean"
                  />
                  <FormInput
                    label="Nom"
                    value={formData.lastName}
                    onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                    required
                    placeholder="Ex: Dupont"
                  />
                </div>

                <FormInput
                  label="Téléphone"
                  type="tel"
                  value={formData.phone}
                  onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                  placeholder="Ex: +33 6 12 34 56 78"
                />
              </div>

              {/* Informations de connexion */}
              <div className="space-y-4">
                <div className="flex items-center gap-2 pb-2 border-b border-gray-200">
                  <Shield className="w-5 h-5 text-primary-600" />
                  <h4 className="font-semibold text-gray-900">Informations de connexion</h4>
                </div>

                <FormInput
                  label="Nom d'utilisateur"
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  disabled={!!editingUser}
                  required
                  placeholder="Ex: jdupont"
                />

                <FormInput
                  label="Email"
                  type="email"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  required
                  placeholder="Ex: jean.dupont@exemple.com"
                />

                <FormInput
                  label={editingUser ? 'Nouveau mot de passe (laisser vide pour ne pas changer)' : 'Mot de passe'}
                  type="password"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  required={!editingUser}
                  placeholder={editingUser ? 'Laissez vide pour conserver' : 'Minimum 6 caractères'}
                />
              </div>

              {/* Rôle et statut */}
              <div className="space-y-4">
                <div className="flex items-center gap-2 pb-2 border-b border-gray-200">
                  <Shield className="w-5 h-5 text-primary-600" />
                  <h4 className="font-semibold text-gray-900">Rôle et permissions</h4>
                </div>

                <FormSelect
                  label="Rôle"
                  value={formData.role}
                  onChange={(e) => setFormData({ ...formData, role: e.target.value })}
                  options={[
                    { value: 'CAISSIER', label: '👤 Caissier - Accès limité aux ventes' },
                    { value: 'ADMIN', label: '🛡️ Administrateur - Accès complet' }
                  ]}
                  required
                />

                <div className="bg-gray-50 p-4 rounded-lg border border-gray-200">
                  <div className="flex items-center justify-between">
                    <div className="flex-1">
                      <label htmlFor="active" className="font-medium text-gray-900 cursor-pointer">
                        Compte actif
                      </label>
                      <p className="text-sm text-gray-600 mt-1">
                        {formData.active ? 'L\'utilisateur peut se connecter' : 'L\'utilisateur ne peut pas se connecter'}
                      </p>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        id="active"
                        checked={formData.active}
                        onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                        className="sr-only peer"
                      />
                      <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-primary-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"></div>
                    </label>
                  </div>
                </div>
              </div>

              {/* Actions */}
              <div className="flex gap-3 pt-4 border-t border-gray-200">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setShowModal(false)}
                  className="flex-1"
                >
                  Annuler
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  className="flex-1"
                >
                  {editingUser ? '✓ Enregistrer les modifications' : '✓ Créer l\'utilisateur'}
                </Button>
              </div>
            </form>
          </Modal>
        )}
      </AnimatePresence>

      {/* Activities Modal */}
      <AnimatePresence>
        {showActivitiesModal && (
          <Modal
            isOpen={showActivitiesModal}
            onClose={() => setShowActivitiesModal(false)}
            title="Historique des activités"
          >
            <div className="space-y-4 max-h-96 overflow-y-auto">
              {selectedUserActivities.length === 0 ? (
                <p className="text-center text-gray-500 py-8">Aucune activité enregistrée</p>
              ) : (
                <div className="space-y-3">
                  {selectedUserActivities.map((activity) => (
                    <div key={activity.id} className="p-4 bg-gray-50 rounded-lg">
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-2">
                            {getActionTypeBadge(activity.actionType)}
                            <span className="text-xs text-gray-500">{activity.entity}</span>
                          </div>
                          <p className="text-sm text-gray-700">{activity.description || '-'}</p>
                        </div>
                        <div className="text-right">
                          <p className="text-xs text-gray-500">{formatDate(activity.createdAt)}</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </Modal>
        )}
      </AnimatePresence>
    </div>
  );
};

export default Users;
