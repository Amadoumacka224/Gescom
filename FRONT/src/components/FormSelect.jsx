import { ChevronDown } from 'lucide-react';

const FormSelect = ({
  label,
  name,
  value,
  onChange,
  options,
  required = false,
  error,
  placeholder = 'Sélectionner...',
}) => {
  return (
    <div className="space-y-2">
      {label && (
        <label htmlFor={name} className="block text-sm font-medium text-gray-700 dark:text-gray-300">
          {label} {required && <span className="text-red-500">*</span>}
        </label>
      )}
      <div className="relative">
        <select
          id={name}
          name={name}
          value={value}
          onChange={onChange}
          required={required}
          className={`input-field appearance-none pr-10 cursor-pointer ${error ? 'border-red-500' : ''}`}
        >
          <option value="">{placeholder}</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none" />
      </div>
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
    </div>
  );
};

export default FormSelect;
