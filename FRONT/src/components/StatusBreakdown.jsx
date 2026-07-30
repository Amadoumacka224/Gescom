import { metricBarClass, toneDotClass } from '../constants/statusBadges';
import { formatPercent, safeRatio } from '../utils/format';

/**
 * Répartition par statut : une ligne par état, chacune avec sa pastille, son libellé,
 * son effectif, sa part et sa barre.
 *
 * Trois raisons de préférer une barre par ligne à un empilement unique :
 *   - les effectifs faibles (une commande annulée sur cent) donneraient un segment invisible ;
 *   - deux segments voisins peuvent être indiscernables — bleu « Confirmée » contre indigo
 *     « Facturée » — alors qu'ici chaque barre est accompagnée de son libellé ;
 *   - toutes les valeurs sont lisibles sans survol, donc au clavier et au lecteur d'écran.
 *
 * Les barres sont `aria-hidden` : elles redisent visuellement l'effectif et la part déjà
 * présents en texte sur la même ligne.
 */
const StatusBreakdown = ({ rows, total }) => (
  <ul className="space-y-3">
    {rows.map(({ key, label, value, tone }) => {
      const share = safeRatio(value, total);
      return (
        <li key={key}>
          <div className="flex items-center gap-2">
            <span className={toneDotClass(tone)} aria-hidden="true" />
            <span className="flex-1 truncate text-sm text-gray-600 dark:text-gray-400">{label}</span>
            <span className="text-sm font-semibold tabular-nums text-gray-900 dark:text-gray-100">
              {value}
            </span>
            {/* `gray-400` en sombre et non `gray-500` : sur le fond des cartes, du gris 500
                en 12 px tombe à 3,4:1, sous le seuil AA. */}
            <span className="w-11 text-right text-xs tabular-nums text-gray-500 dark:text-gray-400">
              {formatPercent(share)}
            </span>
          </div>
          <div className="metric-track mt-1.5" aria-hidden="true">
            {/* `minWidth` : une part non nulle mais infime doit rester visible, sinon la barre
                ment par omission là où le chiffre affiche « 1 ». */}
            <div
              className={metricBarClass(tone)}
              style={{ width: `${share * 100}%`, minWidth: value > 0 ? '0.375rem' : 0 }}
            />
          </div>
        </li>
      );
    })}
  </ul>
);

export default StatusBreakdown;
