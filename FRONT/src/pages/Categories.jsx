import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Search, Edit2, Trash2, ToggleLeft, ToggleRight } from 'lucide-react';
import { motion } from 'framer-motion';
import api from '../services/api';
import { toast } from 'react-hot-toast';
import ConfirmModal from '../components/ConfirmModal';

const Categories = () => {
  const { t } = useTranslation();
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    code: '',
    description: '',
    active: true,
  });

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      setLoading(true);
      console.log('Fetching categories...');
      const response = await api.get('/categories');
      console.log('Categories fetched:', response.data);
      setCategories(response.data);
    } catch (error) {
      console.error('Error fetching categories:', error);
      console.error('Error response:', error.response);
      toast.error('Erreur lors du chargement des catégories');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();

    // Validation
    if (!formData.name || formData.name.trim() === '') {
      toast.error('Le nom de la catégorie est obligatoire');
      return;
    }

    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    // Préparer les données à envoyer
    const dataToSend = {
      name: formData.name.trim(),
      code: formData.code?.trim() || null,
      description: formData.description?.trim() || null,
      active: formData.active !== false
    };

    try {
      if (editingCategory) {
        await api.put(`/categories/${editingCategory.id}`, dataToSend);
        toast.success('✅ Catégorie modifiée avec succès');
      } else {
        await api.post('/categories', dataToSend, {
          headers: {
            'Content-Type': 'application/json'
          }
        });
        toast.success('✅ Catégorie créée avec succès');
      }

      await fetchCategories();
      closeModal();
    } catch (error) {
      console.error('Error saving category:', error);
      const errorMessage = error.response?.data || error.message || 'Erreur lors de l\'enregistrement';
      toast.error('❌ Erreur: ' + errorMessage);
    }
  };

  const handleEdit = (category) => {
    setEditingCategory(category);
    setFormData({
      name: category.name,
      code: category.code || '',
      description: category.description || '',
      active: category.active,
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (window.confirm('Êtes-vous sûr de vouloir supprimer cette catégorie ?')) {
      try {
        await api.delete(`/categories/${id}`);
        toast.success('Catégorie supprimée avec succès');
        fetchCategories();
      } catch (error) {
        console.error('Error deleting category:', error);
        toast.error('Erreur lors de la suppression');
      }
    }
  };

  const handleToggleStatus = async (id) => {
    try {
      await api.patch(`/categories/${id}/toggle-status`);
      toast.success('Statut modifié avec succès');
      fetchCategories();
    } catch (error) {
      console.error('Error toggling status:', error);
      toast.error('Erreur lors de la modification du statut');
    }
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingCategory(null);
    setFormData({
      name: '',
      code: '',
      description: '',
      active: true,
    });
  };

  const filteredCategories = categories.filter(category =>
    category.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (category.code && category.code.toLowerCase().includes(searchTerm.toLowerCase()))
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">Chargement des catégories...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Catégories</h1>
          <p className="text-gray-600 mt-1">Gérer les catégories de produits</p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="btn-primary flex items-center gap-2"
        >
          <Plus className="w-5 h-5" />
          Nouvelle catégorie
        </button>
      </div>

      {/* Search */}
      <div className="card">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
          <input
            type="text"
            placeholder="Rechercher une catégorie..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="input-field pl-10"
          />
        </div>
      </div>

      {/* Categories Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredCategories.map((category) => (
          <motion.div
            key={category.id}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className="card hover:shadow-lg transition-shadow"
          >
            <div className="flex items-start justify-between mb-4">
              <div className="flex-1">
                <h3 className="text-lg font-bold text-gray-900">{category.name}</h3>
                {category.code && (
                  <p className="text-sm text-gray-500 mt-1">Code: {category.code}</p>
                )}
              </div>
              <span
                className={`px-3 py-1 rounded-full text-xs font-medium ${
                  category.active
                    ? 'bg-green-100 text-green-800'
                    : 'bg-gray-100 text-gray-800'
                }`}
              >
                {category.active ? 'Active' : 'Inactive'}
              </span>
            </div>

            {category.description && (
              <p className="text-sm text-gray-600 mb-4 line-clamp-2">
                {category.description}
              </p>
            )}

            <div className="flex items-center gap-2 pt-4 border-t border-gray-100">
              <button
                onClick={() => handleEdit(category)}
                className="flex-1 btn-secondary text-sm py-2 flex items-center justify-center gap-1"
              >
                <Edit2 className="w-4 h-4" />
                Modifier
              </button>
              <button
                onClick={() => handleToggleStatus(category.id)}
                className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                title={category.active ? 'Désactiver' : 'Activer'}
              >
                {category.active ? (
                  <ToggleRight className="w-5 h-5" />
                ) : (
                  <ToggleLeft className="w-5 h-5" />
                )}
              </button>
              <button
                onClick={() => handleDelete(category.id)}
                className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                title="Supprimer"
              >
                <Trash2 className="w-5 h-5" />
              </button>
            </div>
          </motion.div>
        ))}
      </div>

      {filteredCategories.length === 0 && (
        <div className="text-center py-12">
          <p className="text-gray-500">Aucune catégorie trouvée</p>
        </div>
      )}

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6"
          >
            <h2 className="text-2xl font-bold text-gray-900 mb-6">
              {editingCategory ? 'Modifier la catégorie' : 'Nouvelle catégorie'}
            </h2>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Nom *
                </label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="input-field"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Code
                </label>
                <input
                  type="text"
                  value={formData.code}
                  onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                  className="input-field"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Description
                </label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="input-field"
                  rows="3"
                />
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="active"
                  checked={formData.active}
                  onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                  className="w-4 h-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
                />
                <label htmlFor="active" className="text-sm font-medium text-gray-700">
                  Catégorie active
                </label>
              </div>

              <div className="flex gap-3 pt-4">
                <button
                  type="button"
                  onClick={closeModal}
                  className="flex-1 btn-secondary"
                >
                  Annuler
                </button>
                <button type="submit" className="flex-1 btn-primary">
                  {editingCategory ? 'Modifier' : 'Créer'}
                </button>
              </div>
            </form>
          </motion.div>
        </div>
      )}

      {/* Confirmation Modal */}
      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmSubmit}
        title={editingCategory ? "Confirmer la modification" : "Confirmer la création"}
        message={editingCategory
          ? `Voulez-vous vraiment modifier la catégorie "${formData.name}" ?`
          : `Voulez-vous vraiment créer la catégorie "${formData.name}" ?`
        }
        type="info"
      />
    </div>
  );
};

export default Categories;
