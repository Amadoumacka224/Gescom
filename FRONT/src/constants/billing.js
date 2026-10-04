/**
 * Valeurs de facturation par défaut, utilisées seulement si les paramètres de l'entreprise
 * ne sont pas disponibles. Elles doivent rester les mêmes que dans `Settings.java` (ce sont
 * les valeurs enregistrées par le serveur au premier démarrage).
 *
 * Elles sont définies ici une seule fois. Avant, chaque écran de facturation avait sa copie,
 * et un taux changé dans les Paramètres ne s'appliquait pas partout.
 */

/** Taux de TVA standard en Belgique. */
export const DEFAULT_TAX_RATE = 21;

/** Délai de paiement en jours. */
export const DEFAULT_PAYMENT_TERMS = 30;
