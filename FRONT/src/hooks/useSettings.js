import { useEffect, useState } from 'react';
import api from '../services/api';
import { DEFAULT_PAYMENT_TERMS, DEFAULT_TAX_RATE } from '../constants/billing';

/**
 * Les paramètres sont stockés côté serveur et ne changent que quand on les enregistre dans
 * l'écran Paramètres. On les garde donc en mémoire pendant la session au lieu de les
 * redemander à chaque écran. Deux composants peuvent les demander en même temps sur une page
 * (tableau des commandes et panier), d'où la promesse partagée : un seul appel est fait.
 */
let cachedSettings = null;
let pendingRequest = null;

const fetchSettings = () => {
  if (cachedSettings) return Promise.resolve(cachedSettings);
  if (!pendingRequest) {
    pendingRequest = api.get('/settings')
      .then(({ data }) => {
        cachedSettings = data || {};
        return cachedSettings;
      })
      .finally(() => { pendingRequest = null; });
  }
  return pendingRequest;
};

/** À appeler après avoir enregistré les paramètres, sinon les écrans gardent l'ancien taux. */
export const invalidateSettingsCache = () => {
  cachedSettings = null;
};

/**
 * Paramètres de l'entreprise (TVA, délai de paiement, coordonnées pour le PDF).
 *
 * Trois écrans en avaient besoin et les chargeaient chacun de leur côté, deux d'entre eux
 * avec des valeurs en dur en secours. Du coup un taux changé dans les Paramètres ne
 * s'appliquait pas partout. Les valeurs par défaut ne sont plus connues que par ce hook.
 *
 * Si les paramètres ne se chargent pas, on log l'erreur et on renvoie les valeurs par défaut,
 * l'écran continue de marcher.
 */
const useSettings = () => {
  const [settings, setSettings] = useState(cachedSettings);

  useEffect(() => {
    let canceled = false;
    fetchSettings()
      .then((data) => {
        if (!canceled) setSettings(data);
      })
      .catch((error) => {
        console.warn('Réglages indisponibles, valeurs par défaut utilisées:', error);
      });
    return () => { canceled = true; };
  }, []);

  /** TVA par défaut, formatée pour un champ de saisie (« 21.00 »). */
  const defaultTaxRate = () => {
    const rate = Number(settings?.taxRate);
    return (Number.isFinite(rate) ? rate : DEFAULT_TAX_RATE).toFixed(2);
  };

  /** Échéance par défaut au format ISO : aujourd'hui + délai de paiement. */
  const defaultDueDate = () => {
    const terms = Number(settings?.paymentTerms);
    const days = Number.isFinite(terms) && terms > 0 ? terms : DEFAULT_PAYMENT_TERMS;
    return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
  };

  return { settings, defaultTaxRate, defaultDueDate };
};

export default useSettings;
