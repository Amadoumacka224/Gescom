import { motion } from 'framer-motion';
import { statTileClass } from '../constants/statusBadges';

/**
 * Tuile d'indicateur des tableaux de bord.
 *
 * `tone` porte le SENS de la donnée, pas une couleur : un chiffre d'affaires est `success`,
 * un compteur de ruptures `danger`, un volume neutre `info`. Les teintes correspondantes sont
 * celles des badges (voir `src/index.css`), pour qu'un même sens ait la même couleur partout.
 *
 * Le fond teinté clair a remplacé les dégradés saturés à texte blanc : le contraste du chiffre
 * y est garanti quelle que soit la teinte, ce qui n'était pas le cas sur `to-pink-600`.
 *
 * Tout l'intérieur de la carte — fond, bordure, disque d'icône, libellé, chiffre, légende —
 * dérive du même jeton : aucune couleur n'est écrite en dur ici (voir `.stat-*` dans index.css).
 *
 * Pendant le chargement, on affiche des blocs neutres plutôt qu'un « 0,00 € » : un zéro
 * affiché est une information fausse tant que les données ne sont pas arrivées.
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
            {/* Taille, teinte et repli sans troncature sont portés par `.stat-value`. */}
            <p className="stat-value">{value}</p>
            {subtitle && <p className="stat-hint">{subtitle}</p>}
          </>
        )}
      </div>
      {/* Teinte et taille du disque sont portées par `.stat-tile-* .stat-icon` (voir index.css) :
       * il rétrécit tout seul dans une tuile étroite, d'où l'absence de classe de taille ici. */}
      <div className="stat-icon">
        <Icon aria-hidden="true" />
      </div>
    </div>
  </motion.div>
);

export default StatCard;
