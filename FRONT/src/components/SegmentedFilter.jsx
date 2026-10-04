/**
 * Groupe de filtres où un seul choix est possible (statut, type, affichage…).
 *
 * Des boutons plutôt qu'un select : les nombres restent toujours visibles (« Livrées 12 »)
 * et il suffit d'un clic au lieu de deux.
 *
 * `aria-pressed` indique le bouton actif, pas seulement la couleur.
 *
 * `flex-wrap` : avec quatre filtres et leurs nombres (« Tous 160 · Disponible 160 · Stock
 * faible 0 · Rupture 0 ») il faut 416 px. Sur téléphone ça ne rentrait pas, et comme la carte
 * est en `overflow-hidden` le dernier filtre était coupé. Maintenant ça passe à la ligne.
 */
const SegmentedFilter = ({ label, value, options, onChange, className = '' }) => (
  <div
    role="group"
    aria-label={label}
    className={`inline-flex flex-wrap items-center gap-1 p-1 rounded-lg border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900/40 ${className}`}
  >
    {options.map((option) => {
      const selected = option.value === value;
      return (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          aria-pressed={selected}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
            selected
              ? 'bg-primary-600 text-white shadow-soft'
              : 'text-gray-600 dark:text-gray-300 hover:bg-white dark:hover:bg-gray-700'
          }`}
        >
          {option.icon && <option.icon className="w-4 h-4" aria-hidden="true" />}
          {option.label}
          {option.count !== undefined && (
            <span className={`tabular-nums text-xs ${selected ? 'text-white/80' : 'text-gray-400 dark:text-gray-500'}`}>
              {option.count}
            </span>
          )}
        </button>
      );
    })}
  </div>
);

export default SegmentedFilter;
