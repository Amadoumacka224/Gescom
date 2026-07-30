import { useMemo, useState } from 'react';
import { LayoutGrid, Package, Plus, ScanLine, Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { formatCurrency } from '../utils/format';

/**
 * Sélecteur d'articles du panier de traitement : catégories, recherche, scan et grille produits.
 *
 * Repris tel quel de l'ancien modal de création de commande — même agencement façon caisse,
 * mêmes garde-fous de stock — et extrait dans son propre composant pour que le panier
 * (`OrderWorkspaceCart`) puisse rester à l'écran à toutes les étapes du cycle de vie.
 *
 * Le composant ne connaît ni la commande ni son statut : il remonte simplement le produit
 * choisi. C'est l'atelier (`OrderWorkspace`) qui décide s'il est encore affichable — les
 * lignes d'une commande confirmée sont figées, la grille cède alors la place au dossier.
 */
const OrderWorkspaceCatalog = ({
  products,
  categories,
  cartQtyByProduct,
  onAddProduct,
  onScanBarcode,
  onOpenScanner,
  scanning = false,
}) => {
  const { t } = useTranslation();
  const [category, setCategory] = useState('ALL');
  const [search, setSearch] = useState('');
  const [barcode, setBarcode] = useState('');
  const [searchFocused, setSearchFocused] = useState(false);

  // Toutes les catégories actives, y compris celles encore vides : des produits pourront leur
  // être rattachés plus tard, et une colonne qui se réorganise seule désoriente le caissier.
  const activeCategories = useMemo(
    () => categories.filter((c) => c.active !== false).slice().sort((a, b) => a.name.localeCompare(b.name)),
    [categories]
  );

  const filteredProducts = useMemo(() => {
    const term = search.trim().toLowerCase();
    return products.filter((p) => {
      const matchCat = category === 'ALL' || p.category?.id === category;
      const matchSearch = !term
        || `${p.name} ${p.code || ''} ${p.barcode || ''}`.toLowerCase().includes(term);
      return matchCat && matchSearch;
    });
  }, [products, category, search]);

  const submitBarcode = async (e) => {
    e.preventDefault();
    const added = await onScanBarcode(barcode);
    if (added) setBarcode('');
  };

  return (
    <div className="flex gap-4 flex-1 min-w-0">
      {/* ───────── Catégories ───────── */}
      <aside className="hidden md:flex w-44 shrink-0 flex-col bg-gray-50 dark:bg-gray-900/40 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
        <div className="px-3 py-2.5 border-b border-gray-200 dark:border-gray-700 flex items-center gap-2">
          <LayoutGrid className="w-4 h-4 text-primary-600 dark:text-primary-400" aria-hidden="true" />
          <span className="text-xs font-bold uppercase tracking-wide text-gray-600 dark:text-gray-300">
            {t('nav.categories')}
          </span>
        </div>
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          <button
            type="button"
            onClick={() => setCategory('ALL')}
            className={`w-full text-left px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
              category === 'ALL'
                ? 'bg-primary-600 text-white shadow-sm'
                : 'text-gray-600 dark:text-gray-300 hover:bg-gray-200/70 dark:hover:bg-gray-700/60'
            }`}
          >
            {t('orders.catalog.allProducts')}
          </button>
          {activeCategories.map((cat) => (
            <button
              key={cat.id}
              type="button"
              onClick={() => setCategory(cat.id)}
              className={`w-full text-left px-3 py-2 rounded-lg text-sm font-medium truncate transition-colors ${
                category === cat.id
                  ? 'bg-primary-600 text-white shadow-sm'
                  : 'text-gray-600 dark:text-gray-300 hover:bg-gray-200/70 dark:hover:bg-gray-700/60'
              }`}
            >
              {cat.name}
            </button>
          ))}
          {activeCategories.length === 0 && (
            <p className="px-3 py-2 text-xs text-gray-400 dark:text-gray-500">{t('orders.catalog.noCategory')}</p>
          )}
        </div>
      </aside>

      {/* ───────── Recherche, scan et grille ───────── */}
      <main className="flex-1 min-w-0 flex flex-col gap-3">
        {/* La douchette saisit le code puis émet un « Entrée », capté par le onSubmit ; le bouton
            couvre la saisie manuelle. Le produit résolu part directement au panier. */}
        <form onSubmit={submitBarcode} className="flex items-center gap-2">
          <div className="relative flex-1">
            <ScanLine className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-primary-500" aria-hidden="true" />
            <input
              type="text"
              value={barcode}
              onChange={(e) => setBarcode(e.target.value)}
              placeholder={t('orders.catalog.barcodePlaceholder')}
              autoFocus
              autoComplete="off"
              aria-label={t('products.barcode')}
              className="w-full pl-10 pr-4 py-2.5 bg-primary-50/40 dark:bg-gray-700 border border-primary-200 dark:border-gray-600 rounded-xl text-sm font-medium text-gray-900 dark:text-gray-100 focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-200 transition-all"
            />
          </div>
          <button
            type="submit"
            disabled={scanning || !barcode.trim()}
            className="inline-flex items-center gap-1.5 px-4 py-2.5 bg-primary-600 text-white text-sm font-semibold rounded-xl shadow-sm hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <Plus className="w-4 h-4" aria-hidden="true" />
            {t('common.add')}
          </button>
        </form>

        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" aria-hidden="true" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onFocus={() => setSearchFocused(true)}
            onBlur={() => setSearchFocused(false)}
            placeholder={t('orders.catalog.searchPlaceholder')}
            aria-label={t('orders.catalog.searchLabel')}
            className="w-full pl-10 pr-12 py-2.5 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-xl text-sm font-medium text-gray-900 dark:text-gray-100 focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-200 transition-all"
          />
          {/* onMouseDown empêche le blur : le clic ouvre le scanner même barre active. */}
          <button
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={onOpenScanner}
            title={t('scanner.title')}
            aria-label={t('scanner.title')}
            className={`absolute right-1.5 top-1/2 -translate-y-1/2 inline-flex items-center justify-center w-9 h-9 rounded-lg transition-colors ${
              searchFocused
                ? 'text-primary-600 bg-primary-50 dark:bg-primary-500/15 ring-1 ring-primary-200'
                : 'text-gray-400 hover:text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-500/10'
            }`}
          >
            <ScanLine className="w-5 h-5" aria-hidden="true" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto -mx-1 px-1">
          {filteredProducts.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center text-gray-400 dark:text-gray-500 py-12">
              <Package className="w-10 h-10 mb-2 text-gray-300 dark:text-gray-600" aria-hidden="true" />
              <p className="text-sm">{t('orders.catalog.noMatch')}</p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
              {filteredProducts.map((product) => {
                const inCart = cartQtyByProduct[String(product.id)] || 0;
                const outOfStock = product.stockQuantity <= 0;
                const lowStock = !outOfStock && product.stockQuantity < (product.minStockAlert || 0);
                return (
                  <button
                    key={product.id}
                    type="button"
                    onClick={() => onAddProduct(product)}
                    disabled={outOfStock}
                    title={outOfStock
                      ? t('orders.catalog.outOfStockTooltip', { name: product.name })
                      : t('orders.catalog.addTooltip', { name: product.name })}
                    className={`group relative text-left bg-white dark:bg-gray-800 rounded-xl border overflow-hidden flex flex-col transition-all ${
                      outOfStock
                        ? 'border-gray-200 dark:border-gray-700 opacity-60 cursor-not-allowed'
                        : 'border-gray-200 dark:border-gray-700 hover:border-primary-400 hover:shadow-md active:scale-[0.98]'
                    } ${inCart > 0 ? 'ring-2 ring-primary-500 border-transparent' : ''}`}
                  >
                    <div className="aspect-square bg-gray-100 dark:bg-gray-700 relative flex items-center justify-center overflow-hidden">
                      {product.imageUrl ? (
                        <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover" />
                      ) : (
                        <Package className="w-8 h-8 text-gray-300 dark:text-gray-500" aria-hidden="true" />
                      )}
                      {inCart > 0 && (
                        <span className="absolute top-1.5 right-1.5 min-w-[22px] h-[22px] px-1 inline-flex items-center justify-center bg-primary-600 text-white text-xs font-bold rounded-full shadow">
                          {inCart}
                        </span>
                      )}
                      {outOfStock ? (
                        <span className="absolute bottom-1.5 left-1.5 px-1.5 py-0.5 bg-red-600 text-white text-[10px] font-bold rounded">
                          {t('products.outOfStockShort')}
                        </span>
                      ) : lowStock ? (
                        <span className="absolute bottom-1.5 left-1.5 px-1.5 py-0.5 bg-amber-500 text-white text-[10px] font-bold rounded">
                          {t('products.lowStockShort')}
                        </span>
                      ) : null}
                    </div>
                    <div className="p-2.5 flex flex-col flex-1 gap-0.5">
                      <p className="text-sm font-semibold text-gray-900 dark:text-gray-100 leading-tight line-clamp-2">
                        {product.name}
                      </p>
                      <p className="text-[11px] text-gray-400 dark:text-gray-500 truncate">{product.code}</p>
                      <div className="mt-auto pt-1.5 flex items-end justify-between gap-1">
                        <span className="text-sm font-bold text-primary-600 dark:text-primary-400 tabular-nums">
                          {formatCurrency(product.sellingPrice)}
                        </span>
                        <span className="text-[11px] text-gray-400 dark:text-gray-500 shrink-0 tabular-nums">
                          {product.stockQuantity} {product.unit}
                        </span>
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </main>
    </div>
  );
};

export default OrderWorkspaceCatalog;
