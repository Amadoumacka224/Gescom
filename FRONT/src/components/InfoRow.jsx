/**
 * Ligne label / valeur des fiches de détail (client, livraison, facture).
 *
 * Si la valeur est vide on affiche « — », sinon on pourrait croire à un bug d'affichage.
 *
 * À utiliser dans un `<dl>` : le composant génère `<dt>` / `<dd>`, ce qui aide les lecteurs
 * d'écran.
 *
 * `action` permet d'ajouter un bouton à droite de la ligne (copier, compléter…).
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
