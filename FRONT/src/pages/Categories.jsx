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
  Eye,
  CalendarClock,
} from 'lucide-react';
import api from '../services/api';
import { useAuth } from '../context/useAuth';
import Modal from '../components/Modal';
import ConfirmModal from '../components/ConfirmModal';
import Table from '../components/Table';
import Pagination from '../components/Pagination';
import SearchBox from '../components/SearchBox';
import SegmentedFilter from '../components/SegmentedFilter';
import StatCard from '../components/StatCard';
import FormInput from '../components/FormInput';
import Button from '../components/Button';
import KeyFact from '../components/KeyFact';
import { rankSuggestions } from '../utils/searchSuggestions';
import { formatCurrency, formatDate, formatPercent, safeRatio } from '../utils/format';

const EMPTY_FORM = { name: '', code: '', description: '', active: true };

/** Produits détaillés dans la fiche ; au-delà, un simple décompte du reste. */
const DETAIL_PRODUCTS_SHOWN = 8;

const Categories = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  // Le backend restreint create/update/delete/toggle-status aux ADMIN
  // (cf. CategoryController @PreAuthorize("hasRole('ADMIN')")). On masque les
  // actions correspondantes côté UI pour éviter les 403 au CAISSIER.
  const isAdmin = user?.role === 'ADMIN';

  const [categories, setCategories] = useState([]);
  // Produits groupés par catégorie. `CategoryResponse` ne porte ni le total ni la liste :
  // c'est ce regroupement qui dit si une catégorie est vide (donc supprimable) et qui
  // alimente les produits rattachés de la fiche, sans requête supplémentaire à l'ouverture.
  const [productsByCategory, setProductsByCategory] = useState(null);
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

  // La fiche retient un identifiant, pas l'objet : après une activation ou une modification,
  // elle se relit depuis la liste rafraîchie au lieu d'afficher un état périmé. Et si la
  // catégorie disparaît (suppression), la fiche se referme d'elle-même.
  const [selectedCategoryId, setSelectedCategoryId] = useState(null);

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
      const grouped = {};
      for (const product of productsResult.value.data || []) {
        const categoryId = product.category?.id;
        if (!categoryId) continue;
        (grouped[categoryId] ||= []).push(product);
      }
      // Tri une fois pour toutes : la fiche affiche les produits par ordre alphabétique.
      for (const list of Object.values(grouped)) {
        list.sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'fr', { sensitivity: 'base' }));
      }
      setProductsByCategory(grouped);
    } else {
      console.error('Error fetching products:', productsResult.reason);
      setProductsByCategory(null);
    }

    setLoading(false);
  };

  const productsFor = (category) => productsByCategory?.[category?.id] ?? [];
  const countFor = (category) => productsFor(category).length;

  const selectedCategory = useMemo(
    () => categories.find((c) => c.id === selectedCategoryId) || null,
    [categories, selectedCategoryId]
  );

  /** Ce que la catégorie pèse au catalogue : c'est là-dessus qu'on décide de la compléter,
   *  de la désactiver ou de la supprimer. */
  const selectedStats = useMemo(() => {
    const products = productsFor(selectedCategory);
    return {
      products,
      total: products.length,
      active: products.filter((p) => p.active).length,
      lowStock: products.filter(
        (p) => Number(p.stockQuantity || 0) <= Number(p.minStockAlert || 0)
      ).length,
      stockValue: products.reduce(
        (sum, p) => sum + Number(p.sellingPrice || 0) * Number(p.stockQuantity || 0),
        0
      ),
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCategory, productsByCategory]);

  const stats = useMemo(() => {
    const total = categories.length;
    const active = categories.filter((c) => c.active).length;
    return {
      total,
      active,
      inactive: total - active,
      empty: productsByCategory
        ? categories.filter((c) => !productsByCategory[c.id]?.length).length
        : null,
    };
  }, [categories, productsByCategory]);

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
  }, [filteredCategories, sortConfig, productsByCategory]);

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
        if (!productsByCategory) return <span className="text-gray-400 dark:text-gray-500">—</span>;
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
          onRowClick={(category) => setSelectedCategoryId(category.id)}
          actions={
            /* La fiche est ouverte à tous les rôles : le caissier n'avait jusqu'ici aucun
               moyen de consulter une catégorie, la colonne d'actions lui étant masquée. */
            (category) => (
              <>
                <button
                  onClick={(e) => { e.stopPropagation(); setSelectedCategoryId(category.id); }}
                  className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                  title={t('categories.viewDetail')}
                  aria-label={`${t('categories.viewDetail')} — ${category.name}`}
                >
                  <Eye className="w-4 h-4" aria-hidden="true" />
                </button>
                {isAdmin && (
                  <>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleEdit(category); }}
                      className="text-primary-600 hover:text-primary-900 dark:hover:text-primary-300 p-2 hover:bg-primary-50 dark:hover:bg-primary-500/10 rounded-lg transition-colors"
                      title={t('common.edit')}
                      aria-label={`${t('common.edit')} — ${category.name}`}
                    >
                      <Edit className="w-4 h-4" aria-hidden="true" />
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleToggleStatus(category); }}
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
                      onClick={(e) => { e.stopPropagation(); requestDelete(category); }}
                      className="text-red-600 hover:text-red-900 dark:hover:text-red-300 p-2 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors"
                      title={t('common.delete')}
                      aria-label={`${t('common.delete')} — ${category.name}`}
                    >
                      <Trash2 className="w-4 h-4" aria-hidden="true" />
                    </button>
                  </>
                )}
              </>
            )
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

      {/* ---- Fiche catégorie (lecture) ---- */}
      <Modal
        isOpen={!!selectedCategory}
        onClose={() => setSelectedCategoryId(null)}
        title={t('categories.detailsTitle')}
        size="lg"
      >
        {selectedCategory && (
          <div className="space-y-6">
            {/* En-tête : ce que la catégorie est, et ce qu'elle pèse au catalogue. */}
            <header className="-mx-6 -mt-6 border-b border-gray-200 bg-gray-50 px-6 py-6 dark:border-gray-700 dark:bg-gray-900/40">
              <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between lg:gap-8">
                <div className="flex min-w-0 items-start gap-4">
                  <span className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-xl bg-primary-100 text-primary-700 dark:bg-primary-500/20 dark:text-primary-200">
                    <Tags className="h-6 w-6" aria-hidden="true" />
                  </span>
                  <div className="min-w-0">
                    <h3 className="truncate text-xl font-bold tracking-tight text-gray-900 dark:text-gray-100">
                      {selectedCategory.name}
                    </h3>
                    <p className="mt-0.5 font-mono text-sm text-gray-500 dark:text-gray-400">
                      {selectedCategory.code || t('categories.noCode')}
                    </p>
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                      <span className={selectedCategory.active ? 'badge-success' : 'badge-neutral'}>
                        {selectedCategory.active ? t('categories.active') : t('categories.inactive')}
                      </span>
                      {productsByCategory && selectedStats.total === 0 && (
                        <span className="badge-warning">{t('categories.emptyBadge')}</span>
                      )}
                    </div>
                  </div>
                </div>

                <div className="lg:flex-shrink-0 lg:text-right">
                  <p className="text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">
                    {t('categories.attachedProducts')}
                  </p>
                  <p className="mt-1 text-2xl font-bold tabular-nums text-gray-900 dark:text-gray-100">
                    {productsByCategory ? selectedStats.total : '—'}
                  </p>
                </div>
              </div>
            </header>

            {/* Repères : les chiffres qui décident de compléter, désactiver ou supprimer. */}
            <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <KeyFact
                icon={CheckCircle2}
                label={t('categories.statActiveProducts')}
                value={productsByCategory
                  ? `${selectedStats.active} / ${selectedStats.total}`
                  : null}
              />
              <KeyFact
                icon={PackageSearch}
                label={t('categories.statLowStock')}
                value={productsByCategory ? String(selectedStats.lowStock) : null}
                hint={t('categories.statLowStockHint')}
              />
              <KeyFact
                icon={Tags}
                label={t('products.stockValueLabel')}
                value={productsByCategory ? formatCurrency(selectedStats.stockValue) : null}
                hint={t('categories.stockValueHint')}
              />
              <KeyFact
                icon={CalendarClock}
                label={t('categories.columnUpdated')}
                value={formatDate(selectedCategory.updatedAt || selectedCategory.createdAt)}
              />
            </dl>

            <section className="space-y-3">
              <h4 className="subsection-title">{t('common.description')}</h4>
              {selectedCategory.description ? (
                <p className="rounded-xl border border-gray-200 px-4 py-3 text-sm leading-relaxed text-gray-700 dark:border-gray-700 dark:text-gray-300">
                  {selectedCategory.description}
                </p>
              ) : (
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-dashed border-gray-300 px-4 py-3 dark:border-gray-600">
                  <p className="text-sm text-gray-500 dark:text-gray-400">{t('categories.noDescription')}</p>
                  {isAdmin && (
                    <Button
                      variant="secondary"
                      size="sm"
                      icon={Edit}
                      onClick={() => { setSelectedCategoryId(null); handleEdit(selectedCategory); }}
                    >
                      {t('categories.completeDescription')}
                    </Button>
                  )}
                </div>
              )}
            </section>

            {/* Éléments associés : les produits rattachés. Ils sont déjà chargés par la page
                (le décompte de la liste en dépend), la fiche n'appelle donc pas l'API. */}
            <section className="space-y-3">
              <h4 className="subsection-title flex items-center gap-2">
                <PackageSearch className="h-4 w-4 text-gray-400" aria-hidden="true" />
                {t('categories.sectionProducts')}
                {productsByCategory && selectedStats.total > 0 && (
                  <span className="font-normal text-gray-400 dark:text-gray-500">
                    · {selectedStats.total}
                  </span>
                )}
              </h4>

              {!productsByCategory ? (
                <div className="rounded-xl border border-dashed border-gray-300 px-4 py-3 dark:border-gray-600">
                  <p className="text-sm text-gray-500 dark:text-gray-400">
                    {t('categories.productsUnavailable')}
                  </p>
                </div>
              ) : selectedStats.total === 0 ? (
                <div className="rounded-xl border border-dashed border-gray-300 px-4 py-3 dark:border-gray-600">
                  <p className="text-sm text-gray-500 dark:text-gray-400">{t('categories.noProducts')}</p>
                  <p className="mt-1 text-xs text-gray-400 dark:text-gray-500">
                    {t('categories.noProductsHint')}
                  </p>
                </div>
              ) : (
                <>
                  <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
                    <table className="w-full">
                      <thead className="bg-gray-50 dark:bg-gray-900/40">
                        <tr>
                          <th scope="col" className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{t('common.product')}</th>
                          <th scope="col" className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{t('products.sellingPrice')}</th>
                          <th scope="col" className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{t('products.stock')}</th>
                          <th scope="col" className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">{t('products.columnStatus')}</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 dark:divide-gray-700/60">
                        {selectedStats.products.slice(0, DETAIL_PRODUCTS_SHOWN).map((product) => {
                          const stock = Number(product.stockQuantity || 0);
                          const lowStock = stock <= Number(product.minStockAlert || 0);
                          return (
                            <tr key={product.id} className="text-sm text-gray-700 dark:text-gray-300">
                              <td className="px-4 py-3">
                                <div className="font-medium text-gray-900 dark:text-gray-100">{product.name}</div>
                                <div className="font-mono text-xs text-gray-500 dark:text-gray-400">
                                  {product.code || '—'}
                                </div>
                              </td>
                              <td className="px-4 py-3 text-right tabular-nums">
                                {formatCurrency(product.sellingPrice)}
                              </td>
                              <td className="px-4 py-3 text-right">
                                <span className={`tabular-nums ${lowStock ? 'font-semibold text-amber-600 dark:text-amber-400' : ''}`}>
                                  {stock} {product.unit || ''}
                                </span>
                              </td>
                              <td className="px-4 py-3 text-right">
                                <span className={product.active ? 'badge-success' : 'badge-neutral'}>
                                  {product.active ? t('categories.active') : t('categories.inactive')}
                                </span>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                  {selectedStats.total > DETAIL_PRODUCTS_SHOWN && (
                    <p className="text-xs text-gray-500 dark:text-gray-400">
                      {t('categories.moreProducts', {
                        count: selectedStats.total - DETAIL_PRODUCTS_SHOWN,
                      })}
                    </p>
                  )}
                </>
              )}
            </section>

            <p className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-gray-200 pt-4 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">
              <span className="inline-flex items-center gap-1.5">
                <CalendarClock className="h-3.5 w-3.5" aria-hidden="true" />
                {t('categories.createdAtLabel')} {formatDate(selectedCategory.createdAt)}
              </span>
              <span>#{selectedCategory.id}</span>
            </p>

            <div className="sticky bottom-0 -mx-6 -mb-6 flex flex-wrap items-center gap-3 border-t border-gray-200 bg-white/95 px-6 py-4 backdrop-blur dark:border-gray-700 dark:bg-gray-800/95">
              {isAdmin && (
                <>
                  {/* Une catégorie encore rattachée à des produits ne peut pas être supprimée
                      (la clé étrangère `product.category_id` n'a ni cascade ni mise à null) :
                      le bouton reste visible mais inerte, avec la raison écrite à côté. */}
                  <Button
                    variant="danger"
                    icon={Trash2}
                    disabled={selectedStats.total > 0}
                    onClick={() => requestDelete(selectedCategory)}
                  >
                    {t('common.delete')}
                  </Button>
                  {selectedStats.total > 0 && (
                    <span className="text-xs text-gray-400 dark:text-gray-500">
                      {t('categories.deleteBlockedShort')}
                    </span>
                  )}
                </>
              )}
              <div className="ml-auto flex flex-wrap items-center justify-end gap-3">
                <Button variant="secondary" onClick={() => setSelectedCategoryId(null)}>
                  {t('common.close')}
                </Button>
                {isAdmin && (
                  <>
                    <Button
                      variant="secondary"
                      icon={selectedCategory.active ? ToggleLeft : ToggleRight}
                      onClick={() => handleToggleStatus(selectedCategory)}
                    >
                      {selectedCategory.active ? t('categories.deactivate') : t('categories.activate')}
                    </Button>
                    <Button
                      variant="primary"
                      icon={Edit}
                      onClick={() => { setSelectedCategoryId(null); handleEdit(selectedCategory); }}
                    >
                      {t('common.edit')}
                    </Button>
                  </>
                )}
              </div>
            </div>
          </div>
        )}
      </Modal>

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
