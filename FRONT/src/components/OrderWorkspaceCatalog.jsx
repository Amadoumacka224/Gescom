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

/** Rupture et stock faible : mêmes seuils que les pastilles portées par les vignettes. */
const isOutOfStock = (product) => product.stockQuantity <= 0;
const isLowStock = (product) =>
  !isOutOfStock(product) && product.stockQuantity < (product.minStockAlert || 0);

/**
 * Filtres de disponibilité — la question qu'on se pose le plus souvent devant le catalogue,
 * avant même la famille d'article : « qu'est-ce qui manque ? ».
 *
 * `labelKey` plutôt que le libellé : la traduction est résolue au rendu, sinon les intitulés
 * resteraient figés dans la langue active au premier chargement.
 */
const STOCK_FILTERS = [
  { id: 'ALL', icon: Boxes, labelKey: 'orders.catalog.filterAll', match: () => true },
  { id: 'LOW', icon: AlertTriangle, labelKey: 'orders.catalog.filterLow', match: isLowStock },
  { id: 'OUT', icon: PackageX, labelKey: 'orders.catalog.filterOut', match: isOutOfStock },
];

/** Intitulé de section du panneau : discret, il sépare sans occuper de hauteur utile. */
const SectionLabel = ({ icon: Icon, children }) => (
  <p className="flex items-center gap-1.5 px-2 pb-1 text-[11px] font-bold uppercase tracking-wide text-gray-400 dark:text-gray-500">
    {Icon && <Icon className="w-3.5 h-3.5" aria-hidden="true" />}
    {children}
  </p>
);

/**
 * Ligne du panneau latéral : intitulé à gauche, effectif à droite.
 *
 * L'effectif est ce qui distingue un panneau de filtres d'une simple liste de liens — il donne
 * la composition du catalogue sans avoir à cliquer, et évite d'ouvrir une famille vide.
 *
 * La sélection se marque par un filet à gauche et un fond teinté, pas par un aplat plein :
 * une colonne d'aplats colorés tire l'œil hors de la grille, qui est le contenu utile.
 * `aria-pressed` porte l'état, la couleur ne le dit pas seule.
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

/** Même filtre, en pastille : sous `md`, le panneau latéral n'a pas la place de tenir. */
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
 * Sélecteur d'articles du panier de traitement : disponibilité, catégories, recherche, scan
 * et grille produits.
 *
 * Le composant ne connaît ni la commande ni son statut : il remonte simplement le produit
 * choisi. C'est l'atelier (`OrderWorkspace`) qui décide s'il est encore affichable — les
 * lignes d'une commande confirmée sont figées, la grille cède alors la place au dossier.
 *
 * Répartition des rôles à l'écran, celle des catalogues de gestion commerciale :
 *
 *   - le panneau latéral **restreint** la liste (disponibilité, famille) et affiche ce que
 *     chaque critère laisse voir ;
 *   - la barre de recherche **atteint** un article précis, à la frappe ou au scan ;
 *   - la grille reste le contenu, et occupe tout ce que les deux premiers ne prennent pas.
 *
 * Le panneau se réduit à un filet de séparation, sans carte ni fond : sa hiérarchie tient aux
 * intitulés de section et à la sélection, et la grille récupère la place ainsi rendue.
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

  // Toutes les catégories actives, y compris celles encore vides : des produits pourront leur
  // être rattachés plus tard, et une colonne qui se réorganise seule désoriente le caissier.
  // Leur effectif est affiché, un « 0 » se voit sans avoir à ouvrir la famille.
  const activeCategories = useMemo(
    () => categories.filter((c) => c.active !== false).slice().sort((a, b) => a.name.localeCompare(b.name)),
    [categories]
  );

  /* Filtrage en trois passes plutôt qu'en un seul `filter` : chaque passe sert d'assiette aux
     effectifs du critère suivant. Les compteurs de disponibilité portent donc sur la recherche
     en cours, et ceux des catégories sur la disponibilité retenue — un compteur qui ne bouge
     pas quand on restreint la liste ferait ouvrir des familles vides. */
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
   * « Entrée » dans la barre de recherche : c'est par là que passe la douchette, qui tape le
   * code dans le champ actif puis émet un « Entrée ». Un seul champ à l'écran, donc, mais deux
   * usages — d'où le tri ci-dessous.
   *
   * Le code ne part au panier que s'il ressemble à un code-barres : soit il correspond
   * exactement à celui d'un article chargé, soit il n'est fait que de chiffres (six au moins).
   * Sans ce garde-fou, valider une recherche ordinaire (« armo ») déclencherait une résolution
   * de code et un message « code inconnu » sur une saisie parfaitement légitime.
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

  return (
    <div className="flex gap-3 flex-1 min-w-0">
      {/* ───────── Panneau de filtres ───────── */}
      <aside className="hidden md:flex w-44 lg:w-52 shrink-0 flex-col gap-3 border-r border-gray-200 pr-3 dark:border-gray-700">
        {/* Disponibilité d'abord : c'est le filtre le plus utilisé au comptoir, et il tient en
            trois lignes. Les catégories, plus nombreuses, prennent la hauteur restante. */}
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

        {/* Pied de panneau : ce que la sélection laisse voir, et de quoi revenir au catalogue
            entier. Le retour n'apparaît qu'une fois un critère posé — sinon il n'a rien à faire. */}
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

      {/* ───────── Barre de commande et grille ───────── */}
      <main className="flex-1 min-w-0 flex flex-col gap-2.5">
        {/* Une seule barre : elle filtre la grille à la frappe et sert d'entrée au scan. Le champ
            de code-barres séparé faisait double emploi à l'œil — deux barres identiques côte à
            côte — pour une différence qui ne se lisait qu'au placeholder.
            Le champ garde le focus à l'ouverture : la douchette tape dedans sans clic préalable. */}
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
        </form>

        {/* Sous `md`, les mêmes filtres en bandeau défilant : le panneau latéral y prendrait la
            moitié de la largeur, mais s'en passer priverait la tablette du filtrage. */}
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

        <div className="flex-1 overflow-y-auto -mx-1 px-1">
          {filteredProducts.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center text-gray-400 dark:text-gray-500 py-8">
              <Package className="w-10 h-10 mb-2 text-gray-300 dark:text-gray-600" aria-hidden="true" />
              <p className="text-sm">{t('orders.catalog.noMatch')}</p>
              {/* Une liste vide vient presque toujours d'un critère oublié : le retour au
                  catalogue entier est proposé là où le vide se constate. */}
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
            /* Six articles par ligne, plafond assumé : au-delà les vignettes deviennent trop
               petites pour être identifiées de loin. La sixième colonne n'arrive qu'à partir de
               `xl` — en dessous, l'atelier réserve déjà la moitié de la largeur au panier et au
               panneau de filtres, et six vignettes y descendraient sous les 90 px. */
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-2.5">
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
                    {/* 4/3 plutôt que carré. À 200 px de large, la vignette passe de 200 à
                        150 px de haut : c'est elle qui pesait le plus dans la tuile, et elle
                        identifie tout aussi bien l'article. Le gain de hauteur est obtenu ici,
                        pas sur les libellés — un catalogue de caisse se lit de loin. */}
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
                    {/* Pas de code produit sur la tuile : il sert à retrouver un article dans la
                        barre de recherche, qui l'interroge toujours, pas à le reconnaître d'un
                        coup d'œil — on le fait sur la photo et le nom. La ligne gagnée profite
                        au nom. Le nom complet reste dans l'infobulle de la tuile.

                        Le libellé passe par `productShortName`, comme dans le catalogue produits :
                        les parenthèses n'y sont pas affichées. Si deux variantes ne se
                        distinguaient que par là, elles apparaîtraient ici à l'identique — c'est
                        l'infobulle qui les départage.

                        Une seule ligne, coupée par `truncate` : la tuile fait la largeur d'une
                        colonne de la grille, c'est elle qui donne la mesure. Le nom entier est
                        dans l'infobulle de la tuile (`addTooltip` / `outOfStockTooltip`). */}
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
