import { motion } from 'framer-motion';
import { statTileClass } from '../constants/statusBadges';

/**
 * Carte de statistique des tableaux de bord.
 *
 * `tone` indique le sens du chiffre, pas une couleur : un chiffre d'affaires est `success`,
 * un nombre de ruptures `danger`, un simple volume `info`. Les couleurs sont les mêmes que
 * celles des badges (voir `src/index.css`) pour garder la même logique partout.
 *
 * On utilise un fond clair au lieu des anciens dégradés avec texte blanc : le chiffre reste
 * lisible quelle que soit la couleur (ce n'était pas le cas avec `to-pink-600`).
 *
 * Toutes les couleurs de la carte (fond, bordure, icône, texte) viennent de cette même
 * valeur, rien n'est en dur ici (voir `.stat-*` dans index.css).
 *
 * Pendant le chargement on affiche des blocs gris plutôt que « 0,00 € », sinon on afficherait
 * un chiffre faux.
 */
const StatCard = ({ title, value, subtitle, icon: Icon, tone = 'info', loading = false }) => (
  <motion.div
    initial={{ opacity: 0, y: 12 }}
    animate={{ opacity: 1, y: 0 }}
    className={statTileClass(tone)}
  >
    <div className="flex items-center justify-between gap-3">
      <div className="min-w-0 flex-1">
        <p className="stat-label">{title}</p>
        {loading ? (
          <>
            <div className="stat-skeleton h-8 w-3/4 max-w-[7rem] mt-2" />
            <div className="stat-skeleton h-3 w-1/2 max-w-[5rem] mt-2" />
          </>
        ) : (
          <>
            {/* Taille, couleur et retour à la ligne gérés par `.stat-value`. */}
            <p className="stat-value">{value}</p>
            {subtitle && <p className="stat-hint">{subtitle}</p>}
          </>
        )}
      </div>
      {/* Couleur et taille de l'icône gérées par `.stat-tile-* .stat-icon` (voir index.css) :
       * elle se réduit toute seule dans une carte étroite, donc pas de classe de taille ici. */}
      <div className="stat-icon">
        <Icon aria-hidden="true" />
      </div>
    </div>
  </motion.div>
);

export default StatCard;
