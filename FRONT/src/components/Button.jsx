import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';

const Button = ({
  children,
  onClick,
  variant = 'primary',
  size = 'md',
  icon: Icon,
  loading = false,
  disabled = false,
  type = 'button',
  className = '',
}) => {
  const { t } = useTranslation();

  const variants = {
    primary: 'btn-primary',
    secondary: 'btn-secondary',
    danger: 'btn-danger',
    success:
      'inline-flex items-center justify-center gap-2 bg-green-600 hover:bg-green-700 active:bg-green-800 text-white font-medium rounded-lg shadow-soft hover:shadow-card-hover transition-all duration-150 active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed',
    outline:
      'inline-flex items-center justify-center gap-2 border border-primary-600 text-primary-600 hover:bg-primary-50 active:bg-primary-100 font-medium rounded-lg transition-all duration-150 active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed dark:hover:bg-primary-500/10',
  };

  const sizes = {
    sm: 'px-3 py-1.5 text-sm',
    md: 'px-4 py-2.5',
    lg: 'px-6 py-3 text-lg',
  };

  // Le spinner reprend la couleur du texte du bouton pour rester visible
  // sur les variantes claires (secondary / outline).
  const spinnerColor =
    variant === 'secondary' || variant === 'outline'
      ? 'border-current border-t-transparent'
      : 'border-white border-t-transparent';

  return (
    <motion.button
      whileHover={{ scale: disabled || loading ? 1 : 1.02 }}
      whileTap={{ scale: disabled || loading ? 1 : 0.98 }}
      type={type}
      onClick={onClick}
      disabled={disabled || loading}
      className={`${variants[variant]} ${sizes[size]} ${className}`}
    >
      {loading ? (
        <>
          <div className={`w-4 h-4 border-2 ${spinnerColor} rounded-full animate-spin`}></div>
          {t('common.loading')}
        </>
      ) : (
        <>
          {Icon && <Icon className="w-5 h-5" />}
          {children}
        </>
      )}
    </motion.button>
  );
};

export default Button;
