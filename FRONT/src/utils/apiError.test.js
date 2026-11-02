import { describe, expect, it } from 'vitest';
import { extractErrorMessage } from './apiError';

describe('extractErrorMessage', () => {
  it('lit le message d’une erreur métier du backend', () => {
    expect(extractErrorMessage({ response: { data: { message: 'Stock insuffisant' } } })).toBe('Stock insuffisant');
  });

  it('prend la première erreur de validation', () => {
    const error = { response: { data: { errors: { email: ['E-mail invalide'], phone: 'x' } } } };
    expect(extractErrorMessage(error)).toBe('E-mail invalide');
  });

  it('accepte une réponse texte brute', () => {
    expect(extractErrorMessage({ response: { data: 'Erreur serveur' } })).toBe('Erreur serveur');
  });

  it('n’affiche jamais [object Object]', () => {
    expect(extractErrorMessage({ response: { data: {} } })).not.toContain('[object Object]');
  });
});
