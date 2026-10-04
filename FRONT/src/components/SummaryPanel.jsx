import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { ArrowRight } from 'lucide-react';
import ProgressRing from './ProgressRing';
import StatusBreakdown from './StatusBreakdown';
import { miniStatClass, panelToneClass } from '../constants/statusBadges';
import { formatPercent } from '../utils/format';

/**
 * Panneau de résumé d'un domaine (commandes, factures, livraisons).
 *
 * Même structure pour les trois, de haut en bas :
 *
 *   1. l'en-tête : le sujet et le nombre total ;
 *   2. l'anneau : le pourcentage principal (finalisation, encaissement, livraison) ;
 *   3. le chiffre à retenir (`highlight`) : ce qui reste à traiter, le seul en couleur ;
 *   4. la répartition par statut, avec des barres ;
 *   5. le lien vers l'écran complet, toujours en bas.
 *
 * Comme les trois panneaux ont la même structure, on sait tout de suite où regarder.
 *
 * `tone` donne juste la couleur du domaine (icône + anneau). Les statuts gardent les couleurs
 * des badges.
 */
const SummaryPanel = ({
  icon: Icon,
  tone = 'info',
  title,
  total,
  totalLabel,
  rate,
  highlight,
  rows,
  actionLabel,
  onAction,
  delay = 0,
}) => {
  const { t } = useTranslation();

  return (
    <motion.section
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay }}
      className="card flex flex-col"
    >
      <header className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <div className={`panel-icon ${panelToneClass(tone)}`}>
            <Icon aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <h2 className="section-title truncate">{title}</h2>
            <p className="text-xs text-gray-500 dark:text-gray-400">{totalLabel}</p>
          </div>
        </div>
        {/* Pas de `tabular-nums` : à cette taille, les chiffres à largeur fixe donnent un
            nombre trop espacé. */}
        <span className="text-2xl font-bold leading-none text-gray-900 dark:text-gray-100">
          {total}
        </span>
      </header>

      <div className="mt-5 flex items-center gap-4">
        <div className={panelToneClass(tone)}>
          <ProgressRing
            ratio={rate.ratio}
            ariaLabel={t('dashboard.rateAriaLabel', {
              label: rate.label,
              value: formatPercent(rate.ratio),
            })}
          />
        </div>
        <div className="min-w-0">
          <p className="subsection-title">{rate.label}</p>
          <p className="mt-1 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
            {rate.caption}
          </p>
        </div>
      </div>

      {highlight && (
        <div className={`${miniStatClass(highlight.tone)} mt-4`}>
          <span>{highlight.label}</span>
          <span className="font-bold tabular-nums">{highlight.value}</span>
        </div>
      )}

      <div className="my-5 border-t border-gray-100 dark:border-gray-700" />

      <StatusBreakdown rows={rows} total={total} />

      <button
        type="button"
        onClick={onAction}
        className="mt-auto pt-4 text-sm font-medium text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300 flex items-center justify-center gap-1 rounded-lg transition-colors"
      >
        {actionLabel} <ArrowRight className="w-4 h-4" aria-hidden="true" />
      </button>
    </motion.section>
  );
};

export default SummaryPanel;
