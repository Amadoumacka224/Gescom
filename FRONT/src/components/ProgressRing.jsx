import { motion, useReducedMotion } from 'framer-motion';
import { formatPercent } from '../utils/format';

/**
 * Anneau de progression : un taux, et un seul.
 *
 * Ce n'est pas un camembert — il ne compare pas des parts entre elles, il situe UNE valeur
 * entre 0 et 100 %. La répartition par statut, elle, est affichée en barres libellées
 * (`StatusBreakdown`), là où un empilement de secteurs colorés serait illisible.
 *
 * Le rayon 15,9155 donne un périmètre de 100 unités (2πr = 100) : le décalage du tiret vaut
 * donc directement « 100 − pourcentage », sans calcul de circonférence à la main.
 *
 * La teinte vient de `currentColor` : c'est le parent qui porte le jeton de domaine
 * (`panelToneClass`), la piste étant la même teinte à faible opacité (voir `.ring-*`
 * dans index.css). Le chiffre central reste en encre, jamais à la couleur de la donnée.
 */
const RADIUS = 15.9155;

const ProgressRing = ({ ratio = 0, ariaLabel, className = 'w-24 h-24' }) => {
  // `useReducedMotion` : l'anneau se dessine d'un coup si l'utilisateur a demandé
  // moins d'animations au niveau système.
  const reduceMotion = useReducedMotion();
  const value = Number.isFinite(ratio) ? Math.min(1, Math.max(0, ratio)) : 0;
  const percent = value * 100;

  return (
    <div className={`relative flex-shrink-0 ${className}`} role="img" aria-label={ariaLabel}>
      <svg viewBox="0 0 36 36" className="w-full h-full -rotate-90" aria-hidden="true">
        <circle className="ring-track" cx="18" cy="18" r={RADIUS} fill="none" strokeWidth="3" />
        {/* À 0 %, aucun arc : un bout arrondi laisserait un point coloré qui se lit comme
            une valeur non nulle. */}
        {percent > 0 && (
          <motion.circle
            className="ring-fill"
            cx="18"
            cy="18"
            r={RADIUS}
            fill="none"
            strokeWidth="3"
            strokeLinecap="round"
            strokeDasharray="100 100"
            initial={{ strokeDashoffset: reduceMotion ? 100 - percent : 100 }}
            animate={{ strokeDashoffset: 100 - percent }}
            transition={{ duration: reduceMotion ? 0 : 0.9, ease: 'easeOut' }}
          />
        )}
      </svg>
      <span
        className="absolute inset-0 flex items-center justify-center text-lg font-bold text-gray-900 dark:text-gray-100"
        aria-hidden="true"
      >
        {formatPercent(value)}
      </span>
    </div>
  );
};

export default ProgressRing;
