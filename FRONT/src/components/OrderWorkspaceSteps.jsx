import { useState } from 'react';
import {
  AlertCircle, Check, CheckCircle, CreditCard, Download, Euro, FileText, Loader2,
  Plus, RotateCcw, Save, ShoppingCart, XCircle,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { PAYMENT_METHODS } from '../constants/paymentMethods';
import { formatCurrency, todayISO } from '../utils/format';
import { WORKSPACE_STEPS as STEPS, remainingOf, workspaceStepIndex } from '../utils/orderLifecycle';

/**
 * Étapes du traitement d'une commande, pilotées depuis le pied du panier.
 *
 * Le principe : à un instant donné, une commande n'a qu'une seule suite logique. Le panneau
 * n'affiche donc que celle-là — valider, puis confirmer, puis facturer, puis encaisser — au
 * lieu d'aligner cinq boutons dont quatre finiraient en erreur 400. L'enchaînement reproduit
 * la machine à états du backend (`Order.OrderStatus.ALLOWED_TRANSITIONS`) et ses gardes :
 *
 *   Panier → PENDING → CONFIRMED → INVOICED → réglée
 *              (POST)  (stock −)   (facture)  (encaissements)
 *
 * Aucune règle n'est réimplémentée ici : les conditions affichées ne font que refléter celles
 * que le serveur applique de toute façon (modification réservée au brouillon, facturation
 * réservée à une commande confirmée, encaissement porté par la facture).
 */

/** Ruban de progression : quatre pastilles reliées, l'étape courante mise en avant. */
const StepRibbon = ({ index }) => {
  const { t } = useTranslation();
  const canceled = index < 0;
  return (
    <ol className="flex items-center" aria-label={t('orders.steps.progressLabel')}>
      {STEPS.map((step, i) => {
        const done = !canceled && i < index;
        const current = !canceled && i === index;
        return (
          <li key={step.key} className={`flex items-center ${i < STEPS.length - 1 ? 'flex-1' : ''}`}>
            <div className="flex flex-col items-center gap-1">
              <span
                aria-current={current ? 'step' : undefined}
                className={`w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold ${
                  done
                    ? 'bg-green-600 text-white'
                    : current
                      ? 'bg-primary-600 text-white ring-4 ring-primary-100 dark:ring-primary-500/20'
                      : 'bg-gray-200 dark:bg-gray-600 text-gray-500 dark:text-gray-300'
                }`}
              >
                {done ? <Check className="w-3 h-3" aria-hidden="true" /> : i + 1}
              </span>
              <span
                className={`text-[10px] whitespace-nowrap ${
                  current
                    ? 'font-semibold text-primary-700 dark:text-primary-300'
                    : 'text-gray-500 dark:text-gray-400'
                }`}
              >
                {t(step.labelKey)}
              </span>
            </div>
            {i < STEPS.length - 1 && (
              <span
                aria-hidden="true"
                className={`flex-1 h-0.5 mx-1.5 -mt-4 ${
                  done ? 'bg-green-600' : 'bg-gray-200 dark:bg-gray-600'
                }`}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
};

/** Bouton d'action principale de l'étape : pleine largeur, avec état d'attente explicite. */
const StepButton = ({ icon: Icon, label, loadingLabel, loading, disabled, onClick, tone = 'primary' }) => {
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
  const stepIndex = workspaceStepIndex(order, invoice);
  const remaining = remainingOf(invoice);
  const settled = !!invoice && (invoice.status === 'PAID' || remaining <= 0.001);

  // Les champs non encore touchés restent à `null` et prennent leur valeur par défaut au
  // moment du rendu. C'est ce qui permet au montant proposé de suivre le reste dû après un
  // acompte, ou au taux de TVA de refléter les réglages dès qu'ils arrivent, sans effet de
  // synchronisation — et sans jamais écraser une saisie en cours.
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

  // Valeurs par défaut tirées des réglages de l'entreprise (taux de TVA, délai de paiement)
  // plutôt que codées en dur : c'est l'écran Réglages qui fait foi, et les ressaisir à chaque
  // facture était la première source d'écart entre deux caissiers. Le repli éventuel est déjà
  // appliqué par `useSettings`, en amont — il n'y a plus qu'une valeur à reprendre ici.
  const invoiceDate = invoiceForm.invoiceDate ?? todayISO();
  const dueDate = invoiceForm.dueDate ?? defaultDueDate;
  const taxRateValue = invoiceForm.taxRate ?? defaultTaxRate;

  const paymentAmount = paymentForm.amount ?? remaining.toFixed(2);
  const paymentMethod = paymentForm.paymentMethod ?? invoice?.paymentMethod ?? 'CASH';
  const paymentDate = paymentForm.paymentDate ?? todayISO();

  // Après un encaissement, on relâche le montant saisi : la prochaine proposition doit repartir
  // du nouveau reliquat, pas de la somme déjà réglée.
  const submitPayment = async (payload) => {
    await onRecordPayment(payload);
    setPaymentForm((prev) => ({ ...prev, amount: null }));
  };

  // ── Étape 1 : brouillon, la commande n'existe pas encore ────────────────────────────
  if (!order) {
    return (
      <div className="space-y-3">
        <StepRibbon index={stepIndex} />
        {blockingHint && (
          <p className="flex items-start gap-1.5 text-[11px] font-medium text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 rounded-lg p-2">
            <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-px" aria-hidden="true" />
            {blockingHint}
          </p>
        )}
        <StepButton
          icon={CheckCircle}
          label={t('orders.steps.validateOrder')}
          loadingLabel={t('orders.steps.creating')}
          loading={busy === 'create'}
          disabled={!canValidate}
          onClick={onValidate}
        />
        <p className="text-[11px] text-center text-gray-400 dark:text-gray-500">
          {t('orders.steps.validateHint')}
        </p>
      </div>
    );
  }

  // ── Commande annulée : plus de suite possible, on propose de repartir ───────────────
  if (order.status === 'CANCELED') {
    return (
      <div className="space-y-3">
        <StepRibbon index={stepIndex} />
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
      <StepRibbon index={stepIndex} />

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
        // La remise déjà portée par la commande se cumule à celle consentie ici : la facture
        // déduit les deux du sous-total (cf. InvoiceService.createInvoice).
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

            {/* Aperçu du calcul, dans l'ordre exact de `InvoiceService.createInvoice` :
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

      {/* ── Étape 4 : facturée, reste à encaisser ────────────────────────────────────── */}
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

                {/* Même somme, même facture : seul l'encaissement passe par le prestataire au
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

      {/* ── Actions transverses ──────────────────────────────────────────────────────── */}
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

        {/* Annulation : reproduit les gardes de `OrderService.cancelOrder` — possible tant que
            la commande n'est pas dans un état terminal et qu'aucune facture vivante n'y est
            rattachée. Facturée, il faut d'abord annuler la facture depuis l'écran Factures. */}
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

        {/* Fin de parcours : on repart sur un panier vierge sans quitter l'atelier — c'est la
            boucle attendue en caisse, un client après l'autre. */}
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
