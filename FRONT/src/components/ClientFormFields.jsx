import { useTranslation } from 'react-i18next';
import { AlertCircle, Building2, Globe, Mail, MapPin, Phone, ToggleRight, User, UserCheck } from 'lucide-react';
import FormInput from './FormInput';
import FormSection from './FormSection';
import { CLIENT_FIELD_ORDER, CLIENT_MAX_LENGTHS, clientFieldLabels } from '../utils/clientForm';

const TYPE_OPTIONS = [
  { value: 'PARTICULIER', icon: User, labelKey: 'clients.typeIndividual', hintKey: 'clients.typeIndividualHint' },
  { value: 'ENTREPRISE', icon: Building2, labelKey: 'clients.typeBusiness', hintKey: 'clients.typeBusinessHint' },
];

/**
 * Champs du formulaire client, partagés par l'écran Clients et le panier.
 * Les valeurs, les erreurs et l'enregistrement sont gérés par le parent.
 *
 * `showStatus` : affiche le switch actif/inactif (écran Clients seulement).
 */
const ClientFormFields = ({
  values,
  errors = {},
  onChange,
  onBlur,
  onFocusField,
  showErrorSummary = false,
  showStatus = true,
}) => {
  const { t } = useTranslation();
  const isCompany = values.type === 'ENTREPRISE';
  const fieldLabels = clientFieldLabels(t, isCompany);
  const invalidFields = CLIENT_FIELD_ORDER.filter((field) => errors[field]);

  const focusField = (field) => {
    if (onFocusField) onFocusField(field);
    else document.getElementById(field)?.focus();
  };

  /** Props communes à tous les champs texte. */
  const fieldProps = (name) => ({
    name,
    value: values[name],
    onChange,
    onBlur,
    error: errors[name],
    maxLength: CLIENT_MAX_LENGTHS[name],
  });

  return (
    <>
      {/* Champs à corriger : un clic sur une entrée amène au champ concerné. */}
      {showErrorSummary && invalidFields.length > 0 && (
        <div
          role="alert"
          className="mb-6 flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-500/30 dark:bg-red-500/10"
        >
          <AlertCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-red-600 dark:text-red-400" aria-hidden="true" />
          <div className="min-w-0 text-sm">
            <p className="font-semibold text-red-800 dark:text-red-300">
              {t('clients.formErrorTitle', { count: invalidFields.length })}
            </p>
            <ul className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-red-700 dark:text-red-300/90">
              {invalidFields.map((field) => (
                <li key={field}>
                  <button
                    type="button"
                    onClick={() => focusField(field)}
                    className="underline underline-offset-2 hover:no-underline"
                  >
                    {fieldLabels[field]}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {/* Type de client, identité, contact, adresse, statut. */}
      <div className="divide-y divide-gray-200 dark:divide-gray-700">
        <FormSection
          icon={UserCheck}
          title={t('clients.typeLabel')}
          description={t('clients.sectionTypeHint')}
        >
          {/* Particulier ou entreprise, en cartes plutôt qu'en liste déroulante. */}
          <div role="radiogroup" aria-label={t('clients.typeLabel')} className="grid gap-3 sm:grid-cols-2">
            {TYPE_OPTIONS.map((option) => {
              const OptionIcon = option.icon;
              const selected = values.type === option.value;
              return (
                <label
                  key={option.value}
                  className={`relative flex cursor-pointer items-start gap-3 rounded-xl border p-4 transition-colors ${
                    selected
                      ? 'border-primary-500 bg-primary-50/60 dark:border-primary-400 dark:bg-primary-500/10'
                      : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50 dark:border-gray-700 dark:hover:border-gray-600 dark:hover:bg-gray-700/40'
                  }`}
                >
                  <input
                    type="radio"
                    name="type"
                    value={option.value}
                    checked={selected}
                    onChange={onChange}
                    className="peer sr-only"
                  />
                  {/* Focus visible ici, car l'input est caché. */}
                  <span className="pointer-events-none absolute inset-0 rounded-xl peer-focus-visible:ring-2 peer-focus-visible:ring-primary-500 peer-focus-visible:ring-offset-2 dark:peer-focus-visible:ring-offset-gray-800" />
                  <OptionIcon
                    className={`h-5 w-5 flex-shrink-0 ${selected ? 'text-primary-600 dark:text-primary-300' : 'text-gray-400'}`}
                    aria-hidden="true"
                  />
                  <span className="min-w-0">
                    <span className="block text-sm font-semibold text-gray-900 dark:text-gray-100">
                      {t(option.labelKey)}
                    </span>
                    <span className="mt-0.5 block text-xs leading-relaxed text-gray-500 dark:text-gray-400">
                      {t(option.hintKey)}
                    </span>
                  </span>
                </label>
              );
            })}
          </div>
        </FormSection>

        <FormSection
          icon={User}
          title={t('clients.sectionIdentity')}
          description={t('clients.sectionIdentityHint')}
        >
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <FormInput
              label={t('clients.firstName')}
              {...fieldProps('firstName')}
              placeholder={t('clients.firstNamePlaceholder')}
              autoComplete="given-name"
              required
              icon={User}
            />
            <FormInput
              label={t('clients.lastName')}
              {...fieldProps('lastName')}
              placeholder={t('clients.lastNamePlaceholder')}
              autoComplete="family-name"
              required
              icon={User}
            />
          </div>
          <FormInput
            label={isCompany ? t('clients.legalNameLabel') : t('clients.companyLabel')}
            {...fieldProps('company')}
            placeholder={t('clients.companyPlaceholder')}
            hint={isCompany ? t('clients.legalNameHint') : t('clients.companyOptionalHint')}
            autoComplete="organization"
            required={isCompany}
            icon={Building2}
          />
        </FormSection>

        <FormSection
          icon={Mail}
          title={t('clients.sectionContact')}
          description={t('clients.sectionContactHint')}
        >
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
            <FormInput
              label={t('clients.phone')}
              type="tel"
              {...fieldProps('phone')}
              maxLength={undefined}
              placeholder={t('clients.phonePlaceholder')}
              hint={t('clients.phoneHint')}
              autoComplete="tel"
              required
              icon={Phone}
            />
            <FormInput
              label={t('clients.email')}
              type="email"
              {...fieldProps('email')}
              placeholder={t('clients.emailPlaceholder')}
              hint={t('clients.emailHint')}
              autoComplete="email"
              icon={Mail}
            />
          </div>
        </FormSection>

        <FormSection
          icon={MapPin}
          title={t('clients.sectionAddress')}
          description={t('clients.sectionAddressHint')}
        >
          <FormInput
            label={t('clients.streetLabel')}
            {...fieldProps('address')}
            placeholder={t('clients.addressPlaceholder')}
            autoComplete="street-address"
            icon={MapPin}
          />
          {/* Code postal plus étroit que la ville. */}
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
            <FormInput
              label={t('clients.postalCode')}
              {...fieldProps('postalCode')}
              placeholder={t('clients.postalCodePlaceholder')}
              autoComplete="postal-code"
            />
            <div className="sm:col-span-2">
              <FormInput
                label={t('clients.city')}
                {...fieldProps('city')}
                placeholder={t('clients.cityPlaceholder')}
                autoComplete="address-level2"
              />
            </div>
          </div>
          <FormInput
            label={t('clients.country')}
            {...fieldProps('country')}
            placeholder={t('clients.countryPlaceholder')}
            autoComplete="country-name"
            icon={Globe}
          />
        </FormSection>

        {showStatus && (
          <FormSection
            icon={ToggleRight}
            title={t('clients.sectionStatus')}
            description={t('clients.sectionStatusHint')}
          >
            {/* Un client inactif n'apparaît plus dans les commandes. */}
            <div className="flex items-center justify-between gap-4 rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/40">
              <div className="min-w-0">
                <label htmlFor="active" className="cursor-pointer font-medium text-gray-900 dark:text-gray-100">
                  {t('clients.activeLabel')}
                </label>
                <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
                  {values.active ? t('clients.activeStateHint') : t('clients.inactiveStateHint')}
                </p>
              </div>
              <label className="relative inline-flex flex-shrink-0 cursor-pointer items-center">
                <input
                  type="checkbox"
                  id="active"
                  name="active"
                  checked={values.active}
                  onChange={onChange}
                  className="sr-only peer"
                />
                <div className="w-11 h-6 bg-gray-200 dark:bg-gray-600 peer-focus-visible:ring-2 peer-focus-visible:ring-primary-500 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"></div>
              </label>
            </div>
          </FormSection>
        )}
      </div>
    </>
  );
};

export default ClientFormFields;
