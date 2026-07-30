/**
 * Classe une liste d'éléments par pertinence vis-à-vis d'une requête, pour alimenter
 * les suggestions d'autocomplétion (composants SearchBox / SearchableSelect).
 *
 * On ordonne du plus pertinent au moins pertinent et on ne garde qu'un petit nombre
 * de résultats.
 *
 * Barème (le meilleur champ l'emporte) :
 *   - égalité exacte          : 100
 *   - commence par la requête :  60
 *   - début d'un mot          :  40
 *   - sous-chaîne quelconque  :  20
 *   - proche (faute de frappe):  5 à 15  (distance de Levenshtein bornée)
 * Le 1er champ renvoyé par `getFields` est considéré comme principal (nom) et pèse
 * davantage (×1,5), afin qu'une correspondance dans le nom prime sur une
 * correspondance dans un champ secondaire (référence, code-barres, e-mail…).
 *
 * Robustesse :
 *   - insensible à la casse ET aux accents (« Amelie » trouve « Amélie ») ;
 *   - tolérant aux fautes de frappe (« prodiut », « briqette »…) via une distance
 *     d'édition bornée, calculée uniquement en dernier recours (perf).
 *
 * @param {Array}    items     éléments à classer
 * @param {string}   query     texte saisi par l'utilisateur
 * @param {Function} getFields (item) => string[] champs à comparer (nom en 1er)
 * @param {number}   limit     nombre maximum de suggestions retournées
 * @returns {Array} sous-ensemble de `items` trié par pertinence décroissante
 */

// Minuscule + suppression des diacritiques (é → e, ç → c…) pour une recherche
// insensible aux accents.
export const normalizeText = (s) =>
  String(s)
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '');

// Alias interne court, utilisé dans tout ce module.
const normalize = normalizeText;

// Distance de Levenshtein bornée : s'arrête dès que le minimum d'une ligne dépasse
// `max` (retourne alors max + 1). Évite le coût du calcul complet quand les chaînes
// sont manifestement trop éloignées.
const boundedLevenshtein = (a, b, max) => {
  const al = a.length;
  const bl = b.length;
  if (Math.abs(al - bl) > max) return max + 1;
  if (al === 0) return bl;
  if (bl === 0) return al;

  let prev = new Array(bl + 1);
  let curr = new Array(bl + 1);
  for (let j = 0; j <= bl; j++) prev[j] = j;

  for (let i = 1; i <= al; i++) {
    curr[0] = i;
    let rowMin = curr[0];
    for (let j = 1; j <= bl; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      curr[j] = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost);
      if (curr[j] < rowMin) rowMin = curr[j];
    }
    if (rowMin > max) return max + 1; // arrêt anticipé
    const tmp = prev;
    prev = curr;
    curr = tmp;
  }
  return prev[bl];
};

// Tolérance aux fautes de frappe : compare la requête au champ entier et à chacun de
// ses mots (ex. « briqette » ≈ « briquette » dans « Briquette lait »). Le seuil dépend
// de la longueur de la requête (plus elle est courte, moins on tolère d'écart).
const fuzzyScore = (field, q) => {
  if (q.length < 3) return 0; // en dessous, une « faute » n'a pas de sens
  const maxDist = q.length <= 4 ? 1 : q.length <= 7 ? 2 : 3;

  let best = Infinity;
  for (const candidate of [field, ...field.split(/\s+/)]) {
    if (Math.abs(candidate.length - q.length) > maxDist) continue;
    const d = boundedLevenshtein(q, candidate, maxDist);
    if (d < best) best = d;
    if (best === 1) break;
  }
  if (best > maxDist) return 0;
  // Reste sous le palier « sous-chaîne » (20) : une vraie correspondance prime toujours.
  return Math.max(5, 15 - (best - 1) * 5); // dist 1 → 15, dist 2 → 10, dist 3 → 5
};

export const rankSuggestions = (items = [], query = '', getFields, limit = 8) => {
  const q = normalize(query.trim());
  if (!q) return items.slice(0, limit);

  const scoreField = (field) => {
    if (field === q) return 100;
    if (field.startsWith(q)) return 60;
    // Début d'un autre mot, ex. "lait" dans "Briquette lait demi-écrémé".
    if (field.split(/\s+/).some((word) => word.startsWith(q))) return 40;
    if (field.includes(q)) return 20;
    return fuzzyScore(field, q);
  };

  const scored = [];
  for (const item of items) {
    const fields = getFields(item)
      .filter(Boolean)
      .map(normalize);

    let best = 0;
    fields.forEach((field, idx) => {
      const weight = idx === 0 ? 1.5 : 1; // le champ principal (nom) prime
      const s = scoreField(field) * weight;
      if (s > best) best = s;
    });

    if (best > 0) scored.push({ item, best });
  }

  scored.sort((a, b) => b.best - a.best);
  return scored.slice(0, limit).map((s) => s.item);
};
