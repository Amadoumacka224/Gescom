import { useMemo } from 'react';
import { SlidersHorizontal, X, Calendar, RotateCcw } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * Barre de recherche et de filtres des écrans de liste, en quatre parties :
 *   1. la recherche et le bouton « Filtres avancés » ;
 *   2. les filtres rapides de la page ;
 *   3. le panneau des filtres détaillés (fermé par défaut) ;
 *   4. les pastilles des filtres actifs, pour ne pas oublier un filtre caché.
 *
 * Le composant ne filtre rien lui-même : il affiche les champs et appelle
 * `onChange(clé, valeur)`. C'est la page qui filtre.
 *
 * Format d'un champ :
 *   { key, label, type: 'select' | 'text' | 'number' | 'date' | 'checkbox' | 'custom',
 *     options?: [{ value, label }], placeholder?, min?, max?, step?,
 *     render?: ({ id, value, onChange }) => node,   // obligatoire pour `custom`
 *     chipValue?: (value) => string }               // texte affiché dans la pastille
 *
 * `custom` sert quand un simple `<select>` ne suffit pas (ex. liste de clients avec recherche).
 */

const FIELD_CLASS =
  'w-full px-3 py-2 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg text-sm text-gray-900 dark:text-gray-100 placeholder:text-gray-400 focus:border-primary-500 focus:ring-4 focus:ring-primary-500/15 outline-none transition-all';

/** Formate une date en `yyyy-MM-dd` (heure locale) pour un champ date. */
const toDateInput = (date) => {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};

/** Raccourcis de période : ils remplissent les champs « du » et « au », modifiables ensuite. */
const buildDateShortcuts = () => {
  const today = new Date();
  const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const shift = (days) => {
    const d = startOfDay(today);
    d.setDate(d.getDate() - days);
    return d;
  };
  const startOfMonth = (offset = 0) => new Date(today.getFullYear(), today.getMonth() + offset, 1);
  const endOfMonth = (offset = 0) => new Date(today.getFullYear(), today.getMonth() + offset + 1, 0);

  return [
    { key: 'today', labelKey: 'filters.range.today', from: startOfDay(today), to: startOfDay(today) },
    { key: '7d', labelKey: 'filters.range.last7', from: shift(6), to: startOfDay(today) },
    { key: '30d', labelKey: 'filters.range.last30', from: shift(29), to: startOfDay(today) },
    { key: 'month', labelKey: 'filters.range.thisMonth', from: startOfMonth(), to: endOfMonth() },
    { key: 'prevMonth', labelKey: 'filters.range.lastMonth', from: startOfMonth(-1), to: endOfMonth(-1) },
  ];
};

const Field = ({ label, htmlFor, children }) => (
  <div>
    <label htmlFor={htmlFor} className="block text-xs font-semibold text-gray-600 dark:text-gray-400 mb-1.5">
      {label}
    </label>
    {children}
  </div>
);

const Chip = ({ label, removeLabel, onRemove }) => (
  <span className="inline-flex items-center gap-1.5 pl-2.5 pr-1.5 py-1 rounded-full bg-primary-100 dark:bg-primary-500/20 text-primary-900 dark:text-primary-100 text-xs font-medium">
    {label}
    <button
      type="button"
      onClick={onRemove}
      aria-label={removeLabel}
      className="p-0.5 rounded-full hover:bg-primary-300/60 dark:hover:bg-primary-400/30 transition-colors"
    >
      <X className="w-3 h-3" aria-hidden="true" />
    </button>
  </span>
);

const AdvancedFilters = ({
  id,
  search,
  quickFilters = null,
  fields,
  values,
  defaults,
  onChange,
  onReset = null,
  // true si un filtre ou une recherche est actif : affiche le bouton « Réinitialiser ».
  resettable = false,
  expanded,
  onToggleExpanded,
  // { fromKey, toKey } : affiche les raccourcis de période sous les champs.
  dateRange = null,
  toggleLabel,
}) => {
  const { t } = useTranslation();
  // Calculé une seule fois, au montage.
  const shortcuts = useMemo(() => buildDateShortcuts(), []);

  const activeFields = fields.filter((field) => values[field.key] !== defaults[field.key]);

  const chipLabel = (field) => {
    const value = values[field.key];
    if (field.type === 'checkbox') return field.label;
    // Texte lisible plutôt que la valeur brute (ex. nom du client au lieu de son id).
    if (field.chipValue) return `${field.label} : ${field.chipValue(value)}`;
    if (field.type === 'select') {
      const option = field.options?.find((o) => String(o.value) === String(value));
      return `${field.label} : ${option?.label ?? value}`;
    }
    return `${field.label} : ${value}`;
  };

  const applyShortcut = (shortcut) => {
    onChange(dateRange.fromKey, toDateInput(shortcut.from));
    onChange(dateRange.toKey, toDateInput(shortcut.to));
  };

  const shortcutActive = (shortcut) =>
    values[dateRange.fromKey] === toDateInput(shortcut.from)
    && values[dateRange.toKey] === toDateInput(shortcut.to);

  const renderField = (field) => {
    const inputId = `${id}-filter-${field.key}`;
    const value = values[field.key];

    if (field.type === 'checkbox') {
      return (
        <div key={field.key} className="flex items-end">
          <label className="inline-flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300 cursor-pointer py-2">
            <input
              type="checkbox"
              checked={!!value}
              onChange={(e) => onChange(field.key, e.target.checked)}
              className="w-4 h-4 rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500/40"
            />
            {field.label}
          </label>
        </div>
      );
    }

    // Champ fourni par la page. Pas de `htmlFor` : ce n'est pas forcément un vrai input.
    if (field.type === 'custom') {
      return (
        <Field key={field.key} label={field.label}>
          {field.render({ id: inputId, value, onChange: (next) => onChange(field.key, next) })}
        </Field>
      );
    }

    return (
      <Field key={field.key} label={field.label} htmlFor={inputId}>
        {field.type === 'select' ? (
          <select
            id={inputId}
            value={value}
            onChange={(e) => onChange(field.key, e.target.value)}
            className={FIELD_CLASS}
          >
            {field.options.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        ) : (
          <input
            id={inputId}
            type={field.type}
            value={value}
            min={field.min}
            max={field.max}
            step={field.step}
            inputMode={field.type === 'number' ? 'decimal' : undefined}
            placeholder={field.placeholder}
            onChange={(e) => onChange(field.key, e.target.value)}
            className={FIELD_CLASS}
          />
        )}
      </Field>
    );
  };

  return (
    <div className="space-y-4">
      {/* 1. Recherche et bouton des filtres */}
      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        <div className="flex-1 min-w-0">{search}</div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={onToggleExpanded}
            aria-expanded={expanded}
            aria-controls={`${id}-advanced-filters`}
            className="btn-secondary text-sm whitespace-nowrap"
          >
            <SlidersHorizontal className="w-4 h-4" aria-hidden="true" />
            {toggleLabel ?? t('filters.advanced')}
            {activeFields.length > 0 && (
              <span className="ml-1 min-w-[1.25rem] px-1.5 py-0.5 rounded-full bg-primary-600 text-white text-[11px] font-bold tabular-nums">
                {activeFields.length}
              </span>
            )}
          </button>
          {onReset && resettable && (
            <button
              type="button"
              onClick={onReset}
              className="btn-secondary text-sm whitespace-nowrap"
              title={t('filters.resetAllTooltip')}
            >
              <RotateCcw className="w-4 h-4" aria-hidden="true" />
              {t('filters.reset')}
            </button>
          )}
        </div>
      </div>

      {/* 2. Filtres rapides */}
      {quickFilters && <div className="flex flex-wrap items-center gap-2">{quickFilters}</div>}

      {/* 3. Filtres détaillés */}
      {expanded && (
        <div id={`${id}-advanced-filters`} className="space-y-4 pt-4 border-t border-gray-100 dark:border-gray-700/60">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {fields.map(renderField)}
          </div>

          {dateRange && (
            <div className="flex flex-wrap items-center gap-2">
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-600 dark:text-gray-400">
                <Calendar className="w-3.5 h-3.5" aria-hidden="true" />
                {t('filters.period')}
              </span>
              {shortcuts.map((shortcut) => (
                <button
                  key={shortcut.key}
                  type="button"
                  onClick={() => applyShortcut(shortcut)}
                  aria-pressed={shortcutActive(shortcut)}
                  className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${
                    shortcutActive(shortcut)
                      ? 'bg-primary-600 border-primary-600 text-white'
                      : 'bg-white dark:bg-gray-700 border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:border-primary-400'
                  }`}
                >
                  {t(shortcut.labelKey)}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 4. Filtres actifs (visibles même panneau fermé) */}
      {activeFields.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold text-gray-500 dark:text-gray-400 mr-1">
            {t('filters.active')}
          </span>
          {activeFields.map((field) => (
            <Chip
              key={field.key}
              label={chipLabel(field)}
              removeLabel={t('filters.removeFilter', { label: chipLabel(field) })}
              onRemove={() => onChange(field.key, defaults[field.key])}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default AdvancedFilters;
