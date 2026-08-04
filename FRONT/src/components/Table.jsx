import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * Tableau de données de l'application.
 *
 * Options (toutes facultatives, les appels existants restent valides) :
 *   - `column.className` : classes posées à la fois sur l'en-tête et sur la cellule. Sert au
 *     dévoilement progressif des colonnes (`hidden lg:table-cell`) : sur écran étroit, on masque
 *     le secondaire plutôt que de renvoyer l'utilisateur vers un défilement horizontal.
 *   - `column.sortable` + `sortKey` / `sortDirection` / `onSort` : tri piloté par le parent.
 *     L'en-tête porte `aria-sort`, seul moyen pour un lecteur d'écran de connaître l'état du tri.
 *   - `loading` : lignes squelettes plutôt qu'un tableau vide, qui se lit à tort « aucun résultat ».
 *   - `emptyState` : contenu du vide (message + action). À défaut, texte générique.
 *   - `density` : `compact` resserre les cellules pour les catalogues, où l'on compare des
 *     dizaines de lignes d'un coup et où chaque rangée gagnée compte. La valeur par défaut
 *     `comfortable` garde l'espacement des tableaux existants.
 *   - `maxHeight` : hauteur maximale de la zone de défilement, `null` pour la lever.
 *
 * En-tête collant — pourquoi cette hauteur maximale existe :
 * `position: sticky` se règle sur le plus proche ancêtre défilant, et l'en-tête en portait déjà
 * la classe sans jamais coller. Le tableau est en effet enveloppé dans trois boîtes qui, toutes,
 * capturent le collage sans jamais défiler elles-mêmes : `overflow-x-auto` ici, `card
 * overflow-hidden` sur les pages, et `main` (`overflow-auto` dans `MainLayout`) qui grandit avec
 * son contenu — c'est le document qui défile. Rendre le collage dépendant de la fenêtre
 * demanderait de démonter ces trois niveaux, jusqu'à la coquille de l'application.
 *
 * La zone de défilement est donc celle du tableau lui-même : au-delà de `maxHeight`, les lignes
 * défilent sous leurs intitulés, qui restent lus. En dessous — le cas de la plupart des tableaux
 * de l'application — la hauteur reste dictée par le contenu et rien ne change.
 */
const Table = ({
  columns,
  data,
  onRowClick,
  actions,
  loading = false,
  emptyState = null,
  sortKey = null,
  sortDirection = 'asc',
  onSort,
  skeletonRows = 5,
  density = 'comfortable',
  maxHeight = 'max-h-[70vh]',
}) => {
  const { t } = useTranslation();
  const colSpan = columns.length + (actions ? 1 : 0);
  // Utilitaires posés sur `.table-th` / la cellule : la couche `utilities` de Tailwind passe
  // après `components`, l'espacement compact l'emporte donc sur celui de la classe partagée.
  const cellPadding = density === 'compact' ? 'px-4 py-2' : 'px-6 py-4';
  // `whitespace-nowrap` sur les en-têtes du mode compact : un intitulé replié sur deux lignes
  // (« Prix de vente ») creusait la bande d'en-tête et désalignait les libellés entre eux.
  const headPadding = density === 'compact' ? 'px-4 py-2.5 whitespace-nowrap' : '';

  const ariaSort = (column) => {
    if (!column.sortable || sortKey !== column.key) return 'none';
    return sortDirection === 'asc' ? 'ascending' : 'descending';
  };

  const SortIcon = ({ column }) => {
    if (sortKey !== column.key) {
      return <ChevronsUpDown className="w-3.5 h-3.5 opacity-40 group-hover:opacity-100 transition-opacity" aria-hidden="true" />;
    }
    return sortDirection === 'asc'
      ? <ArrowUp className="w-3.5 h-3.5 text-primary-600 dark:text-primary-400" aria-hidden="true" />
      : <ArrowDown className="w-3.5 h-3.5 text-primary-600 dark:text-primary-400" aria-hidden="true" />;
  };

  return (
    <div className={`overflow-auto ${maxHeight || ''}`}>
      <table className="w-full">
        {/* Fond opaque et trait porté par une ombre incrustée : en sombre, `bg-gray-900/40`
            laissait voir les lignes défiler à travers l'en-tête une fois celui-ci collé, et une
            bordure sur un `thead` collant disparaît sous `border-collapse` (défaut de Tailwind). */}
        <thead className="bg-gray-50 dark:bg-gray-900 shadow-[inset_0_-1px_0_0_rgb(229_231_235)] dark:shadow-[inset_0_-1px_0_0_rgb(55_65_81)] sticky top-0 z-10">
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                aria-sort={ariaSort(column)}
                className={`table-th ${headPadding} ${column.className || ''}`}
              >
                {column.sortable && onSort ? (
                  <button
                    type="button"
                    onClick={() => onSort(column.key)}
                    className="group inline-flex items-center gap-1.5 uppercase tracking-wider hover:text-gray-900 dark:hover:text-gray-100 transition-colors rounded"
                  >
                    {column.label}
                    <SortIcon column={column} />
                  </button>
                ) : (
                  column.label
                )}
              </th>
            ))}
            {actions && (
              <th scope="col" className={`table-th-right ${headPadding}`}>
                {t('common.actions')}
              </th>
            )}
          </tr>
        </thead>
        <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-100 dark:divide-gray-700/60">
          {loading ? (
            Array.from({ length: skeletonRows }).map((_, rowIndex) => (
              <tr key={`skeleton-${rowIndex}`}>
                {Array.from({ length: colSpan }).map((__, cellIndex) => (
                  <td key={cellIndex} className={cellPadding}>
                    <div className="skeleton h-4 w-full max-w-[10rem]" />
                  </td>
                ))}
              </tr>
            ))
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={colSpan} className="px-6 py-16 text-center">
                {emptyState || (
                  <span className="text-sm text-gray-400 dark:text-gray-500">{t('common.noData')}</span>
                )}
              </td>
            </tr>
          ) : (
            /* Les lignes n'ont plus d'animation d'entrée.
             *
             * Elle apparaissait en cascade (30 ms par rang) : sur une page de cent produits, la
             * dernière ligne arrivait trois secondes après la première. Et depuis que le tableau
             * défile dans sa propre zone, Chrome n'exécute pas ces animations pour les lignes
             * hors de la partie visible du conteneur — elles restaient bloquées à `opacity: 0`,
             * c'est-à-dire des lignes vides. Le squelette de chargement couvre déjà le moment où
             * les données arrivent ; cent animations simultanées n'apportaient rien de plus. */
            data.map((row, index) => (
              <tr
                key={row.id || index}
                onClick={() => onRowClick && onRowClick(row)}
                className={`text-sm text-gray-700 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/40 ${
                  onRowClick ? 'cursor-pointer' : ''
                }`}
              >
                {columns.map((column) => (
                  <td key={column.key} className={`${cellPadding} align-middle ${column.nowrap === false ? '' : 'whitespace-nowrap'} ${column.className || ''}`}>
                    {column.render ? column.render(row) : row[column.key]}
                  </td>
                ))}
                {actions && (
                  <td className={`${cellPadding} whitespace-nowrap align-middle`}>
                    <div className="flex items-center justify-end gap-1">
                      {actions(row)}
                    </div>
                  </td>
                )}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
};

export default Table;
