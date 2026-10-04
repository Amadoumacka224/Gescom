/**
 * Une ligne de totaux : sous-total, remise, TVA, total, reste à payer.
 *
 * Utilisée dans le détail d'une facture et d'une commande pour qu'ils aient le même rendu
 * (mêmes graisses, montants alignés). `emphasis` est à garder pour les montants vraiment
 * importants (total, reste à payer), sinon plus rien ne ressort.
 *
 * `tone` sert seulement pour les montants qui ont un sens (une remise en moins, un paiement
 * déjà reçu), pas pour les lignes neutres.
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
