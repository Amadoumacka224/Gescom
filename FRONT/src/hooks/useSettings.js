import { useEffect, useState } from 'react';
import api from '../services/api';
import { DEFAULT_PAYMENT_TERMS, DEFAULT_TAX_RATE } from '../constants/billing';

/**
 * Les réglages sont un singleton persistant côté serveur et ne changent qu'à l'enregistrement
 * dans l'écran Réglages : on les garde en mémoire pour la durée de la session plutôt que de
 * les redemander à chaque écran. Deux consommateurs peuvent cohabiter sur une même page (le
 * tableau de bord des commandes et son atelier), d'où la promesse partagée : la requête n'est
 * émise qu'une fois même si les deux montent simultanément.
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

/** À appeler après un enregistrement des réglages, sinon les écrans gardent l'ancien taux. */
export const invalidateSettingsCache = () => {
  cachedSettings = null;
};

/**
 * Réglages de l'entreprise (taux de TVA, délai de paiement, coordonnées du PDF).
 *
 * Trois écrans en avaient besoin et les récupéraient chacun de leur côté, deux d'entre eux
 * en se rabattant sur des constantes en dur : un taux modifié dans Réglages ne s'appliquait
 * alors qu'à une partie des factures. Le repli reste local à ce hook, seul endroit à connaître
 * les valeurs par défaut.
 *
 * L'indisponibilité des réglages ne bloque jamais l'écran appelant : on journalise et on rend
 * les valeurs par défaut.
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

  /** Taux de TVA par défaut, formaté pour un champ de saisie (« 21.00 »). */
  const defaultTaxRate = () => {
    const rate = Number(settings?.taxRate);
    return (Number.isFinite(rate) ? rate : DEFAULT_TAX_RATE).toFixed(2);
  };

  /** Échéance par défaut au format ISO : date du jour + délai de paiement configuré. */
  const defaultDueDate = () => {
    const terms = Number(settings?.paymentTerms);
    const days = Number.isFinite(terms) && terms > 0 ? terms : DEFAULT_PAYMENT_TERMS;
    return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
  };

  return { settings, defaultTaxRate, defaultDueDate };
};

export default useSettings;
