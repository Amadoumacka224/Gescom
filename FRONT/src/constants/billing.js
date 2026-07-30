/**
 * Valeurs de facturation par défaut, appliquées uniquement lorsque les réglages de l'entreprise
 * sont indisponibles. Elles doivent rester alignées sur `Settings.java` — c'est ce jeu de
 * valeurs que le serveur persiste au premier démarrage.
 *
 * Un seul endroit les définit : elles étaient auparavant recopiées dans chaque écran de
 * facturation, si bien qu'un taux modifié dans Réglages ne s'appliquait qu'à une partie des
 * factures.
 */

/** Taux de TVA standard en Belgique. */
export const DEFAULT_TAX_RATE = 21;

/** Délai de paiement en jours. */
export const DEFAULT_PAYMENT_TERMS = 30;
