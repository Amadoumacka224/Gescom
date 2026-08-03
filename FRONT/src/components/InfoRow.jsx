/**
 * Ligne libellé / valeur des fiches de détail (client, livraison, facture).
 *
 * Un champ vide affiche « — » plutôt que rien : une case muette se lit comme un bug d'affichage,
 * alors qu'un tiret dit explicitement « non renseigné ».
 *
 * S'utilise à l'intérieur d'un `<dl>` : le composant émet le couple `<dt>` / `<dd>`, ce qui donne
 * aux lecteurs d'écran la relation entre le libellé et sa valeur.
 *
 * `action` accueille un bouton propre à la ligne (copier, compléter…), aligné à droite pour
 * rester à portée de la valeur qu'il concerne sans s'intercaler dans la lecture.
 */
const InfoRow = ({ icon: Icon, label, value, href, action, className = '' }) => (
  <div className={`flex items-start gap-3 ${className}`}>
    {Icon && <Icon className="w-4 h-4 mt-0.5 text-gray-400 flex-shrink-0" aria-hidden="true" />}
    <div className="min-w-0 flex-1">
      <dt className="text-xs uppercase tracking-wide text-gray-400 dark:text-gray-500">{label}</dt>
      <dd className="text-sm text-gray-900 dark:text-gray-100 break-words">
        {value
          ? (href
              ? <a href={href} className="text-primary-600 dark:text-primary-400 hover:underline">{value}</a>
              : value)
          : <span className="text-gray-400">—</span>}
      </dd>
    </div>
    {action && <div className="flex-shrink-0">{action}</div>}
  </div>
);

export default InfoRow;
