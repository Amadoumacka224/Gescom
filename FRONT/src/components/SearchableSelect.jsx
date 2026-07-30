import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Search, ChevronDown, X, Check } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { rankSuggestions } from '../utils/searchSuggestions';

/**
 * Combobox de recherche réutilisable (autocomplétion).
 * Remplace un <select> classique par un champ filtrable, accessible et
 * navigable au clavier (↑/↓ pour parcourir, Entrée pour choisir, Échap pour fermer).
 *
 * Props principales :
 *  - options : tableau d'objets
 *  - value : valeur sélectionnée (comparée via getOptionValue)
 *  - onChange(value) : appelé avec la valeur de l'option choisie ('' si effacé)
 *  - getOptionValue(option) : clé unique (défaut : option.id)
 *  - getOptionLabel(option) : libellé texte affiché et utilisé pour le filtrage
 *  - getOptionSearch(option) : texte additionnel pris en compte par la recherche
 *  - renderOption(option) : rendu enrichi d'une option dans la liste (optionnel)
 */
const SearchableSelect = ({
  options = [],
  value,
  onChange,
  getOptionValue = (o) => o.id,
  getOptionLabel = (o) => String(o),
  getOptionSearch,
  renderOption,
  placeholder,
  noResultsText,
  minChars = 0,
  disabled = false,
  required = false,
  className = '',
  inputClassName = 'w-full pl-10 pr-9 py-2.5 bg-white border-2 border-blue-300 rounded-lg text-sm font-medium text-gray-900 focus:outline-none focus:border-blue-600 focus:ring-4 focus:ring-blue-100 transition-all',
}) => {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  // Position de la liste, calculée par rapport au champ. La liste est rendue dans
  // un portail (document.body) pour ne pas être rognée par un parent en overflow
  // (ex. un modal scrollable).
  const [coords, setCoords] = useState({ top: 0, left: 0, width: 0, openUp: false });

  const containerRef = useRef(null);
  const inputRef = useRef(null);
  const listRef = useRef(null);
  const listboxId = useId();

  const MAX_LIST_HEIGHT = 240; // px (cohérent avec max-h-60)

  const updateCoords = () => {
    if (!inputRef.current) return;
    const rect = inputRef.current.getBoundingClientRect();
    const spaceBelow = window.innerHeight - rect.bottom;
    const openUp = spaceBelow < MAX_LIST_HEIGHT && rect.top > spaceBelow;
    setCoords({
      top: openUp ? rect.top : rect.bottom,
      left: rect.left,
      width: rect.width,
      openUp,
    });
  };

  const selectedOption = options.find(
    (o) => String(getOptionValue(o)) === String(value)
  );
  const selectedLabel = selectedOption ? getOptionLabel(selectedOption) : '';

  // En mode "parcours" (minChars = 0, query vide ou égale au libellé choisi),
  // on affiche toute la liste ; sinon on classe les options par pertinence vis-à-vis
  // du texte saisi (libellé prioritaire sur le texte de recherche secondaire), de la
  // plus pertinente à la moins pertinente — cf. rankSuggestions.
  const showAll = minChars === 0 && (!query || query === selectedLabel);
  const filtered = showAll
    ? options
    : rankSuggestions(
        options,
        query,
        (o) => [getOptionLabel(o), getOptionSearch ? getOptionSearch(o) : ''],
        options.length
      );

  // La liste n'est visible que si le seuil minChars est atteint. Avec minChars > 0,
  // on n'affiche rien tant que l'utilisateur n'a pas saisi assez de caractères
  // (et tant que le texte n'est que le libellé pré-rempli de la sélection courante).
  const meetsMinChars = query.trim().length >= minChars;
  const showList =
    isOpen && (minChars === 0 ? true : meetsMinChars && query !== selectedLabel);

  // Garde le champ synchronisé avec la sélection lorsque la liste est fermée
  // (sélection externe, réinitialisation du formulaire, etc.).
  useEffect(() => {
    if (!isOpen) setQuery(selectedLabel);
  }, [value, selectedLabel, isOpen]);

  // Fermeture au clic en dehors du composant (le champ ou la liste portée).
  useEffect(() => {
    const handleClickOutside = (e) => {
      const inField = containerRef.current?.contains(e.target);
      const inList = listRef.current?.contains(e.target);
      if (!inField && !inList) setIsOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Recalcule la position de la liste dès qu'elle devient visible (showList), puis au
  // défilement/redimensionnement (capture=true pour intercepter le scroll des conteneurs
  // internes comme un modal). NB : on dépend de `showList` et non de `isOpen` — avec
  // minChars>0, la liste n'apparaît qu'à la 1re lettre saisie, alors que `isOpen` est déjà
  // vrai depuis le focus ; sans ça les coordonnées resteraient à 0 et la liste serait
  // invisible (largeur 0, coin haut-gauche).
  useLayoutEffect(() => {
    if (!showList) return;
    updateCoords();
    const onReposition = () => updateCoords();
    window.addEventListener('scroll', onReposition, true);
    window.addEventListener('resize', onReposition);
    return () => {
      window.removeEventListener('scroll', onReposition, true);
      window.removeEventListener('resize', onReposition);
    };
  }, [showList]);

  // Maintient l'option survolée visible dans la liste défilante.
  useEffect(() => {
    if (!isOpen || !listRef.current) return;
    const node = listRef.current.children[highlight];
    if (node) node.scrollIntoView({ block: 'nearest' });
  }, [highlight, isOpen]);

  const open = () => {
    if (disabled) return;
    setIsOpen(true);
    const idx = filtered.findIndex(
      (o) => String(getOptionValue(o)) === String(value)
    );
    setHighlight(idx >= 0 ? idx : 0);
  };

  const select = (option) => {
    onChange(getOptionValue(option));
    setQuery(getOptionLabel(option));
    setIsOpen(false);
    inputRef.current?.blur();
  };

  const clear = () => {
    onChange('');
    setQuery('');
    setIsOpen(false);
    inputRef.current?.focus();
  };

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!isOpen) return open();
      setHighlight((h) => Math.min(h + 1, filtered.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    } else if (e.key === 'Enter') {
      if (isOpen && filtered[highlight]) {
        e.preventDefault();
        select(filtered[highlight]);
      }
    } else if (e.key === 'Escape') {
      setIsOpen(false);
      setQuery(selectedLabel);
    }
  };

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none" />
      <input
        ref={inputRef}
        type="text"
        role="combobox"
        aria-expanded={showList}
        aria-controls={listboxId}
        aria-autocomplete="list"
        autoComplete="off"
        required={required && !value}
        disabled={disabled}
        value={query}
        placeholder={placeholder ?? t('common.searchPlaceholder')}
        onChange={(e) => {
          setQuery(e.target.value);
          setIsOpen(true);
          setHighlight(0);
        }}
        onFocus={open}
        onKeyDown={handleKeyDown}
        className={inputClassName}
      />
      {value ? (
        <button
          type="button"
          onClick={clear}
          tabIndex={-1}
          className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
          title={t('common.clear')}
        >
          <X className="w-4 h-4" />
        </button>
      ) : minChars === 0 ? (
        // L'indicateur « dérouler » n'a de sens qu'en mode parcours (minChars = 0) : un clic
        // ouvre alors la liste complète. En mode recherche (minChars > 0), un clic n'affiche
        // rien tant qu'aucun caractère n'est saisi ; on masque donc le chevron pour laisser
        // une vraie barre de recherche (seule la loupe à gauche indique l'action).
        <ChevronDown
          className={`absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none transition-transform ${
            showList ? 'rotate-180' : ''
          }`}
        />
      ) : null}

      {showList && createPortal(
        <ul
          ref={listRef}
          id={listboxId}
          role="listbox"
          style={{
            position: 'fixed',
            top: coords.openUp ? undefined : coords.top + 4,
            bottom: coords.openUp ? window.innerHeight - coords.top + 4 : undefined,
            left: coords.left,
            width: coords.width,
            maxHeight: MAX_LIST_HEIGHT,
          }}
          className="z-[60] overflow-auto bg-white border-2 border-gray-200 rounded-lg shadow-xl py-1"
        >
          {filtered.length === 0 ? (
            <li className="px-3 py-2 text-sm text-gray-500 text-center">{noResultsText ?? t('common.noResults')}</li>
          ) : (
            filtered.map((option, index) => {
              const isSelected = String(getOptionValue(option)) === String(value);
              const isHighlighted = index === highlight;
              return (
                <li
                  key={getOptionValue(option)}
                  role="option"
                  aria-selected={isSelected}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => select(option)}
                  onMouseEnter={() => setHighlight(index)}
                  className={`flex items-center justify-between gap-2 px-3 py-2 text-sm cursor-pointer ${
                    isHighlighted ? 'bg-blue-50' : ''
                  } ${isSelected ? 'font-semibold text-blue-700' : 'text-gray-900'}`}
                >
                  <span className="flex-1 min-w-0">
                    {renderOption ? renderOption(option) : getOptionLabel(option)}
                  </span>
                  {isSelected && <Check className="w-4 h-4 text-blue-600 shrink-0" />}
                </li>
              );
            })
          )}
        </ul>,
        document.body
      )}
    </div>
  );
};

export default SearchableSelect;
