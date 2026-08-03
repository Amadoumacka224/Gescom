import { AlertCircle } from 'lucide-react';

/**
 * Champ de saisie standard des formulaires.
 *
 * L'aide (`hint`) et l'erreur (`error`) sont rattachées au champ par `aria-describedby` :
 * un lecteur d'écran annonce la contrainte en même temps que le libellé, au lieu de laisser
 * l'utilisateur découvrir le message uniquement à l'œil. L'aide s'efface quand une erreur
 * s'affiche — les deux textes empilés sous le champ brouilleraient le message à corriger.
 */
const FormInput = ({
  label,
  name,
  type = 'text',
  value,
  onChange,
  placeholder,
  required = false,
  error,
  hint,
  icon: Icon,
  className = '',
  ...props
}) => {
  const describedBy = [hint && !error ? `${name}-hint` : null, error ? `${name}-error` : null]
    .filter(Boolean)
    .join(' ');

  const fieldProps = {
    id: name,
    name,
    value,
    onChange,
    placeholder,
    required,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': describedBy || undefined,
    className: `input-field ${Icon ? 'pl-10' : ''} ${
      error ? 'border-red-500 hover:border-red-500 focus:border-red-500 focus:ring-red-500/15' : ''
    } ${className}`,
  };

  return (
    <div className="space-y-2">
      {label && (
        <label htmlFor={name} className="block text-sm font-medium text-gray-700 dark:text-gray-300">
          {label}
          {required && <span className="text-red-500" aria-hidden="true"> *</span>}
        </label>
      )}
      <div className="relative">
        {Icon && (
          <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
            <Icon className={`h-5 w-5 ${error ? 'text-red-400' : 'text-gray-400'}`} aria-hidden="true" />
          </div>
        )}
        {type === 'textarea' ? (
          <textarea {...fieldProps} rows={4} {...props} />
        ) : (
          <input {...fieldProps} type={type} {...props} />
        )}
      </div>
      {hint && !error && (
        <p id={`${name}-hint`} className="text-xs text-gray-500 dark:text-gray-400">
          {hint}
        </p>
      )}
      {error && (
        <p
          id={`${name}-error`}
          className="flex items-start gap-1.5 text-sm text-red-600 dark:text-red-400"
        >
          <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </p>
      )}
    </div>
  );
};

export default FormInput;
