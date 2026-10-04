/**
 * Règles du formulaire client, utilisées par l'écran Clients et par la création rapide
 * depuis le panier.
 *
 * Les deux envoient le même `ClientRequest`, donc ils doivent refuser les mêmes saisies avec
 * les mêmes messages. Avant, le panier avait sa propre version : trois champs obligatoires
 * vérifiés d'un coup, aucun contrôle de format, et pas de ville, code postal ni pays. Un
 * client créé en caisse n'avait donc pas d'adresse utilisable pour la facture.
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
 * Longueurs max, les mêmes que les `@Size` de `ClientRequest`. Utilisées pour `maxLength`
 * (on ne peut pas taper plus) et pour la validation (une valeur collée ou déjà existante
 * peut quand même être trop longue).
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

/** Même regex que le `@Pattern` du backend : on refuse ici ce qu'il refuserait de toute façon. */
const PHONE_PATTERN = /^[0-9+\- ]{6,20}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** Champs facultatifs : s'ils sont vides, on envoie `null` (voir `buildClientPayload`). */
const OPTIONAL_TEXT_FIELDS = ['email', 'address', 'city', 'postalCode', 'country', 'company'];

/** Ordre des champs à l'écran : sert à savoir lequel prend le focus s'il y a plusieurs erreurs. */
export const CLIENT_FIELD_ORDER = [
  'firstName', 'lastName', 'company', 'email', 'phone', 'address', 'postalCode', 'city', 'country',
];

/**
 * Valide tout le formulaire et renvoie les erreurs par champ.
 * La fonction n'a pas d'effet de bord : l'appelant la relance à chaque frappe et choisit
 * juste *quand* afficher chaque message (champ visité ou essai d'enregistrement), pour ne pas
 * afficher d'erreur sur un champ que l'utilisateur n'a pas encore rempli.
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

  // La raison sociale apparaît sur les documents de l'entreprise : obligatoire seulement pour
  // ce type, un particulier n'a pas de société.
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
 * Prépare le body de la requête : valeurs sans espaces autour, et champs facultatifs vides
 * mis à `null`. Si on envoyait une chaîne vide pour l'email, elle serait enregistrée, et le
 * contrôle d'unicité du backend refuserait le client suivant sans email.
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
 * Noms des champs comme à l'écran : la liste des erreurs doit utiliser les mêmes noms que
 * ceux que voit l'utilisateur, pas ceux du DTO.
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

/** Est-ce que le formulaire a changé depuis son ouverture ? */
export const isClientFormDirty = (values, initial) =>
  CLIENT_FIELD_KEYS.some((key) => values[key] !== initial[key]);
