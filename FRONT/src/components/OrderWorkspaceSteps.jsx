import { useState } from 'react';
import {
  AlertCircle, CheckCircle, CreditCard, Download, Euro, FileText, Loader2,
  Plus, RotateCcw, Save, ShoppingCart, XCircle,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { PAYMENT_METHODS } from '../constants/paymentMethods';
import { formatCurrency, todayISO } from '../utils/format';
import { remainingOf } from '../utils/orderLifecycle';

/**
 * Étapes du traitement d'une commande, affichées en bas du panier.
 *
 * À chaque moment, une commande n'a qu'une seule étape suivante possible, donc on n'affiche
 * que celle-là (valider, confirmer, facturer, encaisser) au lieu de cinq boutons dont quatre
 * renverraient une erreur 400. L'ordre suit la machine à états du backend
 * (`Order.OrderStatus.ALLOWED_TRANSITIONS`) :
 *
 *   Panier → PENDING → CONFIRMED → INVOICED → réglée
 *              (POST)  (stock −)   (facture)  (encaissements)
 *
 * Aucune règle n'est recodée ici : on affiche juste ce que le serveur vérifie de toute façon
 * (modif seulement en brouillon, facture seulement si confirmée, paiement sur la facture).
 *
 * Il y avait avant une barre de progression en haut du panneau, mais elle prenait une
 * quarantaine de pixels pour répéter ce que dit déjà le bouton. On a rendu la place aux
 * articles.
 */

/** Bouton principal de l'étape : pleine largeur, avec un état de chargement. */
const StepButton = ({ icon: Icon, label, loadingLabel, loading, disabled, onClick, title, tone = 'primary' }) => {
  const tones = {
    primary: 'bg-primary-600 hover:bg-primary-700 text-white',
    info: 'bg-blue-600 hover:bg-blue-700 text-white',
    accent: 'bg-violet-600 hover:bg-violet-700 text-white',
    success: 'bg-green-600 hover:bg-green-700 text-white',
  };
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled || loading}
      title={title}
      className={`w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl font-bold text-sm shadow-sm hover:shadow transition-all disabled:opacity-50 disabled:cursor-not-allowed ${tones[tone]}`}
    >
      {loading ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" /> : <Icon className="w-4 h-4" aria-hidden="true" />}
      {loading ? loadingLabel : label}
    </button>
  );
};

const fieldClass =
  'w-full px-2.5 py-1.5 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg text-xs font-medium text-gray-900 dark:text-gray-100 focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-200 transition-all';
const labelClass = 'block text-[11px] font-semibold text-gray-600 dark:text-gray-400 mb-1';

const OrderWorkspaceSteps = ({
  order,
  invoice,
  invoiceLoading,
  busy,
  itemCount,
  unitCount,
  totalHT,
  dirty,
  canValidate,
  blockingHint,
  defaultTaxRate,
  defaultDueDate,
  onValidate,
  onSaveItems,
  onConfirm,
  onCreateInvoice,
  onRecordPayment,
  onOpenTerminal,
  onDownloadPdf,
  onCancelOrder,
  onReset,
  onOpenInvoice,
}) => {
  const { t } = useTranslation();
  const remaining = remainingOf(invoice);
  const settled = !!invoice && (invoice.status === 'PAID' || remaining <= 0.001);

  // Tant qu'un champ n'a pas été touché il reste à `null` et prend sa valeur par défaut au
  // rendu. Comme ça le montant proposé suit le reste à payer après un acompte, et la TVA
  // suit les paramètres dès qu'ils sont chargés, sans écraser ce que l'utilisateur tape.
  const [invoiceForm, setInvoiceForm] = useState({
    invoiceDate: null,
    dueDate: null,
    paymentMethod: 'CASH',
    taxRate: null,
    discount: '',
    notes: '',
  });
  const [paymentForm, setPaymentForm] = useState({
    amount: null,
    paymentMethod: null,
    paymentDate: null,
  });

  // Valeurs par défaut prises dans les paramètres de l'entreprise (TVA, délai de paiement)
  // plutôt qu'en dur. Les retaper à chaque facture créait des différences entre caissiers.
  // La valeur de secours est déjà gérée par `useSettings`.
  const invoiceDate = invoiceForm.invoiceDate ?? todayISO();
  const dueDate = invoiceForm.dueDate ?? defaultDueDate;
  const taxRateValue = invoiceForm.taxRate ?? defaultTaxRate;

  const paymentAmount = paymentForm.amount ?? remaining.toFixed(2);
  const paymentMethod = paymentForm.paymentMethod ?? invoice?.paymentMethod ?? 'CASH';
  const paymentDate = paymentForm.paymentDate ?? todayISO();

  // Après un paiement, on vide le montant saisi pour que la proposition suivante reparte du
  // nouveau reste à payer.
  const submitPayment = async (payload) => {
    await onRecordPayment(payload);
    setPaymentForm((prev) => ({ ...prev, amount: null }));
  };

  // ── Étape 1 : brouillon, la commande n'est pas encore créée ─────────────────────────
  if (!order) {
    return (
      <div className="space-y-3">
        {blockingHint && (
          <p className="flex items-start gap-1.5 text-[11px] font-medium text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 rounded-lg p-2">
            <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-px" aria-hidden="true" />
            {blockingHint}
          </p>
        )}
        {/* L'explication (commande enregistrée en attente, stock pas touché avant la
            confirmation) est dans l'infobulle du bouton plutôt qu'en texte permanent, pour
            ne pas perdre une ligne à chaque vente. */}
        <StepButton
          icon={CheckCircle}
          label={t('orders.steps.validateOrder')}
          loadingLabel={t('orders.steps.creating')}
          loading={busy === 'create'}
          disabled={!canValidate}
          onClick={onValidate}
          title={t('orders.steps.validateHint')}
        />
      </div>
    );
  }

  // ── Commande annulée : plus rien à faire, on propose d'en recommencer une ───────────
  if (order.status === 'CANCELED') {
    return (
      <div className="space-y-3">
        <p className="flex items-center gap-2 p-2.5 bg-red-50 dark:bg-red-500/10 text-red-700 dark:text-red-300 rounded-lg text-xs font-medium">
          <XCircle className="w-4 h-4 shrink-0" aria-hidden="true" />
          {t('orders.steps.canceledNotice')}
        </p>
        <button type="button" onClick={onReset} className="btn-secondary w-full text-sm">
          <Plus className="w-4 h-4" aria-hidden="true" />
          {t('orders.addOrder')}
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {/* ── Étape 2 : commande en attente, à confirmer (sortie de stock) ─────────────── */}
      {order.status === 'PENDING' && (
        <>
          {dirty && (
            <>
              <p className="flex items-start gap-1.5 text-[11px] font-medium text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 rounded-lg p-2">
                <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-px" aria-hidden="true" />
                {t('orders.steps.unsavedChanges')}
              </p>
              <StepButton
                icon={Save}
                label={t('common.saveChanges')}
                loadingLabel={t('orders.steps.saving')}
                loading={busy === 'save'}
                disabled={!canValidate}
                onClick={onSaveItems}
                tone="info"
              />
            </>
          )}
          {!dirty && blockingHint && (
            <p className="flex items-start gap-1.5 text-[11px] font-medium text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 rounded-lg p-2">
              <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-px" aria-hidden="true" />
              {blockingHint}
            </p>
          )}
          <StepButton
            icon={CheckCircle}
            label={t('orders.steps.confirmOrder')}
            loadingLabel={t('orders.steps.confirming')}
            loading={busy === 'confirm'}
            disabled={dirty || !canValidate}
            onClick={onConfirm}
            tone="info"
          />
          <p className="text-[11px] text-center text-gray-400 dark:text-gray-500">
            {t('orders.steps.confirmHint', { units: unitCount, refs: itemCount })}
          </p>
        </>
      )}

      {/* ── Étape 3 : commande confirmée, à facturer ─────────────────────────────────── */}
      {order.status === 'CONFIRMED' && (() => {
        const rate = parseFloat(taxRateValue);
        const rateInvalid = Number.isNaN(rate) || rate < 0 || rate > 100;
        const commercial = parseFloat(invoiceForm.discount) || 0;
        // La remise de la commande s'ajoute à celle saisie ici : la facture enlève les deux
        // du sous-total (voir InvoiceService.createInvoice).
        const totalDiscount = (Number(order.discount) || 0) + commercial;
        const base = Math.max(totalHT - totalDiscount, 0);
        const tax = rateInvalid ? 0 : base * (rate / 100);
        const dueBefore = !!dueDate && !!invoiceDate && dueDate < invoiceDate;
        const discountTooLarge = totalDiscount > totalHT + 0.001;
        const invalid = rateInvalid || dueBefore || discountTooLarge || !invoiceDate || !dueDate;

        return (
          <div className="space-y-2.5">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500">
              {t('orders.steps.invoicingHeading')}
            </p>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label htmlFor="ws-invoice-date" className={labelClass}>{t('invoices.invoiceDateLabel')}</label>
                <input
                  id="ws-invoice-date"
                  type="date"
                  value={invoiceDate}
                  onChange={(e) => setInvoiceForm({ ...invoiceForm, invoiceDate: e.target.value })}
                  className={fieldClass}
                />
              </div>
              <div>
                <label htmlFor="ws-due-date" className={labelClass}>{t('orders.recap.dueOn')}</label>
                <input
                  id="ws-due-date"
                  type="date"
                  min={invoiceDate || undefined}
                  value={dueDate}
                  onChange={(e) => setInvoiceForm({ ...invoiceForm, dueDate: e.target.value })}
                  className={`${fieldClass} ${dueBefore ? 'border-red-400 focus:border-red-500 focus:ring-red-200' : ''}`}
                />
              </div>
              <div>
                <label htmlFor="ws-tax-rate" className={labelClass}>{t('orders.steps.taxRateLabel')}</label>
                <input
                  id="ws-tax-rate"
                  type="number"
                  min="0"
                  max="100"
                  step="0.01"
                  value={taxRateValue}
                  onChange={(e) => setInvoiceForm({ ...invoiceForm, taxRate: e.target.value })}
                  className={`${fieldClass} text-right ${rateInvalid ? 'border-red-400 focus:border-red-500 focus:ring-red-200' : ''}`}
                />
              </div>
              <div>
                <label htmlFor="ws-invoice-discount" className={labelClass}>{t('orders.steps.discountLabel')}</label>
                <input
                  id="ws-invoice-discount"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="0.00"
                  value={invoiceForm.discount}
                  onChange={(e) => setInvoiceForm({ ...invoiceForm, discount: e.target.value })}
                  className={`${fieldClass} text-right ${discountTooLarge ? 'border-red-400 focus:border-red-500 focus:ring-red-200' : ''}`}
                />
              </div>
              <div className="col-span-2">
                <label htmlFor="ws-payment-method" className={labelClass}>{t('orders.steps.plannedPayment')}</label>
                <select
                  id="ws-payment-method"
                  value={invoiceForm.paymentMethod}
                  onChange={(e) => setInvoiceForm({ ...invoiceForm, paymentMethod: e.target.value })}
                  className={fieldClass}
                >
                  {PAYMENT_METHODS.map((m) => (
                    <option key={m.value} value={m.value}>{t(m.labelKey)}</option>
                  ))}
                </select>
              </div>
              <div className="col-span-2">
                <label htmlFor="ws-invoice-notes" className={labelClass}>{t('orders.steps.invoiceMention')}</label>
                <input
                  id="ws-invoice-notes"
                  type="text"
                  maxLength={500}
                  value={invoiceForm.notes}
                  onChange={(e) => setInvoiceForm({ ...invoiceForm, notes: e.target.value })}
                  placeholder={t('orders.steps.optional')}
                  className={fieldClass}
                />
              </div>
            </div>

            {/* Aperçu du calcul, dans le même ordre que `InvoiceService.createInvoice` :
                HT − remise, TVA sur le net, puis TTC. */}
            <div className="flex items-center justify-between text-xs px-0.5">
              <span className="text-gray-500 dark:text-gray-400">{t('orders.steps.amountToInvoice')}</span>
              <span className="font-bold text-violet-700 dark:text-violet-300 tabular-nums">
                {invalid ? '—' : formatCurrency(base + tax)}
              </span>
            </div>

            {dueBefore && (
              <p className="text-[11px] text-red-600 dark:text-red-400">
                {t('orders.steps.dueBeforeInvoice')}
              </p>
            )}
            {discountTooLarge && (
              <p className="text-[11px] text-red-600 dark:text-red-400">
                {t('orders.steps.discountTooLarge')}
              </p>
            )}

            <StepButton
              icon={Euro}
              label={t('orders.steps.createInvoice')}
              loadingLabel={t('orders.steps.creating')}
              loading={busy === 'invoice'}
              disabled={invalid}
              onClick={() => onCreateInvoice({
                invoiceDate,
                dueDate,
                paymentMethod: invoiceForm.paymentMethod,
                taxRate: rate,
                discount: commercial > 0 ? commercial : null,
                notes: invoiceForm.notes.trim() || null,
              })}
              tone="accent"
            />
          </div>
        );
      })()}

      {/* ── Étape 4 : facturée, paiement à encaisser ─────────────────────────────────── */}
      {['INVOICED', 'DELIVERED'].includes(order.status) && (
        <>
          {invoiceLoading && !invoice && (
            <p className="text-center text-xs text-gray-500 dark:text-gray-400 py-2">
              {t('orders.recap.loadingInvoice')}
            </p>
          )}

          {invoice && invoice.status === 'CANCELED' && (
            <p className="flex items-center gap-2 p-2.5 bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 rounded-lg text-xs font-medium">
              <XCircle className="w-4 h-4 shrink-0" aria-hidden="true" />
              {t('orders.steps.invoiceCanceledNotice')}
            </p>
          )}

          {invoice && invoice.status !== 'CANCELED' && !settled && (() => {
            const amount = parseFloat(paymentAmount);
            const entered = paymentAmount !== '' && !Number.isNaN(amount);
            const exceeds = entered && amount > remaining + 0.001;
            const valid = entered && amount > 0 && !exceeds;
            const paid = Number(invoice.paidAmount || 0);

            return (
              <div className="space-y-2.5">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-gray-500 dark:text-gray-400">
                    {paid > 0
                      ? t('orders.steps.alreadyPaidRemaining', { amount: formatCurrency(paid) })
                      : t('orders.remainingDue')}
                  </span>
                  <span className="font-black text-amber-600 dark:text-amber-400 tabular-nums">
                    {formatCurrency(remaining)}
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div className="col-span-2">
                    <div className="flex items-center justify-between mb-1">
                      <label htmlFor="ws-amount" className={labelClass}>{t('orders.steps.amountReceived')}</label>
                      <button
                        type="button"
                        onClick={() => setPaymentForm((prev) => ({ ...prev, amount: remaining.toFixed(2) }))}
                        className="text-[11px] font-semibold text-green-700 dark:text-green-400 hover:underline"
                      >
                        {t('orders.steps.payAll')}
                      </button>
                    </div>
                    <input
                      id="ws-amount"
                      type="number"
                      min="0"
                      step="0.01"
                      max={remaining.toFixed(2)}
                      value={paymentAmount}
                      onChange={(e) => setPaymentForm({ ...paymentForm, amount: e.target.value })}
                      className={`${fieldClass} text-right ${exceeds ? 'border-red-400 focus:border-red-500 focus:ring-red-200' : ''}`}
                    />
                  </div>
                  <div>
                    <label htmlFor="ws-pay-method" className={labelClass}>{t('orders.steps.methodShort')}</label>
                    <select
                      id="ws-pay-method"
                      value={paymentMethod}
                      onChange={(e) => setPaymentForm({ ...paymentForm, paymentMethod: e.target.value })}
                      className={fieldClass}
                    >
                      {PAYMENT_METHODS.map((m) => (
                        <option key={m.value} value={m.value}>{t(m.labelKey)}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label htmlFor="ws-pay-date" className={labelClass}>{t('orders.date')}</label>
                    <input
                      id="ws-pay-date"
                      type="date"
                      max={todayISO()}
                      value={paymentDate}
                      onChange={(e) => setPaymentForm({ ...paymentForm, paymentDate: e.target.value })}
                      className={fieldClass}
                    />
                  </div>
                </div>

                {exceeds && (
                  <p className="text-[11px] text-red-600 dark:text-red-400">
                    {t('orders.steps.amountExceeds', { amount: formatCurrency(remaining) })}
                  </p>
                )}

                <StepButton
                  icon={CreditCard}
                  label={amount >= remaining - 0.001
                    ? t('orders.steps.settleInFull')
                    : t('orders.steps.recordDeposit')}
                  loadingLabel={t('orders.steps.recordingPayment')}
                  loading={busy === 'pay'}
                  disabled={!valid}
                  onClick={() => submitPayment({ amount, paymentMethod, paymentDate })}
                  tone="success"
                />

                {/* Même montant et même facture, mais le paiement passe par le terminal au
                    lieu d'être saisi à la main. */}
                <button
                  type="button"
                  onClick={() => onOpenTerminal(amount)}
                  disabled={!valid || busy === 'pay'}
                  className="w-full flex items-center justify-center gap-2 px-4 py-2 rounded-xl border border-primary-300 dark:border-primary-500/40 text-primary-700 dark:text-primary-300 text-sm font-semibold hover:bg-primary-50 dark:hover:bg-primary-500/10 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <CreditCard className="w-4 h-4" aria-hidden="true" />
                  {t('orders.steps.payByCard')}
                </button>
              </div>
            );
          })()}

          {invoice && settled && (
            <p className="flex items-center gap-2 p-2.5 bg-green-50 dark:bg-green-500/10 text-green-700 dark:text-green-300 rounded-lg text-xs font-medium">
              <CheckCircle className="w-4 h-4 shrink-0" aria-hidden="true" />
              {t('orders.steps.invoiceSettled', { amount: formatCurrency(invoice.paidAmount) })}
            </p>
          )}

          {order.status === 'DELIVERED' && (
            <p className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              <CheckCircle className="w-4 h-4 shrink-0 text-green-600" aria-hidden="true" />
              {t('orders.steps.deliveredNotice')}
            </p>
          )}
        </>
      )}

      {/* ── Actions communes ─────────────────────────────────────────────────────────── */}
      <div className="pt-2 border-t border-gray-200 dark:border-gray-700 space-y-1.5">
        {invoice && invoice.status !== 'CANCELED' && (
          <button
            type="button"
            onClick={onDownloadPdf}
            disabled={busy === 'pdf'}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 text-xs font-semibold text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors disabled:opacity-60 disabled:cursor-wait"
          >
            <Download className="w-3.5 h-3.5" aria-hidden="true" />
            {busy === 'pdf' ? t('orders.steps.generatingPdf') : t('orders.steps.downloadInvoicePdf')}
          </button>
        )}

        {invoice && (
          <button
            type="button"
            onClick={onOpenInvoice}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 text-xs font-semibold text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          >
            <FileText className="w-3.5 h-3.5" aria-hidden="true" />
            {t('orders.steps.viewInvoice', { number: invoice.invoiceNumber })}
          </button>
        )}

        {/* Annulation : mêmes conditions que `OrderService.cancelOrder`. Possible tant que la
            commande n'est pas terminée et qu'elle n'a pas de facture active. Si elle est
            facturée, il faut d'abord annuler la facture dans l'écran Factures. */}
        {(['PENDING', 'CONFIRMED'].includes(order.status)
          || (order.status === 'INVOICED' && (invoice?.status ?? order.invoiceStatus) === 'CANCELED')) && (
          <button
            type="button"
            onClick={onCancelOrder}
            disabled={busy === 'cancel'}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 text-xs font-semibold text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-lg transition-colors disabled:opacity-60"
          >
            <XCircle className="w-3.5 h-3.5" aria-hidden="true" />
            {busy === 'cancel' ? t('orders.steps.canceling') : t('orders.steps.cancelOrder')}
          </button>
        )}

        {order.status === 'INVOICED' && (invoice?.status ?? order.invoiceStatus) !== 'CANCELED' && (
          <p className="text-[11px] text-center text-gray-400 dark:text-gray-500">
            {t('orders.steps.cancelInvoiceFirst')}
          </p>
        )}

        {/* Fin du parcours : on repart sur un panier vide sans fermer l'écran, pour enchaîner
            les clients en caisse. */}
        {(settled || order.status === 'DELIVERED') && (
          <button type="button" onClick={onReset} className="btn-secondary w-full text-sm">
            <ShoppingCart className="w-4 h-4" aria-hidden="true" />
            {t('orders.addOrder')}
          </button>
        )}

        {!settled && order.status !== 'DELIVERED' && (
          <button
            type="button"
            onClick={onReset}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 text-xs font-medium text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" aria-hidden="true" />
            {t('orders.steps.setAsideAndStartNew')}
          </button>
        )}
      </div>
    </div>
  );
};

export default OrderWorkspaceSteps;
