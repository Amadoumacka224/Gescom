/**
 * Ligne d'un bloc de totaux : sous-total, remise, TVA, total, reste à payer.
 *
 * Partagée par le détail d'une facture et celui d'une commande, dont les récapitulatifs doivent
 * se lire de la même façon — mêmes graisses, même alignement des montants sur une colonne unique.
 * `emphasis` est réservé aux deux ou trois chiffres qui décident de l'action à mener (total,
 * reste à payer) : tout mettre en avant reviendrait à ne rien mettre en avant.
 *
 * `tone` n'habille que les montants qui portent une nature — une remise se retranche, un
 * règlement déjà encaissé s'ajoute — jamais les lignes neutres.
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
