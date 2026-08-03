/**
 * Repère chiffré ou daté d'un en-tête de fiche : échéance d'une facture, nombre de produits
 * rattachés à une catégorie, mode de règlement…
 *
 * Quatre repères alignés se balaient d'un regard, là où quatre lignes libellé/valeur empilées
 * demandent quatre allers-retours. À réserver aux valeurs courtes : la valeur est tronquée
 * plutôt que de casser l'alignement de la rangée.
 *
 * S'utilise dans un `<dl>` — le composant émet le couple `<dt>` / `<dd>`.
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
