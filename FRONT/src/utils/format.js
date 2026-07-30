/**
 * Formatage partagé des montants, dates et heures.
 *
 * Centralisé pour que toutes les vues affichent un même montant à l'identique : les pages
 * caisse utilisaient auparavant soit Intl.NumberFormat, soit un .toFixed(2) brut, ce qui
 * produisait « 1 234,50 € » ici et « 1234.50 € » là pour la même valeur.
 *
 * Le format suit la langue de l'interface : un utilisateur en anglais lit « 1,234.50 » et
 * « 29/07/2026 » selon sa convention, pas celle du français. Les formateurs Intl sont coûteux
 * à construire, on les garde en cache par locale.
 */

import i18n from '../i18n';

/**
 * Locale de formatage pour une langue d'interface.
 * L'anglais est rendu en en-GB et non en-US : l'application est belge, la date s'y écrit
 * jour/mois comme dans les deux autres langues.
 */
const LOCALES = {
  fr: 'fr-BE',
  en: 'en-GB',
  nl: 'nl-BE',
};

const currentLocale = () => LOCALES[i18n.language?.split('-')[0]] ?? LOCALES.fr;

/** Formateurs mémorisés par locale : `new Intl.NumberFormat` à chaque montant est coûteux. */
const formatterCache = new Map();

const formatter = (kind, options) => {
  const locale = currentLocale();
  const key = `${kind}:${locale}`;
  let cached = formatterCache.get(key);
  if (!cached) {
    cached = kind === 'date' || kind === 'time'
      ? new Intl.DateTimeFormat(locale, options)
      : new Intl.NumberFormat(locale, options);
    formatterCache.set(key, cached);
  }
  return cached;
};

/** Montant formaté sans symbole : « 1 234,50 ». */
export const formatAmount = (amount) =>
  formatter('amount', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    .format(Number(amount) || 0);

/** Montant formaté avec le symbole, placé selon la convention de la langue. */
export const formatCurrency = (amount) =>
  formatter('currency', { style: 'currency', currency: 'EUR' }).format(Number(amount) || 0);

/** Montant compacté pour les graduations d'axe : « 1,2 k ». Illisible en dessous. */
export const formatCompactCurrency = (amount) =>
  formatter('compact', { notation: 'compact', maximumFractionDigits: 1 })
    .format(Number(amount) || 0);

/**
 * Part exprimée en pourcentage à partir d'un ratio 0–1 : « 68 % ».
 * Arrondi à l'entier — au dixième près, un taux de tableau de bord donne une fausse
 * impression de précision et allonge inutilement le libellé.
 */
export const formatPercent = (ratio) =>
  formatter('percent', { style: 'percent', maximumFractionDigits: 0 }).format(Number(ratio) || 0);

/** Ratio borné à [0, 1], 0 si le dénominateur est nul ou absent. */
export const safeRatio = (part, whole) => {
  const total = Number(whole) || 0;
  if (total <= 0) return 0;
  return Math.min(1, Math.max(0, (Number(part) || 0) / total));
};

export const formatTime = (iso) =>
  iso ? formatter('time', { hour: '2-digit', minute: '2-digit' }).format(new Date(iso)) : '—';

export const formatDate = (iso) => (iso ? formatter('date').format(new Date(iso)) : '—');

/** Date du jour au format attendu par <input type="date"> (YYYY-MM-DD), en heure locale. */
export const todayISO = () => {
  const now = new Date();
  const offsetMs = now.getTimezoneOffset() * 60 * 1000;
  return new Date(now.getTime() - offsetMs).toISOString().split('T')[0];
};

/** Les formateurs figent leur locale : un changement de langue doit les invalider. */
i18n.on('languageChanged', () => formatterCache.clear());
