import { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Boxes,
  Package,
  PackageX,
  RotateCcw,
  ScanLine,
  Search,
  SlidersHorizontal,
  Tags,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { formatCurrency, productShortName } from '../utils/format';

/** Rupture et stock faible : mêmes seuils que les pastilles des vignettes. */
const isOutOfStock = (product) => product.stockQuantity <= 0;
const isLowStock = (product) =>
  !isOutOfStock(product) && product.stockQuantity < (product.minStockAlert || 0);

/**
 * Filtres de disponibilité : c'est souvent la première question devant le catalogue
 * (« qu'est-ce qui manque ? »).
 *
 * On stocke `labelKey` et pas le texte, pour que la traduction se fasse au rendu (sinon le
 * texte resterait dans la langue du premier chargement).
 */
const STOCK_FILTERS = [
  { id: 'ALL', icon: Boxes, labelKey: 'orders.catalog.filterAll', match: () => true },
  { id: 'LOW', icon: AlertTriangle, labelKey: 'orders.catalog.filterLow', match: isLowStock },
  { id: 'OUT', icon: PackageX, labelKey: 'orders.catalog.filterOut', match: isOutOfStock },
];

/** Titre de section du panneau, discret pour ne pas prendre de place. */
const SectionLabel = ({ icon: Icon, children }) => (
  <p className="flex items-center gap-1.5 px-2 pb-1 text-[11px] font-bold uppercase tracking-wide text-gray-400 dark:text-gray-500">
    {Icon && <Icon className="w-3.5 h-3.5" aria-hidden="true" />}
    {children}
  </p>
);

/**
 * Ligne du panneau de gauche : libellé à gauche, nombre d'articles à droite.
 *
 * Le nombre permet de voir la composition du catalogue sans cliquer, et d'éviter d'ouvrir
 * une catégorie vide.
 *
 * La sélection est marquée par une barre à gauche et un fond léger, pas un fond plein (trop
 * voyant à côté de la grille). `aria-pressed` donne l'état, pas seulement la couleur.
 */
const FilterRow = ({ icon: Icon, label, count, selected, onClick }) => (
  <button
    type="button"
    onClick={onClick}
    aria-pressed={selected}
    title={label}
    className={`flex w-full items-center gap-2 rounded-md border-l-2 px-2 py-1.5 text-left text-sm transition-colors ${
      selected
        ? 'border-primary-600 bg-primary-50 font-semibold text-primary-700 dark:bg-primary-500/15 dark:text-primary-300'
        : 'border-transparent text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700/60'
    }`}
  >
    {Icon && <Icon className="w-4 h-4 shrink-0" aria-hidden="true" />}
    <span className="min-w-0 flex-1 truncate">{label}</span>
    <span
      className={`shrink-0 text-xs tabular-nums ${
        selected ? 'text-primary-600 dark:text-primary-300' : 'text-gray-400 dark:text-gray-500'
      }`}
    >
      {count}
    </span>
  </button>
);

/** Même filtre en pastille, pour les écrans sous `md` où le panneau ne rentre pas. */
const FilterChip = ({ label, count, selected, onClick }) => (
  <button
    type="button"
    onClick={onClick}
    aria-pressed={selected}
    className={`inline-flex shrink-0 items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
      selected
        ? 'border-primary-600 bg-primary-600 text-white'
        : 'border-gray-300 text-gray-600 dark:border-gray-600 dark:text-gray-300'
    }`}
  >
    {label}
    <span className={`tabular-nums ${selected ? 'text-white/75' : 'text-gray-400 dark:text-gray-500'}`}>
      {count}
    </span>
  </button>
);

/**
 * Sélection des articles dans le panier : disponibilité, catégories, recherche, scan et
 * grille des produits.
 *
 * Le composant ne connaît pas la commande ni son statut, il renvoie juste le produit choisi.
 * C'est `OrderWorkspace` qui décide de l'afficher ou non (une fois la commande confirmée,
 * la grille est remplacée par le récapitulatif).
 *
 * Organisation de l'écran :
 *
 *   - le panneau de gauche **filtre** la liste (disponibilité, catégorie) et affiche le
 *     nombre d'articles pour chaque filtre ;
 *   - la barre de recherche sert à **trouver** un article précis, en tapant ou en scannant ;
 *   - la grille prend toute la place restante.
 *
 * Le panneau n'a ni carte ni fond, juste un trait de séparation, pour laisser plus de place
 * à la grille.
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
  const [stockFilter, setStockFilter] = useState('ALL');
  const [search, setSearch] = useState('');
  const [searchFocused, setSearchFocused] = useState(false);

  // Toutes les catégories actives, même vides : on pourra y ajouter des produits plus tard,
  // et une liste qui change toute seule perturbe le caissier. Le nombre est affiché, donc
  // on voit le « 0 » sans ouvrir la catégorie.
  const activeCategories = useMemo(
    () => categories.filter((c) => c.active !== false).slice().sort((a, b) => a.name.localeCompare(b.name)),
    [categories]
  );

  /* Filtrage en trois étapes au lieu d'un seul `filter` : chaque étape sert à calculer les
     compteurs du filtre suivant. Les compteurs de disponibilité tiennent compte de la
     recherche, et ceux des catégories de la disponibilité choisie. Sinon on afficherait des
     nombres faux et on ouvrirait des catégories vides. */
  const searchMatched = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return products;
    return products.filter((p) =>
      `${p.name} ${p.code || ''} ${p.barcode || ''}`.toLowerCase().includes(term)
    );
  }, [products, search]);

  const stockCounts = useMemo(
    () => ({
      ALL: searchMatched.length,
      LOW: searchMatched.filter(isLowStock).length,
      OUT: searchMatched.filter(isOutOfStock).length,
    }),
    [searchMatched]
  );

  const availabilityMatched = useMemo(() => {
    const rule = STOCK_FILTERS.find((f) => f.id === stockFilter)?.match;
    return rule ? searchMatched.filter(rule) : searchMatched;
  }, [searchMatched, stockFilter]);

  const countByCategory = useMemo(() => {
    const counts = new Map();
    availabilityMatched.forEach((p) => {
      const id = p.category?.id;
      if (id != null) counts.set(id, (counts.get(id) || 0) + 1);
    });
    return counts;
  }, [availabilityMatched]);

  const filteredProducts = useMemo(
    () => (category === 'ALL'
      ? availabilityMatched
      : availabilityMatched.filter((p) => p.category?.id === category)),
    [availabilityMatched, category]
  );

  const filtersActive = category !== 'ALL' || stockFilter !== 'ALL' || search.trim() !== '';

  const resetFilters = () => {
    setCategory('ALL');
    setStockFilter('ALL');
    setSearch('');
  };

  /**
   * Touche Entrée dans la recherche. La douchette tape le code dans le champ actif puis
   * envoie Entrée, donc ce champ sert à la fois pour la recherche et pour le scan.
   *
   * On n'envoie le code au panier que s'il ressemble à un code-barres : soit il correspond
   * exactement à un article chargé, soit il ne contient que des chiffres (au moins six).
   * Sinon, une recherche normale (« armo ») afficherait « code inconnu » pour rien.
   */
  const submitSearch = async (e) => {
    e.preventDefault();
    const term = search.trim();
    if (!term || scanning) return;
    const looksLikeBarcode = /^\d{6,}$/.test(term)
      || products.some((p) => (p.barcode || '').trim() === term);
    if (!looksLikeBarcode) return;
    const added = await onScanBarcode(term);
    if (added) setSearch('');
  };

  // Sous `lg`, le catalogue est limité à 55 vh et la grille scrolle à l'intérieur. Sans
  // limite, avec 180 articles sur deux colonnes, le panier et le bouton de validation se
  // retrouvaient tout en bas de la page. On utilise une hauteur relative à la fenêtre (pas
  // une valeur fixe comme les 24 rem testés avant) pour que le haut du panier reste visible.
  // Le minimum sert pour les fenêtres très basses. Au-dessus de `lg`, le catalogue reprend
  // la hauteur de la colonne.
  return (
    <div className="flex gap-3 min-w-0 shrink-0 h-[55vh] min-h-[18rem] lg:h-auto lg:min-h-0 lg:flex-1 lg:shrink">
      {/* ───────── Panneau de filtres ───────── */}
      <aside className="hidden md:flex w-44 lg:w-52 shrink-0 flex-col gap-3 border-r border-gray-200 pr-3 dark:border-gray-700">
        {/* Disponibilité en premier : c'est le filtre le plus utilisé et il fait trois lignes.
            Les catégories prennent le reste de la hauteur. */}
        <div>
          <SectionLabel icon={SlidersHorizontal}>{t('orders.catalog.availabilityTitle')}</SectionLabel>
          <div className="space-y-0.5">
            {STOCK_FILTERS.map((filter) => (
              <FilterRow
                key={filter.id}
                icon={filter.icon}
                label={t(filter.labelKey)}
                count={stockCounts[filter.id]}
                selected={stockFilter === filter.id}
                onClick={() => setStockFilter(filter.id)}
              />
            ))}
          </div>
        </div>

        <div className="flex min-h-0 flex-1 flex-col">
          <SectionLabel icon={Tags}>{t('nav.categories')}</SectionLabel>
          <div className="min-h-0 flex-1 space-y-0.5 overflow-y-auto">
            <FilterRow
              label={t('orders.catalog.allProducts')}
              count={availabilityMatched.length}
              selected={category === 'ALL'}
              onClick={() => setCategory('ALL')}
            />
            {activeCategories.map((cat) => (
              <FilterRow
                key={cat.id}
                label={cat.name}
                count={countByCategory.get(cat.id) || 0}
                selected={category === cat.id}
                onClick={() => setCategory(cat.id)}
              />
            ))}
            {activeCategories.length === 0 && (
              <p className="px-2 py-1.5 text-xs text-gray-400 dark:text-gray-500">
                {t('orders.catalog.noCategory')}
              </p>
            )}
          </div>
        </div>

        {/* Bas du panneau : nombre d'articles affichés et bouton pour tout réafficher
            (seulement si un filtre est actif). */}
        <div className="border-t border-gray-200 pt-2 dark:border-gray-700">
          <p className="px-2 text-xs tabular-nums text-gray-500 dark:text-gray-400">
            {t('orders.catalog.resultCount', { count: filteredProducts.length })}
          </p>
          {filtersActive && (
            <button
              type="button"
              onClick={resetFilters}
              className="mt-1 flex w-full items-center gap-1.5 rounded-md px-2 py-1.5 text-xs font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-500/10"
            >
              <RotateCcw className="w-3.5 h-3.5" aria-hidden="true" />
              {t('orders.catalog.resetFilters')}
            </button>
          )}
        </div>
      </aside>

      {/* ───────── Recherche et grille ───────── */}
      <main className="flex-1 min-w-0 min-h-0 flex flex-col gap-2.5">
        {/* Une seule barre pour la recherche et le scan. Avant il y avait un champ code-barres
            à part, mais on avait deux barres identiques côte à côte.
            Le focus est mis dessus à l'ouverture pour pouvoir scanner directement. */}
        <form onSubmit={submitSearch} className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" aria-hidden="true" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onFocus={() => setSearchFocused(true)}
            onBlur={() => setSearchFocused(false)}
            placeholder={t('orders.catalog.searchPlaceholder')}
            aria-label={t('orders.catalog.searchLabel')}
            autoFocus
            autoComplete="off"
            className="w-full pl-10 pr-12 py-2 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-xl text-sm font-medium text-gray-900 dark:text-gray-100 focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-200 transition-all"
          />
          {/* onMouseDown pour éviter le blur : le clic ouvre le scanner même si la barre a le focus. */}
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
        </form>

        {/* Sous `md`, les mêmes filtres dans une bande qui défile horizontalement : le panneau
            prendrait la moitié de l'écran, mais on garde les filtres sur tablette. */}
        <div className="flex gap-1.5 overflow-x-auto pb-0.5 md:hidden">
          {STOCK_FILTERS.map((filter) => (
            <FilterChip
              key={filter.id}
              label={t(filter.labelKey)}
              count={stockCounts[filter.id]}
              selected={stockFilter === filter.id}
              onClick={() => setStockFilter(filter.id)}
            />
          ))}
          <span className="w-px shrink-0 bg-gray-200 dark:bg-gray-700" aria-hidden="true" />
          <FilterChip
            label={t('orders.catalog.allProducts')}
            count={availabilityMatched.length}
            selected={category === 'ALL'}
            onClick={() => setCategory('ALL')}
          />
          {activeCategories.map((cat) => (
            <FilterChip
              key={cat.id}
              label={cat.name}
              count={countByCategory.get(cat.id) || 0}
              selected={category === cat.id}
              onClick={() => setCategory(cat.id)}
            />
          ))}
        </div>

        {/* La grille a son propre scroll à toutes les tailles, c'est ce qui l'empêche de
            pousser le panier. `min-h-0` est obligatoire, sinon l'élément flex ne descend pas
            sous la hauteur de son contenu. */}
        <div className="flex-1 min-h-0 overflow-y-auto -mx-1 px-1">
          {filteredProducts.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center text-gray-400 dark:text-gray-500 py-8">
              <Package className="w-10 h-10 mb-2 text-gray-300 dark:text-gray-600" aria-hidden="true" />
              <p className="text-sm">{t('orders.catalog.noMatch')}</p>
              {/* Une liste vide vient souvent d'un filtre oublié, donc on propose de tout
                  réafficher ici. */}
              {filtersActive && (
                <button
                  type="button"
                  onClick={resetFilters}
                  className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-600 transition-colors hover:border-primary-400 hover:text-primary-600 dark:border-gray-600 dark:text-gray-300"
                >
                  <RotateCcw className="w-3.5 h-3.5" aria-hidden="true" />
                  {t('orders.catalog.resetFilters')}
                </button>
              )}
            </div>
          ) : (
            /* Maximum cinq articles par ligne et minimum 120 px par vignette. C'est géré par
               `.catalog-grid` en fonction de la largeur de la grille, pas de la fenêtre
               (voir `index.css`). */
            <div className="catalog-grid">
              {filteredProducts.map((product) => {
                const inCart = cartQtyByProduct[String(product.id)] || 0;
                const outOfStock = isOutOfStock(product);
                const lowStock = isLowStock(product);
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
                    {/* Format 4/3 au lieu de carré : à 200 px de large, l'image fait 150 px de
                        haut au lieu de 200. On gagne de la hauteur ici plutôt que sur le texte,
                        qui doit rester lisible de loin. */}
                    <div className="aspect-[4/3] bg-gray-100 dark:bg-gray-700 relative flex items-center justify-center overflow-hidden">
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
                    {/* Pas de code produit sur la vignette : il sert pour la recherche, pas pour
                        reconnaître l'article (on le reconnaît à la photo et au nom). La place
                        gagnée va au nom.

                        Le nom passe par `productShortName`, comme dans le catalogue produits,
                        donc sans les parenthèses. Si deux variantes ne diffèrent que par là,
                        c'est l'infobulle qui permet de les distinguer.

                        Nom sur une seule ligne avec `truncate`. Le nom complet est dans
                        l'infobulle (`addTooltip` / `outOfStockTooltip`). */}
                    <div className="p-2 flex flex-col flex-1 gap-0.5">
                      <p className="text-sm font-semibold text-gray-900 dark:text-gray-100 leading-tight truncate">
                        {productShortName(product.name)}
                      </p>
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
