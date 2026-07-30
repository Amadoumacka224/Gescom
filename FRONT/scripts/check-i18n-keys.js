/**
 * Vérifie que chaque clé passée à `t('…')` dans les sources existe dans le catalogue de
 * référence (fr.json), et signale les clés du catalogue que plus aucun écran n'utilise.
 *
 * Complète `check-i18n.js`, qui ne compare que les catalogues entre eux : la parité fr/en/nl
 * ne dit rien d'un `t('orders.tittle')` mal orthographié, qui s'affiche alors en clé brute.
 *
 * Les clés construites dynamiquement (`t(\`status.order.${key}\`)`) sont vérifiées par préfixe :
 * on exige qu'au moins une clé du catalogue commence par la partie fixe.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const srcDir = path.join(here, '..', 'src');
const reference = JSON.parse(
  fs.readFileSync(path.join(srcDir, 'i18n', 'locales', 'fr.json'), 'utf8')
);

/** Aplatit le catalogue en un ensemble de clés pointées. */
const flatten = (node, prefix = '', out = new Set()) => {
  for (const [key, value] of Object.entries(node)) {
    const full = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === 'object') flatten(value, full, out);
    else out.add(full);
  }
  return out;
};

const known = flatten(reference);

/**
 * Une clé à pluriel est stockée sous ses formes suffixées (`itemCount_one`, `itemCount_other`)
 * et jamais sous son nom nu : `t('orders.cart.itemCount', { count })` est donc valide même si
 * `orders.cart.itemCount` est absent du catalogue.
 */
const PLURAL_SUFFIXES = ['zero', 'one', 'two', 'few', 'many', 'other'];
const resolves = (key) =>
  known.has(key) || PLURAL_SUFFIXES.some((suffix) => known.has(`${key}_${suffix}`));

const sourceFiles = [];
const walk = (dir) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== 'i18n') walk(full);
    } else if (/\.jsx?$/.test(entry.name)) {
      sourceFiles.push(full);
    }
  }
};
walk(srcDir);

const missing = [];
const used = new Set();

// t('clé') / t("clé") / i18nKey="clé" — clés littérales.
const literalPattern = /(?:\bt\(\s*['"]([\w.-]+)['"]|i18nKey=["']([\w.-]+)["'])/g;
// t(`préfixe.${…}`) — clés dynamiques, vérifiées sur leur partie fixe.
const dynamicPattern = /\bt\(\s*`([\w.-]*?)\$\{/g;

for (const file of sourceFiles) {
  const text = fs.readFileSync(file, 'utf8');
  const relative = path.relative(srcDir, file);

  for (const match of text.matchAll(literalPattern)) {
    const key = match[1] ?? match[2];
    used.add(key);
    if (!resolves(key)) missing.push(`${relative}: t('${key}')`);
  }

  for (const match of text.matchAll(dynamicPattern)) {
    const prefix = match[1];
    if (!prefix) continue;
    const hits = [...known].filter((k) => k.startsWith(prefix));
    if (hits.length === 0) missing.push(`${relative}: t(\`${prefix}\${…}\`) — aucun préfixe`);
    hits.forEach((k) => used.add(k));
  }
}

// Les clés servant de valeur par défaut (labelKey, i18nKey en constante) sont référencées
// comme littéraux ailleurs que dans un t() : on les récupère par recherche brute.
for (const file of sourceFiles) {
  const text = fs.readFileSync(file, 'utf8');
  for (const match of text.matchAll(/\b\w*[lL]abelKey:\s*['"]([\w.-]+\.[\w.-]+)['"]/g)) {
    used.add(match[1]);
    if (!resolves(match[1])) {
      missing.push(`${path.relative(srcDir, file)}: labelKey '${match[1]}'`);
    }
  }
  for (const match of text.matchAll(/return\s+['"]([\w]+\.[\w.]+)['"]/g)) used.add(match[1]);
  for (const match of text.matchAll(/`([\w.-]+)\.\$\{/g)) {
    [...known].filter((k) => k.startsWith(match[1])).forEach((k) => used.add(k));
  }
}

const unused = [...known].filter((k) => !used.has(k)).sort();

if (missing.length > 0) {
  console.error(`\n${missing.length} clé(s) manquante(s) dans fr.json :`);
  missing.forEach((m) => console.error(`  ${m}`));
}

// Les clés orphelines ne cassent rien : on ne les liste que sur demande (`--unused`),
// et la détection reste approximative (une clé construite dynamiquement peut passer pour
// inutilisée). À lire comme une piste de nettoyage, pas comme un verdict.
if (process.argv.includes('--unused') && unused.length > 0) {
  console.log(`\n${unused.length} clé(s) du catalogue sans usage détecté :`);
  unused.forEach((k) => console.log(`  ${k}`));
}

if (missing.length > 0) process.exit(1);
console.log(`\nToutes les clés utilisées existent (${used.size} référencée(s)).`);
