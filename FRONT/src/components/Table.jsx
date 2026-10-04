import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * Tableau de données de l'application.
 *
 * Options (toutes facultatives) :
 *   - `column.className` : classes mises sur l'en-tête et sur la cellule. Sert à cacher des
 *     colonnes sur petit écran (`hidden lg:table-cell`) plutôt que d'avoir un scroll horizontal.
 *   - `column.sortable` + `sortKey` / `sortDirection` / `onSort` : tri géré par le parent.
 *     L'en-tête a `aria-sort` pour que les lecteurs d'écran connaissent le tri.
 *   - `loading` : lignes squelettes au lieu d'un tableau vide (qu'on lirait « aucun résultat »).
 *   - `emptyState` : contenu quand il n'y a rien (message + action). Sinon texte par défaut.
 *   - `density` : `compact` réduit l'espacement, pour les catalogues avec beaucoup de lignes.
 *     Par défaut `comfortable`, l'espacement normal.
 *   - `maxHeight` : hauteur max de la zone qui scrolle, `null` pour l'enlever.
 *
 * Pourquoi cette hauteur max (en-tête collant) :
 * `position: sticky` se base sur le premier parent qui scrolle. L'en-tête avait déjà la classe
 * mais ne collait jamais, parce que le tableau est dans trois conteneurs qui ne scrollent pas
 * eux-mêmes : `overflow-x-auto` ici, `card overflow-hidden` dans les pages, et `main`
 * (`overflow-auto` dans `MainLayout`) qui grandit avec le contenu. C'est la page qui scrolle.
 * Pour coller à la fenêtre, il aurait fallu modifier ces trois niveaux.
 *
 * Donc c'est le tableau qui scrolle : au-delà de `maxHeight`, les lignes défilent sous
 * l'en-tête qui reste visible. Pour les petits tableaux (la plupart), rien ne change.
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
  // Classes utilitaires sur `.table-th` / la cellule : dans Tailwind, `utilities` passe après
  // `components`, donc l'espacement compact remplace celui de la classe commune.
  const cellPadding = density === 'compact' ? 'px-4 py-2' : 'px-6 py-4';
  // `whitespace-nowrap` sur les en-têtes en mode compact : un titre sur deux lignes
  // (« Prix de vente ») agrandissait l'en-tête et décalait les autres titres.
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
        {/* Fond opaque et bordure faite avec une ombre : en mode sombre, avec `bg-gray-900/40`
            on voyait les lignes passer à travers l'en-tête collé, et une vraie bordure sur un
            `thead` sticky disparaît avec `border-collapse` (défaut de Tailwind). */}
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
            /* Plus d'animation d'apparition sur les lignes.
             *
             * Elles apparaissaient une par une (30 ms par ligne), donc sur une page de cent
             * produits la dernière arrivait trois secondes après. En plus, depuis que le tableau
             * a son propre scroll, Chrome ne lance pas l'animation des lignes cachées, qui
             * restaient à `opacity: 0` (lignes vides). Le squelette de chargement suffit. */
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
