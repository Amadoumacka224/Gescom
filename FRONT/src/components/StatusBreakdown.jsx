import { metricBarClass, toneDotClass } from '../constants/statusBadges';
import { formatPercent, safeRatio } from '../utils/format';

/**
 * Répartition par statut : une ligne par statut avec sa pastille, son libellé, son nombre,
 * son pourcentage et sa barre.
 *
 * Pourquoi une barre par ligne plutôt qu'une seule barre empilée :
 *   - un petit nombre (une commande annulée sur cent) ferait un segment invisible ;
 *   - deux couleurs proches (bleu « Confirmée » et indigo « Facturée ») seraient difficiles
 *     à distinguer, alors qu'ici chaque barre a son libellé ;
 *   - tout est lisible sans survol, donc aussi au clavier et au lecteur d'écran.
 *
 * Les barres sont en `aria-hidden` car le nombre et le pourcentage sont déjà écrits sur
 * la même ligne.
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
            {/* `gray-400` en mode sombre et pas `gray-500` : sur le fond des cartes, le gris 500
                en 12 px donne un contraste de 3,4:1, en dessous du minimum AA. */}
            <span className="w-11 text-right text-xs tabular-nums text-gray-500 dark:text-gray-400">
              {formatPercent(share)}
            </span>
          </div>
          <div className="metric-track mt-1.5" aria-hidden="true">
            {/* `minWidth` : une très petite part doit quand même se voir, sinon la barre est
                vide alors que le chiffre affiche « 1 ». */}
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
