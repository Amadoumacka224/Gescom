import { describe, expect, it } from 'vitest';
import { EMPTY_CLIENT_FORM, buildClientPayload, isClientFormDirty, validateClient } from './clientForm';

// Renvoie la clé de traduction : les tests vérifient quelle règle a sauté, pas le texte.
const t = (key) => key;

const validClient = {
  ...EMPTY_CLIENT_FORM,
  firstName: 'Anne',
  lastName: 'Martin',
  phone: '+32 470 12 34 56',
};

describe('validateClient', () => {
  it('accepte un particulier complet', () => {
    expect(validateClient(validClient, t)).toEqual({});
  });

  it('exige le prénom, le nom et le téléphone', () => {
    expect(validateClient(EMPTY_CLIENT_FORM, t)).toEqual({
      firstName: 'clients.errorFirstNameRequired',
      lastName: 'clients.errorLastNameRequired',
      phone: 'clients.errorPhoneRequired',
    });
  });

  it('refuse un téléphone ou un e-mail mal formé', () => {
    const errors = validateClient({ ...validClient, phone: 'abc', email: 'anne@' }, t);
    expect(errors.phone).toBe('clients.errorPhoneFormat');
    expect(errors.email).toBe('clients.errorEmailFormat');
  });

  it('exige la raison sociale pour une entreprise seulement', () => {
    expect(validateClient({ ...validClient, type: 'ENTREPRISE' }, t).company)
      .toBe('clients.errorCompanyRequired');
    expect(validateClient(validClient, t).company).toBeUndefined();
  });

  it('applique les longueurs maximales du backend', () => {
    expect(validateClient({ ...validClient, postalCode: '1'.repeat(21) }, t).postalCode)
      .toBe('clients.errorMaxLength');
  });
});

describe('buildClientPayload', () => {
  it('nettoie les espaces, met les champs facultatifs vides à null et l’e-mail en minuscules', () => {
    const payload = buildClientPayload({ ...validClient, firstName: '  Anne ', email: ' Anne@Example.BE ', city: '  ' });
    expect(payload.firstName).toBe('Anne');
    expect(payload.email).toBe('anne@example.be');
    expect(payload.city).toBeNull();
    expect(payload.company).toBeNull();
  });
});

describe('isClientFormDirty', () => {
  it('détecte une modification depuis l’ouverture', () => {
    expect(isClientFormDirty(validClient, validClient)).toBe(false);
    expect(isClientFormDirty({ ...validClient, city: 'Liège' }, validClient)).toBe(true);
  });
});
