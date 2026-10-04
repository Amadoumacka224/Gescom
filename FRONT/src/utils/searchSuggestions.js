/**
 * Trie une liste d'éléments selon leur pertinence par rapport à la recherche, pour les
 * suggestions d'autocomplétion (SearchBox / SearchableSelect).
 *
 * On trie du plus pertinent au moins pertinent et on ne garde que quelques résultats.
 *
 * Points (c'est le meilleur champ qui compte) :
 *   - identique               : 100
 *   - commence par la requête :  60
 *   - début d'un mot          :  40
 *   - contient la requête     :  20
 *   - proche (faute de frappe):  5 à 15  (distance de Levenshtein limitée)
 * Le premier champ renvoyé par `getFields` est le champ principal (le nom) et compte plus
 * (×1,5), pour qu'un résultat sur le nom passe avant un résultat sur un autre champ
 * (référence, code-barres, e-mail…).
 *
 * En plus :
 *   - on ignore les majuscules ET les accents (« Amelie » trouve « Amélie ») ;
 *   - on accepte les fautes de frappe (« prodiut », « briqette »…) avec une distance
 *     d'édition limitée, calculée seulement en dernier recours (performances).
 *
 * @param {Array}    items     éléments à trier
 * @param {string}   query     texte tapé par l'utilisateur
 * @param {Function} getFields (item) => string[] champs à comparer (le nom en premier)
 * @param {number}   limit     nombre max de suggestions renvoyées
 * @returns {Array} les éléments retenus, du plus pertinent au moins pertinent
 */

// Met en minuscules et enlève les accents (é → e, ç → c…) pour une recherche
// qui ne tient pas compte des accents.
export const normalizeText = (s) =>
  String(s)
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '');

// Petit alias utilisé dans tout le fichier.
const normalize = normalizeText;

// Distance de Levenshtein limitée : on s'arrête dès que le minimum d'une ligne dépasse
// `max` (on renvoie alors max + 1). Ça évite de tout calculer quand les deux chaînes
// sont de toute façon trop différentes.
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

// Fautes de frappe : on compare la requête au champ entier et à chacun de ses mots
// (ex. « briqette » ≈ « briquette » dans « Briquette lait »). La tolérance dépend de la
// longueur de la requête (plus elle est courte, moins on accepte d'erreurs).
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
  // On reste sous le score « contient » (20) : une vraie correspondance passe toujours avant.
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
