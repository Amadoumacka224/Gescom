import { afterEach, describe, expect, it } from 'vitest';
import i18n from '../i18n';
import { formatAmount, productShortName, safeRatio } from './format';

// Intl sépare les milliers par une espace insécable (fine ou non selon la locale) ; NFKC la
// ramène à une espace ordinaire.
const plain = (text) => text.normalize('NFKC');

describe('productShortName', () => {
  it('retire les précisions entre parenthèses, même imbriquées', () => {
    expect(productShortName('Armoire 2 portes (chêne massif, 90 × 45 cm)')).toBe('Armoire 2 portes');
    expect(productShortName('Lampe (abat-jour (lin) beige) LED')).toBe('Lampe LED');
  });

  it('garde le nom complet s’il est entièrement entre parenthèses', () => {
    expect(productShortName('(sans nom)')).toBe('(sans nom)');
  });
});

describe('safeRatio', () => {
  it('borne le ratio entre 0 et 1 et gère un total nul', () => {
    expect(safeRatio(1, 4)).toBe(0.25);
    expect(safeRatio(10, 4)).toBe(1);
    expect(safeRatio(3, 0)).toBe(0);
  });
});

describe('formatAmount', () => {
  afterEach(() => i18n.changeLanguage('fr'));

  it('suit la langue de l’interface', async () => {
    await i18n.changeLanguage('fr');
    expect(plain(formatAmount(1234.5))).toBe('1 234,50');

    await i18n.changeLanguage('en');
    expect(formatAmount(1234.5)).toBe('1,234.50');
  });
});
