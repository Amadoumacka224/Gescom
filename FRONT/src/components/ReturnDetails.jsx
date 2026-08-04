import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Banknote, Boxes, Download, FileText, Package, Repeat, ShoppingCart, User } from 'lucide-react';
import Button from './Button';
import InfoRow from './InfoRow';
import StatCard from './StatCard';
import useSettings from '../hooks/useSettings';
import { generateCreditNotePDF } from '../utils/pdfGenerator';
import { formatCurrency, formatDate, formatTime } from '../utils/format';

/* Teinte du traitement : ce qui rend l'argent est signalé, la remise en stock est l'ordinaire. */
const TREATMENT_TONE = {
  RESTOCK: 'badge-success',
  REFUND: 'badge-warning',
  EXCHANGE: 'badge-accent',
};

/**
 * Fiche d'un retour enregistré : le document tel qu'il a été validé.
 *
 * Purement consultative — un retour ne se modifie pas. Corriger une erreur passe par les
 * opérations de stock (ajustement, sortie), qui laissent elles aussi leur trace dans le grand
 * livre plutôt que de réécrire l'historique.
 */
const ReturnDetails = ({ stockReturn, loading }) => {
  const { t } = useTranslation();
  const { settings } = useSettings();

  /* L'avoir n'a de sens que s'il y a de l'argent à rendre : une remise en stock ou un échange
   * reprend de la marchandise sans mouvement financier, il n'y a alors rien à créditer. */
  const refundable = Number(stockReturn?.refundAmount) > 0;

  const handleCreditNote = () => {
    try {
      // Les coordonnées de l'entreprise viennent des Réglages ; leur absence ne bloque pas
      // l'édition, le générateur applique ses propres valeurs par défaut.
      generateCreditNotePDF(stockReturn, settings || {});
    } catch (error) {
      console.error('Error generating credit note:', error);
      toast.error(t('stock.returns.creditNoteError'));
    }
  };

  if (loading || !stockReturn) {
    return (
      <div className="space-y-4" aria-busy="true">
        <div className="h-24 rounded-xl bg-gray-100 dark:bg-gray-700/40 animate-pulse" />
        <div className="h-40 rounded-xl bg-gray-100 dark:bg-gray-700/40 animate-pulse" />
      </div>
    );
  }

  const items = stockReturn.items || [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3 -mt-1">
        <p className="text-sm text-gray-500 dark:text-gray-400">
          {formatDate(stockReturn.createdAt)} · {formatTime(stockReturn.createdAt)}
          {stockReturn.createdBy
            ? ` · ${
                `${stockReturn.createdBy.firstName || ''} ${stockReturn.createdBy.lastName || ''}`.trim() ||
                stockReturn.createdBy.username
              }`
            : ''}
        </p>
        {/* Le titre porté par l'enveloppe et non par le bouton : un bouton désactivé ne reçoit
            pas les événements de survol, l'explication ne s'afficherait jamais. */}
        <span title={refundable ? undefined : t('stock.returns.creditNoteNoRefund')}>
          <Button
            variant="secondary"
            size="sm"
            icon={Download}
            disabled={!refundable}
            onClick={handleCreditNote}
          >
            {t('stock.returns.creditNote')}
          </Button>
        </span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <StatCard
          title={t('stock.returns.columnQuantity')}
          value={stockReturn.totalQuantity ?? 0}
          subtitle={t('stock.returns.lineCount', { count: items.length })}
          icon={Boxes}
          tone="info"
        />
        <StatCard
          title={t('stock.returns.refundTotal')}
          value={formatCurrency(stockReturn.refundAmount)}
          subtitle={t('stock.returns.refundHint')}
          icon={Banknote}
          tone={Number(stockReturn.refundAmount) > 0 ? 'warning' : 'neutral'}
        />
      </div>

      <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <InfoRow icon={ShoppingCart} label={t('stock.returns.columnOrder')} value={stockReturn.orderNumber} />
        <InfoRow icon={FileText} label={t('stock.returns.columnInvoice')} value={stockReturn.invoiceNumber} />
        <InfoRow icon={User} label={t('stock.returns.columnClient')} value={stockReturn.clientName} />
        <InfoRow icon={Package} label={t('stock.returns.notesLabel')} value={stockReturn.notes} />
      </dl>

      <div>
        <h3 className="section-title mb-3">{t('stock.returns.articlesTitle')}</h3>
        <div className="space-y-2">
          {items.map((item) => (
            <div
              key={item.id}
              className="flex flex-wrap items-start justify-between gap-3 rounded-xl border border-gray-200 dark:border-gray-700 p-4"
            >
              <div className="min-w-0 space-y-1">
                <p className="font-medium text-gray-900 dark:text-gray-100">{item.product?.name || '—'}</p>
                <p className="text-xs text-gray-500 dark:text-gray-400">
                  {item.product?.code || '—'} · {t(`stock.returns.reasons.${item.reason}`, { defaultValue: item.reason })}
                </p>
                {item.replacementProduct && (
                  <p className="inline-flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                    <Repeat className="w-3.5 h-3.5" aria-hidden="true" />
                    {t('stock.returns.replacedBy', { product: item.replacementProduct.name })}
                  </p>
                )}
              </div>
              <div className="flex items-center gap-3 shrink-0">
                <span className={TREATMENT_TONE[item.treatment] || 'badge-neutral'}>
                  {t(`stock.returns.treatments.${item.treatment}`, { defaultValue: item.treatment })}
                </span>
                <div className="text-right tabular-nums">
                  <div className="font-semibold text-gray-900 dark:text-gray-100">
                    {item.quantity} × {formatCurrency(item.unitPrice)}
                  </div>
                  {Number(item.refundAmount) > 0 && (
                    <div className="text-xs text-amber-600 dark:text-amber-400">
                      {t('stock.returns.refundLine', { amount: formatCurrency(item.refundAmount) })}
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default ReturnDetails;
