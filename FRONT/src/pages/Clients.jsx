import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Search, Edit, Trash2, Mail, Phone, MapPin, Building, User } from 'lucide-react';
import { motion } from 'framer-motion';
import clientService from '../services/clientService';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Pagination from '../components/Pagination';
import FormInput from '../components/FormInput';
import FormSelect from '../components/FormSelect';
import Button from '../components/Button';
import Table from '../components/Table';

const Clients = () => {
  const { t } = useTranslation();
  const [clients, setClients] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [editingClient, setEditingClient] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [formData, setFormData] = useState({
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
    active: true
  });

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
    } finally {
      setLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    setLoading(true);

    try {
      if (editingClient) {
        await clientService.updateClient(editingClient.id, formData);
        alert('✅ Client modifié avec succès!');
      } else {
        await clientService.createClient(formData);
        alert('✅ Client créé avec succès!');
      }

      await fetchClients();
      handleCloseModal();
    } catch (error) {
      console.error('Error saving client:', error);
      const errorMessage = error.response?.data || error.message || 'Erreur lors de l\'enregistrement du client';
      alert('❌ Erreur: ' + errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const handleEdit = (client) => {
    setEditingClient(client);
    setFormData(client);
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (window.confirm('Êtes-vous sûr de vouloir supprimer ce client ?')) {
      try {
        await clientService.deleteClient(id);
        alert('Client supprimé avec succès!');
        await fetchClients();
      } catch (error) {
        console.error('Error deleting client:', error);
        const errorMessage = error.response?.data || error.message || 'Erreur lors de la suppression du client';
        alert('❌ Erreur: ' + errorMessage);
      }
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingClient(null);
    setFormData({
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
      active: true
    });
  };

  const filteredClients = clients.filter((client) =>
    `${client.firstName} ${client.lastName} ${client.email}`
      .toLowerCase()
      .includes(searchTerm.toLowerCase())
  );

  // Pagination
  const totalPages = Math.ceil(filteredClients.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;
  const paginatedClients = filteredClients.slice(startIndex, endIndex);

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const handleItemsPerPageChange = (newItemsPerPage) => {
    setItemsPerPage(newItemsPerPage);
    setCurrentPage(1); // Reset to first page
  };

  const columns = [
    {
      key: 'name',
      label: 'Client',
      render: (client) => (
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-secondary-500 rounded-full flex items-center justify-center">
            <span className="text-white font-bold text-sm">
              {client.firstName?.charAt(0)}{client.lastName?.charAt(0)}
            </span>
          </div>
          <div>
            <div className="font-medium text-gray-900">
              {client.firstName} {client.lastName}
            </div>
            <div className="text-sm text-gray-500">{client.company || '-'}</div>
          </div>
        </div>
      )
    },
    {
      key: 'email',
      label: 'Email',
      render: (client) => (
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <Mail className="w-4 h-4" />
          {client.email || '-'}
        </div>
      )
    },
    {
      key: 'phone',
      label: 'Téléphone',
      render: (client) => (
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <Phone className="w-4 h-4" />
          {client.phone}
        </div>
      )
    },
    {
      key: 'type',
      label: 'Type',
      render: (client) => (
        <span className={`badge ${client.type === 'ENTREPRISE' ? 'badge-info' : 'badge-success'}`}>
          {client.type === 'ENTREPRISE' ? 'Entreprise' : 'Particulier'}
        </span>
      )
    },
    {
      key: 'status',
      label: 'Statut',
      render: (client) => (
        <span className={`badge ${client.active ? 'badge-success' : 'badge-danger'}`}>
          {client.active ? 'Actif' : 'Inactif'}
        </span>
      )
    }
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">{t('clients.title')}</h1>
          <p className="text-gray-600 mt-1">Gérez vos clients et leurs informations</p>
        </div>
        <Button
          variant="primary"
          icon={Plus}
          onClick={() => setShowModal(true)}
        >
          {t('clients.addClient')}
        </Button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <div className="card bg-gradient-to-br from-blue-50 to-cyan-50 border-blue-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-blue-600 font-medium">Total Clients</p>
              <p className="text-3xl font-bold text-blue-700">{clients.length}</p>
            </div>
            <User className="w-12 h-12 text-blue-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-green-50 to-emerald-50 border-green-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-green-600 font-medium">Actifs</p>
              <p className="text-3xl font-bold text-green-700">
                {clients.filter(c => c.active).length}
              </p>
            </div>
            <User className="w-12 h-12 text-green-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-purple-50 to-pink-50 border-purple-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-purple-600 font-medium">Particuliers</p>
              <p className="text-3xl font-bold text-purple-700">
                {clients.filter(c => c.type === 'PARTICULIER').length}
              </p>
            </div>
            <User className="w-12 h-12 text-purple-600 opacity-50" />
          </div>
        </div>

        <div className="card bg-gradient-to-br from-orange-50 to-red-50 border-orange-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-orange-600 font-medium">Entreprises</p>
              <p className="text-3xl font-bold text-orange-700">
                {clients.filter(c => c.type === 'ENTREPRISE').length}
              </p>
            </div>
            <Building className="w-12 h-12 text-orange-600 opacity-50" />
          </div>
        </div>
      </div>

      {/* Search */}
      <div className="card">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="text"
            placeholder={t('common.search') + ' clients...'}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="input-field pl-10"
          />
        </div>
      </div>

      {/* Table */}
      <div className="card overflow-hidden">
        <Table
          columns={columns}
          data={paginatedClients}
          actions={(client) => (
            <>
              <button
                onClick={() => handleEdit(client)}
                className="text-primary-600 hover:text-primary-900 p-2 hover:bg-primary-50 rounded-lg transition-colors"
                title="Modifier"
              >
                <Edit className="w-4 h-4" />
              </button>
              <button
                onClick={() => handleDelete(client.id)}
                className="text-red-600 hover:text-red-900 p-2 hover:bg-red-50 rounded-lg transition-colors"
                title="Supprimer"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </>
          )}
        />

        {/* Pagination */}
        {filteredClients.length > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={filteredClients.length}
            itemsPerPage={itemsPerPage}
            onPageChange={handlePageChange}
            onItemsPerPageChange={handleItemsPerPageChange}
          />
        )}
      </div>

      {/* Modal Form */}
      <Modal
        isOpen={showModal}
        onClose={handleCloseModal}
        title={editingClient ? 'Modifier le client' : 'Nouveau client'}
        size="lg"
      >
        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <FormInput
              label="Prénom"
              name="firstName"
              value={formData.firstName}
              onChange={handleInputChange}
              placeholder="Jean"
              required
              icon={User}
            />

            <FormInput
              label="Nom"
              name="lastName"
              value={formData.lastName}
              onChange={handleInputChange}
              placeholder="Dupont"
              required
              icon={User}
            />

            <FormInput
              label="Email"
              name="email"
              type="email"
              value={formData.email}
              onChange={handleInputChange}
              placeholder="jean.dupont@email.com"
              icon={Mail}
            />

            <FormInput
              label="Téléphone"
              name="phone"
              type="tel"
              value={formData.phone}
              onChange={handleInputChange}
              placeholder="0600000000"
              required
              icon={Phone}
            />

            <FormSelect
              label="Type de client"
              name="type"
              value={formData.type}
              onChange={handleInputChange}
              required
              options={[
                { value: 'PARTICULIER', label: 'Particulier' },
                { value: 'ENTREPRISE', label: 'Entreprise' }
              ]}
            />

            <FormInput
              label="Entreprise"
              name="company"
              value={formData.company}
              onChange={handleInputChange}
              placeholder="Nom de l'entreprise"
              icon={Building}
            />

            <FormInput
              label="Adresse"
              name="address"
              value={formData.address}
              onChange={handleInputChange}
              placeholder="123 Rue de la Paix"
              icon={MapPin}
            />

            <FormInput
              label="Ville"
              name="city"
              value={formData.city}
              onChange={handleInputChange}
              placeholder="Paris"
              icon={MapPin}
            />

            <FormInput
              label="Code postal"
              name="postalCode"
              value={formData.postalCode}
              onChange={handleInputChange}
              placeholder="75000"
            />

            <FormInput
              label="Pays"
              name="country"
              value={formData.country}
              onChange={handleInputChange}
              placeholder="France"
            />
          </div>

          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="active"
              name="active"
              checked={formData.active}
              onChange={handleInputChange}
              className="w-4 h-4 text-primary-600 rounded focus:ring-primary-500"
            />
            <label htmlFor="active" className="text-sm font-medium text-gray-700">
              Client actif
            </label>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200">
            <Button
              variant="secondary"
              onClick={handleCloseModal}
              type="button"
            >
              Annuler
            </Button>
            <Button
              variant="primary"
              type="submit"
              loading={loading}
            >
              {editingClient ? 'Modifier' : 'Créer'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Confirmation Modal */}
      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmSubmit}
        title={editingClient ? "Confirmer la modification" : "Confirmer la création"}
        message={editingClient
          ? `Voulez-vous vraiment modifier le client ${formData.firstName} ${formData.lastName} ?`
          : `Voulez-vous vraiment créer le client ${formData.firstName} ${formData.lastName} ?`
        }
        type="info"
      />
    </div>
  );
};

export default Clients;
