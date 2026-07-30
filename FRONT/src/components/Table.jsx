import { motion } from 'framer-motion';
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
}) => {
  const { t } = useTranslation();
  const colSpan = columns.length + (actions ? 1 : 0);

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
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="bg-gray-50 dark:bg-gray-900/40 border-b border-gray-200 dark:border-gray-700 sticky top-0 z-10">
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                aria-sort={ariaSort(column)}
                className={`table-th ${column.className || ''}`}
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
              <th scope="col" className="table-th-right">
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
                  <td key={cellIndex} className="px-6 py-4">
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
            data.map((row, index) => (
              <motion.tr
                key={row.id || index}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.03 }}
                onClick={() => onRowClick && onRowClick(row)}
                className={`text-sm text-gray-700 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/40 ${
                  onRowClick ? 'cursor-pointer' : ''
                }`}
              >
                {columns.map((column) => (
                  <td key={column.key} className={`px-6 py-4 ${column.nowrap === false ? '' : 'whitespace-nowrap'} ${column.className || ''}`}>
                    {column.render ? column.render(row) : row[column.key]}
                  </td>
                ))}
                {actions && (
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center justify-end gap-1">
                      {actions(row)}
                    </div>
                  </td>
                )}
              </motion.tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
};

export default Table;
