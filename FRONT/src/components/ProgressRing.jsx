import { motion, useReducedMotion } from 'framer-motion';
import { formatPercent } from '../utils/format';

/**
 * Anneau de progression pour afficher un seul pourcentage.
 *
 * Ce n'est pas un camembert : il ne compare pas des parts, il montre une valeur entre 0 et
 * 100 %. La répartition par statut est affichée avec des barres (`StatusBreakdown`), plus
 * lisibles.
 *
 * Avec un rayon de 15,9155 le périmètre vaut 100 (2πr = 100), donc le décalage du trait est
 * simplement « 100 − pourcentage ».
 *
 * La couleur vient de `currentColor`, donnée par le parent (`panelToneClass`). Le fond de
 * l'anneau est la même couleur en transparent (voir `.ring-*` dans index.css). Le chiffre au
 * centre reste dans la couleur du texte.
 */
const RADIUS = 15.9155;

const ProgressRing = ({ ratio = 0, ariaLabel, className = 'w-24 h-24' }) => {
  // `useReducedMotion` : pas d'animation si l'utilisateur a réduit les animations
  // dans son système.
  const reduceMotion = useReducedMotion();
  const value = Number.isFinite(ratio) ? Math.min(1, Math.max(0, ratio)) : 0;
  const percent = value * 100;

  return (
    <div className={`relative flex-shrink-0 ${className}`} role="img" aria-label={ariaLabel}>
      <svg viewBox="0 0 36 36" className="w-full h-full -rotate-90" aria-hidden="true">
        <circle className="ring-track" cx="18" cy="18" r={RADIUS} fill="none" strokeWidth="3" />
        {/* À 0 % on ne dessine rien : le bout arrondi ferait un point coloré qui ressemble
            à une valeur. */}
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
