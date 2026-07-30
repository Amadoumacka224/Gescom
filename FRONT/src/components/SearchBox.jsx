import { useEffect, useRef, useState } from 'react';
import { Search, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * Champ de recherche avec liste de suggestions (autocomplétion) affichée sous le champ.
 * Le filtrage reste piloté par le parent via `value`/`onChange` ; ce composant se contente
 * d'afficher les suggestions déjà calculées (`suggestions`) et de remonter le clic.
 *
 * Navigation : ↑/↓ pour parcourir, Entrée pour valider, Échap pour fermer.
 */
const SearchBox = ({
  id,
  value,
  onChange,
  placeholder,
  suggestions = [],
  getKey = (item) => item.id,
  renderSuggestion,
  onSelectSuggestion,
  minChars = 1,
  noResultsText,
  className = '',
  inputClassName = 'input-field pl-10 pr-9 w-full',
}) => {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const containerRef = useRef(null);
  const listRef = useRef(null);

  const canShow = open && value.trim().length >= minChars;

  // Fermeture au clic en dehors.
  useEffect(() => {
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // Réinitialise l'élément survolé quand la requête change.
  useEffect(() => setHighlight(0), [value]);

  // Maintient l'élément survolé visible.
  useEffect(() => {
    if (!canShow || !listRef.current) return;
    const node = listRef.current.children[highlight];
    if (node) node.scrollIntoView({ block: 'nearest' });
  }, [highlight, canShow]);

  const select = (item) => {
    onSelectSuggestion?.(item);
    setOpen(false);
  };

  const handleKeyDown = (e) => {
    if (!canShow) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlight((h) => Math.min(h + 1, suggestions.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    } else if (e.key === 'Enter') {
      if (suggestions[highlight]) {
        e.preventDefault();
        select(suggestions[highlight]);
      }
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400 pointer-events-none" />
      <input
        id={id}
        type="text"
        value={value}
        placeholder={placeholder ?? t('common.searchPlaceholder')}
        autoComplete="off"
        role="combobox"
        aria-expanded={canShow}
        aria-autocomplete="list"
        onChange={(e) => {
          onChange(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={handleKeyDown}
        className={inputClassName}
      />
      {value && (
        <button
          type="button"
          tabIndex={-1}
          onClick={() => {
            onChange('');
            setOpen(false);
          }}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
          title={t('common.clear')}
        >
          <X className="w-4 h-4" />
        </button>
      )}

      {canShow && (
        <ul
          ref={listRef}
          role="listbox"
          className="absolute z-50 mt-1 w-full max-h-72 overflow-auto bg-white border border-gray-200 rounded-lg shadow-xl py-1"
        >
          {suggestions.length === 0 ? (
            <li className="px-3 py-2 text-sm text-gray-500 text-center">{noResultsText ?? t('common.noResults')}</li>
          ) : (
            suggestions.map((item, idx) => (
              <li
                key={getKey(item)}
                role="option"
                aria-selected={idx === highlight}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => select(item)}
                onMouseEnter={() => setHighlight(idx)}
                className={`px-3 py-2 text-sm cursor-pointer text-gray-900 ${idx === highlight ? 'bg-blue-50' : ''}`}
              >
                {renderSuggestion ? renderSuggestion(item) : getKey(item)}
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
};

export default SearchBox;
