import { describe, expect, it } from 'vitest';
import {
  clampDiscount,
  computeItemsGross,
  computeItemsTotal,
  computeLineTotal,
  orderNetAmount,
  orderPayableAmount,
} from './orderTotals';

describe('clampDiscount', () => {
  it('borne la remise entre 0 et 100 %', () => {
    expect(clampDiscount(-5)).toBe(0);
    expect(clampDiscount(150)).toBe(100);
    expect(clampDiscount('12.5')).toBe(12.5);
  });

  it('traite une saisie invalide comme 0', () => {
    expect(clampDiscount('abc')).toBe(0);
    expect(clampDiscount(undefined)).toBe(0);
  });
});

describe('montants de ligne', () => {
  it('applique la remise de ligne au montant brut', () => {
    expect(computeLineTotal({ unitPrice: 20, quantity: 3, discount: 10 })).toBeCloseTo(54);
  });

  it('additionne les lignes du panier, brut et net', () => {
    const items = [
      { unitPrice: 10, quantity: 2, discount: 0 },
      { unitPrice: 5, quantity: 4, discount: 50 },
    ];
    expect(computeItemsGross(items)).toBe(40);
    expect(computeItemsTotal(items)).toBe(30);
  });

  it('renvoie 0 pour un panier absent', () => {
    expect(computeItemsTotal(null)).toBe(0);
  });
});

describe('orderNetAmount', () => {
  it('prend finalAmount quand le backend l’a calculé', () => {
    expect(orderNetAmount({ totalAmount: 100, discount: 10, finalAmount: 90 })).toBe(90);
  });

  it('retire la remise globale quand finalAmount est absent', () => {
    expect(orderNetAmount({ totalAmount: 100, discount: 15 })).toBe(85);
  });
});

describe('orderPayableAmount', () => {
  it('prend le total de la facture quand elle existe', () => {
    const order = { totalAmount: 100, finalAmount: 100, invoiceStatus: 'UNPAID', invoiceTotalAmount: 121 };
    expect(orderPayableAmount(order, 21)).toEqual({ amount: 121, estimated: false });
  });

  it('estime la TVA quand la commande n’est pas facturée', () => {
    const { amount, estimated } = orderPayableAmount({ totalAmount: 100, finalAmount: 100 }, 21);
    expect(amount).toBeCloseTo(121);
    expect(estimated).toBe(true);
  });

  it('ignore une facture annulée', () => {
    const order = { finalAmount: 50, invoiceStatus: 'CANCELED', invoiceTotalAmount: 999 };
    expect(orderPayableAmount(order, 0)).toEqual({ amount: 50, estimated: true });
  });
});
