import { describe, expect, it } from 'vitest';
import { resolveOrderStatusKey } from './statusBadges';

describe('resolveOrderStatusKey', () => {
  it('affiche le paiement pour une commande facturée', () => {
    expect(resolveOrderStatusKey({ status: 'INVOICED', invoiceStatus: 'PAID' })).toBe('PAID');
    expect(resolveOrderStatusKey({ status: 'INVOICED', invoiceStatus: 'PARTIALLY_PAID' })).toBe('PARTIALLY_PAID');
    expect(resolveOrderStatusKey({ status: 'INVOICED', invoiceStatus: 'UNPAID' })).toBe('INVOICED');
  });

  it('préfère la facture chargée à part au statut porté par la liste', () => {
    expect(resolveOrderStatusKey({ status: 'INVOICED', invoiceStatus: 'UNPAID' }, { status: 'PAID' })).toBe('PAID');
  });

  it('ne laisse pas le paiement masquer une livraison', () => {
    expect(resolveOrderStatusKey({ status: 'DELIVERED', invoiceStatus: 'PAID' })).toBe('DELIVERED');
  });

  it('retombe sur PENDING pour un statut inconnu ou absent', () => {
    expect(resolveOrderStatusKey({ status: 'INCONNU' })).toBe('PENDING');
    expect(resolveOrderStatusKey(null)).toBe('PENDING');
  });
});
