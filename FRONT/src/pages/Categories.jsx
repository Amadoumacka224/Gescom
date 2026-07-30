import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'react-hot-toast';
import {
  Tags,
  Plus,
  RefreshCw,
  Edit,
  Trash2,
  ToggleLeft,
  ToggleRight,
  CheckCircle2,
  CircleSlash,
  PackageSearch,
  X,
} from 'lucide-react';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Table from '../components/Table';
import Pagination from '../components/Pagination';
import SearchBox from '../components/SearchBox';
import SegmentedFilter from '../components/SegmentedFilter';
import StatCard from '../components/StatCard';
import FormInput from '../components/FormInput';
import Button from '../components/Button';
import { rankSuggestions } from '../utils/searchSuggestions';
import { formatDate, formatPercent, safeRatio } from '../utils/format';

const EMPTY_FORM = { name: '', code: '', description: '', active: true };

const Categories = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  // Le backend restreint create/update/delete/toggle-status aux ADMIN
  // (cf. CategoryController @PreAuthorize("hasRole('ADMIN')")). On masque les
  // actions correspondantes côté UI pour éviter les 403 au CAISSIER.
  const isAdmin = user?.role === 'ADMIN';

  const [categories, setCategories] = useState([]);
  // Les produits ne servent qu'à compter ce qui est rattaché à chaque catégorie :
  // CategoryResponse ne porte pas ce total, et c'est lui qui dit si une catégorie est
  // vide (donc archivable) ou au contraire impossible à supprimer.
  const [productCounts, setProductCounts] = useState(null);
  const [loading, setLoading] = useState(true);

  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [sortConfig, setSortConfig] = useState({ key: 'name', direction: 'asc' });
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);

  const [showModal, setShowModal] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [formData, setFormData] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState('');
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [categoryToDelete, setCategoryToDelete] = useState(null);

  useEffect(() => {
    fetchData();
  }, []);

  // Retour à la première page dès que le contenu de la liste change sous les pieds
  // de l'utilisateur : rester en page 4 d'un résultat qui n'en compte plus qu'une
  // affiche un tableau vide.
  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, statusFilter, itemsPerPage]);

  const fetchData = async () => {
    setLoading(true);
    // Le décompte des produits est accessoire : son échec ne doit pas priver
    // l'utilisateur de la liste des catégories.
    const [categoriesResult, productsResult] = await Promise.allSettled([
      api.get('/categories'),
      api.get('/products'),
    ]);

    if (categoriesResult.status === 'fulfilled') {
      setCategories(categoriesResult.value.data || []);
    } else {
      console.error('Error fetching categories:', categoriesResult.reason);
      toast.error(t('categories.loadError'));
    }

    if (productsResult.status === 'fulfilled') {
      const counts = {};
      for (const product of productsResult.value.data || []) {
        const categoryId = product.category?.id;
        if (categoryId) counts[categoryId] = (counts[categoryId] || 0) + 1;
      }
      setProductCounts(counts);
    } else {
      console.error('Error fetching products:', productsResult.reason);
      setProductCounts(null);
    }

    setLoading(false);
  };

  const countFor = (category) => productCounts?.[category.id] ?? 0;

  const stats = useMemo(() => {
    const total = categories.length;
    const active = categories.filter((c) => c.active).length;
    return {
      total,
      active,
      inactive: total - active,
      empty: productCounts ? categories.filter((c) => !productCounts[c.id]).length : null,
    };
  }, [categories, productCounts]);

  const filteredCategories = useMemo(() => {
    const query = searchTerm.trim().toLowerCase();
    return categories.filter((category) => {
      if (statusFilter === 'active' && !category.active) return false;
      if (statusFilter === 'inactive' && category.active) return false;
      if (!query) return true;
      return [category.name, category.code, category.description]
        .filter(Boolean)
        .some((field) => field.toLowerCase().includes(query));
    });
  }, [categories, searchTerm, statusFilter]);

  const sortedCategories = useMemo(() => {
    const { key, direction } = sortConfig;
    const factor = direction === 'asc' ? 1 : -1;

    const valueOf = (category) => {
      if (key === 'products') return countFor(category);
      if (key === 'status') return category.active ? 1 : 0;
      if (key === 'updatedAt') return new Date(category.updatedAt || category.createdAt || 0).getTime();
      return category[key] ?? '';
    };

    return [...filteredCategories].sort((a, b) => {
      const left = valueOf(a);
      const right = valueOf(b);
      if (typeof left === 'string' || typeof right === 'string') {
        // `localeCompare` pour que « Épicerie » se classe avec les E et non après les Z.
        return String(left).localeCompare(String(right), 'fr', { sensitivity: 'base' }) * factor;
      }
      return (left - right) * factor;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filteredCategories, sortConfig, productCounts]);

  const totalPages = Math.max(1, Math.ceil(sortedCategories.length / itemsPerPage));
  const displayedCategories = sortedCategories.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  const categorySuggestions = useMemo(
    () => rankSuggestions(categories, searchTerm, (c) => [c.name, c.code, c.description], 6),
    [categories, searchTerm]
  );

  const hasActiveFilters = searchTerm.trim() !== '' || statusFilter !== 'all';

  const resetFilters = () => {
    setSearchTerm('');
    setStatusFilter('all');
  };

  const handleSort = (key) => {
    setSortConfig((prev) => ({
      key,
      direction: prev.key === key && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
  };

  const openCreateModal = () => {
    setEditingCategory(null);
    setFormData(EMPTY_FORM);
    setFormError('');
    setShowModal(true);
  };

  const handleEdit = (category) => {
    setEditingCategory(category);
    setFormData({
      name: category.name,
      code: category.code || '',
      description: category.description || '',
      active: category.active,
    });
    setFormError('');
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingCategory(null);
    setFormData(EMPTY_FORM);
    setFormError('');
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      // Erreur posée sous le champ concerné plutôt qu'en notification : elle reste
      // visible pendant la correction.
      setFormError(t('categories.nameRequired'));
      return;
    }
    setFormError('');
    setShowConfirmModal(true);
  };

  const confirmSubmit = async () => {
    const payload = {
      name: formData.name.trim(),
      code: formData.code?.trim() || null,
      description: formData.description?.trim() || null,
      active: formData.active !== false,
    };

    try {
      if (editingCategory) {
        await api.put(`/categories/${editingCategory.id}`, payload);
        toast.success(t('categories.updatedSuccess'));
      } else {
        await api.post('/categories', payload);
        toast.success(t('categories.createdSuccess'));
      }
      await fetchData();
      closeModal();
    } catch (error) {
      console.error('Error saving category:', error);
      toast.error(error.response?.data?.message || t('categories.saveError'));
    }
  };

  /* Une catégorie encore rattachée à des produits ne peut pas être supprimée : la clé
   * étrangère `product.category_id` n'a ni cascade ni mise à null, la requête partirait
   * pour revenir en erreur de contrainte. On l'annonce avant, avec la marche à suivre. */
  const requestDelete = (category) => {
    const count = countFor(category);
    if (count > 0) {
      // `total` et non `count` : i18next réserve `count` à la pluralisation et irait
      // chercher une clé `deleteBlocked_one` / `_other` qui n'existe pas.
      toast.error(t('categories.deleteBlocked', { name: category.name, total: count }));
      return;
    }
    setCategoryToDelete(category);
  };

  const confirmDelete = async () => {
    try {
      await api.delete(`/categories/${categoryToDelete.id}`);
      toast.success(t('categories.deleteSuccess'));
      await fetchData();
    } catch (error) {
      console.error('Error deleting category:', error);
      toast.error(error.response?.data?.message || t('categories.deleteError'));
    } finally {
      setCategoryToDelete(null);
    }
  };

  const handleToggleStatus = async (category) => {
    try {
      await api.patch(`/categories/${category.id}/toggle-status`);
      toast.success(t('categories.statusChangedSuccess'));
      await fetchData();
    } catch (error) {
      console.error('Error toggling status:', error);
      toast.error(t('categories.statusChangeError'));
    }
  };

  /* Colonnes ordonnées par importance décroissante : identité, puis code, puis volume,
   * puis état, puis contexte. Les colonnes secondaires disparaissent sur écran étroit
   * (`hidden … table-cell`) plutôt que de pousser le tableau en défilement horizontal,
   * où la colonne d'actions devient inatteignable. */
  const columns = [
    {
      key: 'name',
      label: t('categories.columnName'),
      sortable: true,
      nowrap: false,
      render: (category) => (
        <div className="min-w-0">
          <div className="font-semibold text-gray-900 dark:text-gray-100">{category.name}</div>
          <div className="text-xs text-gray-500 dark:text-gray-400 line-clamp-1">
            {category.description || t('categories.noDescription')}
          </div>
        </div>
      ),
    },
    {
      key: 'code',
      label: t('categories.columnCode'),
      sortable: true,
      className: 'hidden sm:table-cell',
      render: (category) =>
        category.code ? (
          <span className="font-mono text-xs text-gray-700 dark:text-gray-300">{category.code}</span>
        ) : (
          <span className="text-gray-400 dark:text-gray-500">—</span>
        ),
    },
    {
      key: 'products',
      label: t('categories.columnProducts'),
      sortable: true,
      className: 'hidden md:table-cell',
      render: (category) => {
        if (!productCounts) return <span className="text-gray-400 dark:text-gray-500">—</span>;
        const count = countFor(category);
        // Une catégorie vide est signalée : c'est elle qu'on peut supprimer ou compléter.
        return count === 0 ? (
          <span className="badge-warning">{t('categories.emptyBadge')}</span>
        ) : (
          <span className="tabular-nums font-semibold text-gray-900 dark:text-gray-100">{count}</span>
        );
      },
    },
    {
      key: 'status',
      label: t('categories.columnStatus'),
      sortable: true,
      render: (category) => (
        <span className={category.active ? 'badge-success' : 'badge-neutral'}>
          {category.active ? t('categories.active') : t('categories.inactive')}
        </span>
      ),
    },
    {
      key: 'updatedAt',
      label: t('categories.columnUpdated'),
      sortable: true,
      className: 'hidden lg:table-cell',
      render: (category) => (
        <span className="tabular-nums text-gray-600 dark:text-gray-400">
          {formatDate(category.updatedAt || category.createdAt)}
        </span>
      ),
    },
  ];

  // Deux vides bien distincts : « la recherche ne donne rien » appelle un ajustement des
  // filtres, « il n'y a rien » appelle une création.
  const emptyState = hasActiveFilters ? (
    <div className="flex flex-col items-center gap-3">
      <Tags className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('categories.noResultsTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('categories.noResultsHint')}</p>
      </div>
      <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
        {t('categories.resetFilters')}
      </Button>
    </div>
  ) : (
    <div className="flex flex-col items-center gap-3">
      <Tags className="empty-state-icon" aria-hidden="true" />
      <div>
        <p className="font-medium text-gray-700 dark:text-gray-300">{t('categories.emptyTitle')}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('categories.emptyHint')}</p>
      </div>
      {isAdmin && (
        <Button variant="primary" size="sm" icon={Plus} onClick={openCreateModal}>
          {t('categories.addNew')}
        </Button>
      )}
    </div>
  );

  return (
    <div className="space-y-6">
      {/* ---- En-tête ---- */}
      <div className="page-header">
        <div className="flex items-center gap-3">
          <div className="page-header-icon">
            <Tags aria-hidden="true" />
          </div>
          <div>
            <h1 className="page-title">{t('categories.title')}</h1>
            <p className="page-subtitle">{t('categories.subtitle')}</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" icon={RefreshCw} onClick={fetchData} loading={loading}>
            {t('common.refresh')}
          </Button>
          {isAdmin && (
            <Button variant="primary" icon={Plus} onClick={openCreateModal}>
              {t('categories.addNew')}
            </Button>
          )}
        </div>
      </div>

      {/* ---- Indicateurs ---- */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 sm:gap-6">
        <StatCard
          title={t('categories.statTotal')}
          value={stats.total}
          subtitle={t('categories.statTotalHint')}
          icon={Tags}
          tone="info"
          loading={loading}
        />
        <StatCard
          title={t('categories.statActive')}
          value={stats.active}
          subtitle={t('categories.shareOfTotal', {
            percent: formatPercent(safeRatio(stats.active, stats.total)),
          })}
          icon={CheckCircle2}
          tone="success"
          loading={loading}
        />
        <StatCard
          title={t('categories.statInactive')}
          value={stats.inactive}
          subtitle={t('categories.statInactiveHint')}
          icon={CircleSlash}
          tone="neutral"
          loading={loading}
        />
        <StatCard
          title={t('categories.statEmpty')}
          value={stats.empty ?? '—'}
          subtitle={t('categories.statEmptyHint')}
          icon={PackageSearch}
          tone="warning"
          loading={loading}
        />
      </div>

      {/* ---- Recherche et filtre d'état ---- */}
      <div className="card">
        <div className="flex flex-col lg:flex-row lg:items-center gap-4">
          <SearchBox
            className="flex-1"
            value={searchTerm}
            onChange={setSearchTerm}
            placeholder={t('categories.searchPlaceholder')}
            suggestions={categorySuggestions}
            getKey={(c) => c.id}
            onSelectSuggestion={(c) => setSearchTerm(c.name)}
            renderSuggestion={(c) => (
              <span className="flex items-center justify-between gap-2">
                <span className="flex flex-col min-w-0">
                  <span className="font-medium truncate">{c.name}</span>
                  <span className="text-xs text-gray-400 truncate">
                    {c.code || t('categories.noCode')}
                  </span>
                </span>
                <span className="text-xs text-gray-500 shrink-0">
                  {c.active ? t('categories.active') : t('categories.inactive')}
                </span>
              </span>
            )}
          />
          <div className="flex flex-wrap items-center gap-3">
            <SegmentedFilter
              label={t('categories.columnStatus')}
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: 'all', label: t('categories.filterAll'), count: stats.total },
                { value: 'active', label: t('categories.filterActive'), count: stats.active },
                { value: 'inactive', label: t('categories.filterInactive'), count: stats.inactive },
              ]}
            />
            {hasActiveFilters && (
              <Button variant="secondary" size="sm" icon={X} onClick={resetFilters}>
                {t('categories.resetFilters')}
              </Button>
            )}
          </div>
        </div>
      </div>

      {/* ---- Liste ---- */}
      <div className="card overflow-hidden p-0">
        <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div>
            <h2 className="section-title">{t('categories.listTitle')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
              {t('categories.listHint')}
            </p>
          </div>
        </div>

        <Table
          columns={columns}
          data={displayedCategories}
          loading={loading}
          emptyState={emptyState}
          sortKey={sortConfig.key}
          sortDirection={sortConfig.direction}
          onSort={handleSort}
          actions={
            isAdmin
              ? (category) => (
                  <>
                    <button
                      onClick={() => handleEdit(category)}
                      className="text-primary-600 hover:text-primary-900 dark:hover:text-primary-300 p-2 hover:bg-primary-50 dark:hover:bg-primary-500/10 rounded-lg transition-colors"
                      title={t('common.edit')}
                      aria-label={`${t('common.edit')} — ${category.name}`}
                    >
                      <Edit className="w-4 h-4" aria-hidden="true" />
                    </button>
                    <button
                      onClick={() => handleToggleStatus(category)}
                      className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                      title={category.active ? t('categories.deactivate') : t('categories.activate')}
                      aria-label={`${
                        category.active ? t('categories.deactivate') : t('categories.activate')
                      } — ${category.name}`}
                    >
                      {category.active ? (
                        <ToggleRight className="w-4 h-4" aria-hidden="true" />
                      ) : (
                        <ToggleLeft className="w-4 h-4" aria-hidden="true" />
                      )}
                    </button>
                    <button
                      onClick={() => requestDelete(category)}
                      className="text-red-600 hover:text-red-900 dark:hover:text-red-300 p-2 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors"
                      title={t('common.delete')}
                      aria-label={`${t('common.delete')} — ${category.name}`}
                    >
                      <Trash2 className="w-4 h-4" aria-hidden="true" />
                    </button>
                  </>
                )
              : null
          }
        />

        {!loading && sortedCategories.length > 0 && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalItems={sortedCategories.length}
            itemsPerPage={itemsPerPage}
            onPageChange={setCurrentPage}
            onItemsPerPageChange={setItemsPerPage}
          />
        )}
      </div>

      {/* ---- Formulaire ---- */}
      <Modal
        isOpen={showModal}
        onClose={closeModal}
        title={editingCategory ? t('categories.editTitle') : t('categories.newTitle')}
        size="sm"
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <FormInput
            label={t('categories.nameLabel')}
            name="name"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder={t('categories.namePlaceholder')}
            error={formError}
            required
          />

          <div>
            <FormInput
              label={t('categories.codeLabel')}
              name="code"
              value={formData.code}
              onChange={(e) => setFormData({ ...formData, code: e.target.value })}
              placeholder={t('categories.codePlaceholder')}
            />
            <p className="mt-1.5 text-xs text-gray-500 dark:text-gray-400">
              {t('categories.codeHint')}
            </p>
          </div>

          <FormInput
            label={t('common.description')}
            name="description"
            type="textarea"
            rows={3}
            value={formData.description}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            placeholder={t('categories.descriptionPlaceholder')}
          />

          <label className="flex items-start gap-3 p-4 rounded-xl border border-gray-200 dark:border-gray-700 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/40 transition-colors">
            <input
              type="checkbox"
              checked={formData.active}
              onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
              className="mt-0.5 w-4 h-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
            />
            <span className="min-w-0">
              <span className="block text-sm font-medium text-gray-900 dark:text-gray-100">
                {t('categories.activeLabel')}
              </span>
              <span className="block text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                {t('categories.activeHint')}
              </span>
            </span>
          </label>

          <div className="flex justify-end gap-3 pt-2">
            <Button variant="secondary" onClick={closeModal}>
              {t('common.cancel')}
            </Button>
            <Button variant="primary" type="submit">
              {editingCategory ? t('common.saveChanges') : t('common.create')}
            </Button>
          </div>
        </form>
      </Modal>

      {/* ---- Confirmation d'enregistrement ---- */}
      <ConfirmModal
        isOpen={showConfirmModal}
        onClose={() => setShowConfirmModal(false)}
        onConfirm={confirmSubmit}
        title={editingCategory ? t('categories.confirmEdit') : t('categories.confirmCreate')}
        message={
          editingCategory
            ? t('categories.confirmEditMessage', { name: formData.name })
            : t('categories.confirmCreateMessage', { name: formData.name })
        }
        type="info"
      />

      {/* ---- Confirmation de suppression ---- */}
      <ConfirmModal
        isOpen={Boolean(categoryToDelete)}
        onClose={() => setCategoryToDelete(null)}
        onConfirm={confirmDelete}
        title={t('categories.deleteTitle')}
        message={t('categories.deleteMessage', { name: categoryToDelete?.name })}
        type="danger"
        confirmLabel={t('common.delete')}
      />
    </div>
  );
};

export default Categories;
