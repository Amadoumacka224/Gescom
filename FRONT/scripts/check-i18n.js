#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const localesDir = join(here, '..', 'src', 'i18n', 'locales');
const reference = 'fr';
const others = ['en', 'nl'];

function flatten(obj, prefix = '') {
  const out = new Map();
  for (const [key, value] of Object.entries(obj)) {
    const full = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      for (const [k, v] of flatten(value, full)) out.set(k, v);
    } else {
      out.set(full, value);
    }
  }
  return out;
}

function load(lang) {
  const raw = readFileSync(join(localesDir, `${lang}.json`), 'utf8');
  return flatten(JSON.parse(raw));
}

const ref = load(reference);
let failed = false;

for (const lang of others) {
  const target = load(lang);
  const missing = [...ref.keys()].filter((k) => !target.has(k));
  const extra = [...target.keys()].filter((k) => !ref.has(k));
  const invalidKeys = [...target.keys()].filter((k) => /\s/.test(k));
  const empty = [...target.entries()]
    .filter(([, v]) => typeof v === 'string' && v.trim() === '')
    .map(([k]) => k);

  const problems = missing.length + extra.length + invalidKeys.length + empty.length;
  if (problems === 0) {
    console.log(`  ${lang}: OK (${target.size} keys)`);
    continue;
  }

  failed = true;
  console.log(`  ${lang}: ${problems} problem(s)`);
  if (missing.length) console.log(`    missing (${missing.length}):`, missing.join(', '));
  if (extra.length) console.log(`    extra (${extra.length}):`, extra.join(', '));
  if (invalidKeys.length) console.log(`    whitespace in key:`, invalidKeys.join(', '));
  if (empty.length) console.log(`    empty value:`, empty.join(', '));
}

const refInvalid = [...ref.keys()].filter((k) => /\s/.test(k));
if (refInvalid.length) {
  failed = true;
  console.log(`  ${reference}: whitespace in key:`, refInvalid.join(', '));
}

if (failed) {
  console.error('\ni18n parity check failed.');
  process.exit(1);
}
console.log(`\ni18n parity check passed (${reference} = ${ref.size} keys).`);
