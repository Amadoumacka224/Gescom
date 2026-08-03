/**
 * Règles du formulaire client, partagées par l'écran Clients et la création à la volée
 * depuis le panier de traitement.
 *
 * Les deux écrans enregistrent le même `ClientRequest` : ils doivent donc refuser les mêmes
 * saisies, sous les mêmes messages. Le panier tenait auparavant sa propre version — trois
 * champs obligatoires vérifiés d'un bloc, aucun contrôle de format, et ni ville ni code postal
 * ni pays, si bien qu'un client créé en caisse arrivait sans adresse exploitable en facturation.
 */

export const EMPTY_CLIENT_FORM = {
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  address: '',
  city: '',
  postalCode: '',
  country: '',
  company: '',
  type: 'PARTICULIER',
  active: true,
};

export const CLIENT_FIELD_KEYS = Object.keys(EMPTY_CLIENT_FORM);

/**
 * Longueurs maximales reprises telles quelles des contraintes `@Size` de `ClientRequest`.
 * Elles servent à la fois d'attribut `maxLength` (l'utilisateur ne peut pas dépasser) et de
 * garde-fou à la validation (une valeur collée ou héritée peut, elle, être trop longue).
 */
export const CLIENT_MAX_LENGTHS = {
  firstName: 100,
  lastName: 100,
  email: 100,
  address: 255,
  city: 100,
  postalCode: 20,
  country: 100,
  company: 50,
};

/** Même expression que le `@Pattern` du backend : refuser ici ce qu'il refusera de toute façon. */
const PHONE_PATTERN = /^[0-9+\- ]{6,20}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** Champs facultatifs : vides, ils partent à `null` (cf. `buildClientPayload`). */
const OPTIONAL_TEXT_FIELDS = ['email', 'address', 'city', 'postalCode', 'country', 'company'];

/** Ordre visuel des champs : sert à choisir lequel reçoit le focus quand plusieurs sont en erreur. */
export const CLIENT_FIELD_ORDER = [
  'firstName', 'lastName', 'company', 'email', 'phone', 'address', 'postalCode', 'city', 'country',
];

/**
 * Valide le formulaire en une passe et renvoie les messages par champ.
 * La fonction est pure : l'appelant la rejoue à chaque frappe et décide seulement *quand*
 * afficher chaque message (champ visité ou tentative d'enregistrement), ce qui évite de
 * signaler une erreur sur un champ que l'utilisateur n'a pas encore atteint.
 */
export const validateClient = (data, t) => {
  const errors = {};
  const trimmed = (field) => (data[field] || '').trim();

  if (!trimmed('firstName')) errors.firstName = t('clients.errorFirstNameRequired');
  if (!trimmed('lastName')) errors.lastName = t('clients.errorLastNameRequired');

  if (!trimmed('phone')) errors.phone = t('clients.errorPhoneRequired');
  else if (!PHONE_PATTERN.test(trimmed('phone'))) errors.phone = t('clients.errorPhoneFormat');

  if (trimmed('email') && !EMAIL_PATTERN.test(trimmed('email'))) {
    errors.email = t('clients.errorEmailFormat');
  }

  // La raison sociale identifie l'entreprise sur ses documents : on l'exige pour ce type
  // uniquement, un particulier n'ayant pas de société à renseigner.
  if (data.type === 'ENTREPRISE' && !trimmed('company')) {
    errors.company = t('clients.errorCompanyRequired');
  }

  Object.entries(CLIENT_MAX_LENGTHS).forEach(([field, max]) => {
    if (!errors[field] && trimmed(field).length > max) {
      errors[field] = t('clients.errorMaxLength', { max });
    }
  });

  return errors;
};

/**
 * Prépare le corps de la requête : valeurs élaguées, et facultatifs vides remis à `null`.
 * Envoyer une chaîne vide pour l'email le ferait enregistrer tel quel, et le contrôle d'unicité
 * du backend refuserait alors le client suivant sans email.
 */
export const buildClientPayload = (data) => {
  const payload = { type: data.type, active: data.active };
  ['firstName', 'lastName', 'phone'].forEach((field) => {
    payload[field] = (data[field] || '').trim();
  });
  OPTIONAL_TEXT_FIELDS.forEach((field) => {
    payload[field] = (data[field] || '').trim() || null;
  });
  payload.email = payload.email ? payload.email.toLowerCase() : null;
  return payload;
};

/**
 * Libellés tels qu'affichés à l'écran : un récapitulatif d'erreurs doit nommer les champs
 * comme l'utilisateur les voit, pas comme le DTO les nomme.
 */
export const clientFieldLabels = (t, isCompany) => ({
  firstName: t('clients.firstName'),
  lastName: t('clients.lastName'),
  company: isCompany ? t('clients.legalNameLabel') : t('clients.companyLabel'),
  email: t('clients.email'),
  phone: t('clients.phone'),
  address: t('clients.streetLabel'),
  postalCode: t('clients.postalCode'),
  city: t('clients.city'),
  country: t('clients.country'),
});

/** Le formulaire a-t-il bougé depuis son ouverture ? */
export const isClientFormDirty = (values, initial) =>
  CLIENT_FIELD_KEYS.some((key) => values[key] !== initial[key]);
