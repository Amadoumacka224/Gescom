import { motion, AnimatePresence } from 'framer-motion';
import { AlertCircle, AlertTriangle, CheckCircle, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const ConfirmModal = ({ isOpen, onClose, onConfirm, title, message, type = 'info', confirmLabel, cancelLabel }) => {
  const { t } = useTranslation();
  const typeStyles = {
    info: {
      icon: AlertCircle,
      iconBg: 'bg-blue-100 dark:bg-blue-500/15',
      iconColor: 'text-blue-600 dark:text-blue-400',
      buttonBg: 'bg-blue-600 hover:bg-blue-700',
    },
    success: {
      icon: CheckCircle,
      iconBg: 'bg-green-100 dark:bg-green-500/15',
      iconColor: 'text-green-600 dark:text-green-400',
      buttonBg: 'bg-green-600 hover:bg-green-700',
    },
    warning: {
      icon: AlertTriangle,
      iconBg: 'bg-amber-100 dark:bg-amber-500/15',
      iconColor: 'text-amber-600 dark:text-amber-400',
      buttonBg: 'bg-amber-500 hover:bg-amber-600',
    },
    danger: {
      icon: AlertTriangle,
      iconBg: 'bg-red-100 dark:bg-red-500/15',
      iconColor: 'text-red-600 dark:text-red-400',
      buttonBg: 'bg-red-600 hover:bg-red-700',
    },
  };

  const style = typeStyles[type] || typeStyles.info;
  const Icon = style.icon;

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
              className="bg-white dark:bg-gray-800 rounded-2xl shadow-elevated ring-1 ring-gray-900/5 w-full max-w-md overflow-hidden"
            >
              {/* Icon */}
              <div className="p-6 text-center">
                <div className={`w-14 h-14 ${style.iconBg} rounded-full flex items-center justify-center mx-auto mb-4`}>
                  <Icon className={`w-7 h-7 ${style.iconColor}`} />
                </div>

                {/* Title */}
                <h3 className="modal-title mb-2">
                  {title}
                </h3>

                {/* Message */}
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {message}
                </p>
              </div>

              {/* Actions */}
              <div className="bg-gray-50 dark:bg-gray-900/40 px-6 py-4 flex gap-3 border-t border-gray-100 dark:border-gray-700">
                <button
                  onClick={onClose}
                  className="flex-1 px-4 py-2.5 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200 font-semibold rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 transition-all"
                >
                  {cancelLabel ?? t('common.cancel')}
                </button>
                <button
                  onClick={() => {
                    onConfirm();
                    onClose();
                  }}
                  className={`flex-1 px-4 py-2.5 ${style.buttonBg} text-white font-semibold rounded-lg transition-all shadow-soft`}
                >
                  {confirmLabel ?? t('common.confirm')}
                </button>
              </div>
            </motion.div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default ConfirmModal;
