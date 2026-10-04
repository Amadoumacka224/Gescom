/**
 * Une ligne de totaux (sous-total, remise, TVA, total…) dans le détail d'une facture
 * ou d'une commande.
 *
 * `emphasis` : met en avant un montant important (total, reste à payer).
 * `tone` : couleur pour un montant particulier (remise, paiement reçu).
 */
const AmountRow = ({ label, value, emphasis = false, tone = '' }) => (
  <div className={`flex items-baseline justify-between gap-6 px-4 ${emphasis ? 'py-3' : 'py-2.5'}`}>
    <span
      className={emphasis
        ? 'text-sm font-semibold text-gray-900 dark:text-gray-100'
        : 'text-sm text-gray-600 dark:text-gray-400'}
    >
      {label}
    </span>
    <span
      className={`tabular-nums ${emphasis ? 'text-base font-bold' : 'text-sm font-medium'} ${
        tone || 'text-gray-900 dark:text-gray-100'
      }`}
    >
      {value}
    </span>
  </div>
);

export default AmountRow;
