import { X } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { useTranslation } from 'react-i18next';

/**
 * `fill` : le contenu occupe toute la hauteur disponible et gère lui-même son défilement,
 * au lieu de s'empiler dans une zone scrollable. C'est ce que réclame un poste de travail
 * (le panier de commande) dont les colonnes doivent aller jusqu'en bas de l'écran : la boîte
 * prend alors toute la hauteur utile et se contente d'un rembourrage discret, chaque pixel
 * gagné étant une ligne d'article de plus à l'écran.
 */
const Modal = ({ isOpen, onClose, title, children, size = 'md', fill = false }) => {
  const { t } = useTranslation();
  const sizes = {
    sm: 'max-w-md',
    md: 'max-w-2xl',
    lg: 'max-w-4xl',
    xl: 'max-w-6xl',
    fullscreen: 'max-w-[95vw]',
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-gray-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          >
            {/* Modal */}
            <motion.div
              initial={{ scale: 0.96, opacity: 0, y: 8 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.96, opacity: 0, y: 8 }}
              transition={{ type: 'spring', duration: 0.3, bounce: 0.15 }}
              onClick={(e) => e.stopPropagation()}
              className={`bg-white dark:bg-gray-800 rounded-2xl shadow-elevated ring-1 ring-gray-900/5 w-full ${sizes[size]} ${
                // La hauteur retranche le rembourrage du fond (p-4) pour ne jamais déborder,
                // sans quoi le haut de la boîte serait rogné sur les petits écrans.
                fill ? 'h-[calc(100vh-2rem)] max-h-[95vh]' : 'max-h-[90vh]'
              } overflow-hidden flex flex-col`}
            >
              {/* Header */}
              <div className={`${fill ? 'px-5 py-3' : 'px-6 py-4'} border-b border-gray-200 dark:border-gray-700 flex items-center justify-between shrink-0`}>
                <h2 className="modal-title">{title}</h2>
                <button
                  onClick={onClose}
                  aria-label={t('common.close')}
                  className="p-2 -mr-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Content */}
              <div className={fill
                ? 'flex-1 min-h-0 overflow-hidden p-3 sm:p-4'
                : 'flex-1 overflow-y-auto p-6'}
              >
                {children}
              </div>
            </motion.div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default Modal;
