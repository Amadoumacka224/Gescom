/**
 * Formatage des montants, dates et heures, commun à toute l'appli.
 *
 * Tout est ici pour qu'un même montant s'affiche pareil partout : avant, les pages de caisse
 * utilisaient soit Intl.NumberFormat, soit un .toFixed(2), ce qui donnait « 1 234,50 € » à
 * un endroit et « 1234.50 € » à un autre.
 *
 * Le format dépend de la langue de l'interface : en anglais on a « 1,234.50 » et
 * « 29/07/2026 », pas le format français. Créer un formateur Intl coûte cher, donc on les
 * garde en cache par locale.
 */

import i18n from '../i18n';

/**
 * Locale à utiliser pour une langue de l'interface.
 * L'anglais utilise en-GB et pas en-US : l'appli est belge, la date s'écrit jour/mois comme
 * dans les deux autres langues.
 */
const LOCALES = {
  fr: 'fr-BE',
  en: 'en-GB',
  nl: 'nl-BE',
};

const currentLocale = () => LOCALES[i18n.language?.split('-')[0]] ?? LOCALES.fr;

/** Formateurs gardés en cache par locale (un `new Intl.NumberFormat` par montant coûte cher). */
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

/** Montant raccourci pour les axes des graphiques : « 1,2 k ». Pas lisible pour les petits montants. */
export const formatCompactCurrency = (amount) =>
  formatter('compact', { notation: 'compact', maximumFractionDigits: 1 })
    .format(Number(amount) || 0);

/**
 * Pourcentage à partir d'un ratio entre 0 et 1 : « 68 % ».
 * Arrondi à l'unité : avec une décimale, ça donne une impression de précision qui ne sert à
 * rien et ça allonge le texte.
 */
export const formatPercent = (ratio) =>
  formatter('percent', { style: 'percent', maximumFractionDigits: 0 }).format(Number(ratio) || 0);

/** Ratio limité entre 0 et 1, 0 si le diviseur est nul ou absent. */
export const safeRatio = (part, whole) => {
  const total = Number(whole) || 0;
  if (total <= 0) return 0;
  return Math.min(1, Math.max(0, (Number(part) || 0) / total));
};

export const formatTime = (iso) =>
  iso ? formatter('time', { hour: '2-digit', minute: '2-digit' }).format(new Date(iso)) : '—';

export const formatDate = (iso) => (iso ? formatter('date').format(new Date(iso)) : '—');

/** Date du jour au format de <input type="date"> (YYYY-MM-DD), en heure locale. */
export const todayISO = () => {
  const now = new Date();
  const offsetMs = now.getTimezoneOffset() * 60 * 1000;
  return new Date(now.getTime() - offsetMs).toISOString().split('T')[0];
};

/**
 * Nom court d'un produit : on enlève ce qui est entre parenthèses.
 *
 * Les parenthèses contiennent des détails techniques (« Armoire 2 portes (chêne massif,
 * 90 × 45 cm) ») qui allongent le nom sans aider à reconnaître l'article dans une liste.
 * Le nom complet reste dans l'infobulle et la fiche, et la recherche se fait toujours sur
 * lui : on peut trouver un article en tapant une partie qui n'est plus affichée.
 *
 * La boucle gère les parenthèses imbriquées, qu'une seule regex `[^()]*` n'enlèverait qu'à
 * moitié. Si le nom est entièrement entre parenthèses, on garde le nom d'origine (mieux vaut
 * un nom long qu'un nom vide).
 */
export const productShortName = (name) => {
  const full = String(name ?? '');
  let short = full;
  let previous;
  do {
    previous = short;
    short = short.replace(/\s*\([^()]*\)/g, ' ');
  } while (short !== previous);
  return short.replace(/\s{2,}/g, ' ').trim() || full;
};

/** Les formateurs gardent leur locale : il faut les vider quand la langue change. */
i18n.on('languageChanged', () => formatterCache.clear());
