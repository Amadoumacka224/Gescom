/**
 * Petite info mise en avant en haut d'une fiche : échéance d'une facture, nombre de produits
 * d'une catégorie, mode de paiement…
 *
 * Plusieurs infos côte à côte se lisent plus vite qu'une liste. À garder pour des valeurs
 * courtes : une valeur trop longue est coupée.
 *
 * À utiliser dans un `<dl>` (le composant génère `<dt>` / `<dd>`).
 */
const KeyFact = ({ icon: Icon, label, value, hint }) => (
  <div className="rounded-xl border border-gray-200 px-4 py-3 dark:border-gray-700">
    <dt className="flex items-center gap-1.5 text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">
      {Icon && <Icon className="h-3.5 w-3.5" aria-hidden="true" />}
      {label}
    </dt>
    <dd className="mt-1 truncate text-sm font-semibold tabular-nums text-gray-900 dark:text-gray-100">
      {value || '—'}
    </dd>
    {hint && <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{hint}</p>}
  </div>
);

export default KeyFact;
