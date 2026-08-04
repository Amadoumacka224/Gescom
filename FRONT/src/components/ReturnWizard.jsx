import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  AlertTriangle,
  Banknote,
  Minus,
  Package,
  PackagePlus,
  Plus,
  Receipt,
  Repeat,
  RotateCcw,
  Search,
  User,
  X,
} from 'lucide-react';
import api from '../services/api';
import Button from './Button';
import FormInput from './FormInput';
import SearchableSelect from './SearchableSelect';
import { formatCurrency, formatDate } from '../utils/format';
import { ORDER_STATUS_TONE, badgeClass } from '../constants/statusBadges';

/* Motifs de retour : liste fermée, alignée sur StockReturnItem.ReturnReason. Un motif libre
 * ne se compte pas — c'est cette liste qui rend les retours analysables. Les précisions vont
 * dans les notes du retour. */
const REASONS = ['DEFECTIVE', 'DAMAGED', 'WRONG_ITEM', 'NOT_SATISFIED', 'ORDER_ERROR', 'OTHER'];

/* Traitements, avec l'effet de stock que le backend leur applique (cf. ReturnTreatment). */
const TREATMENTS = [
  { value: 'RESTOCK', icon: PackagePlus },
  { value: 'REFUND', icon: Banknote },
  { value: 'EXCHANGE', icon: Repeat },
];

const DEFAULT_LINE = { selected: false, quantity: 1, reason: 'DEFECTIVE', treatment: 'RESTOCK', replacementProductId: '' };

/**
 * Saisie d'un retour client, adossée à la vente d'origine.
 *
 * Le formulaire n'est jamais une saisie libre : on part d'un numéro de commande ou de facture,
 * le serveur renvoie la vente et ses lignes, et seules les quantités encore retournables sont
 * saisissables. Le récapitulatif montre en permanence l'effet sur le stock et le montant
 * remboursé — l'utilisateur valide ce qu'il a vu.
 */
const ReturnWizard = ({ isOpen, products = [], onSuccess, onClose }) => {
  const { t } = useTranslation();

  const [reference, setReference] = useState('');
  const [searching, setSearching] = useState(false);
  const [lookupError, setLookupError] = useState('');
  const [sale, setSale] = useState(null);

  // État de saisie par produit. Séparé de `sale` : recharger une vente ne doit pas
  // conserver des quantités saisies pour une autre.
  const [lines, setLines] = useState({});
  const [notes, setNotes] = useState('');
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Repartir d'un formulaire vierge à chaque ouverture : une modale rouverte doit poser la
  // même question qu'à la première fois, pas rejouer le dernier retour saisi.
  useEffect(() => {
    if (!isOpen) return;
    setReference('');
    setSale(null);
    setLines({});
    setNotes('');
    setLookupError('');
    setFormError('');
  }, [isOpen]);

  const apiMessage = (error, fallback) => {
    const raw = error.response?.data;
    if (typeof raw === 'string') return raw;
    return raw?.message || raw?.error || fallback;
  };

  const handleLookup = async (e) => {
    e?.preventDefault();
    const query = reference.trim();
    if (!query) {
      setLookupError(t('stock.returns.referenceRequired'));
      return;
    }
    try {
      setSearching(true);
      setLookupError('');
      const { data } = await api.get('/stock/returns/lookup', { params: { reference: query } });
      setSale(data);
      // Pré-remplir chaque ligne retournable avec la quantité maximale : le cas courant est
      // le retour intégral d'un article, l'utilisateur n'a plus qu'à décocher ou réduire.
      setLines(
        Object.fromEntries(
          data.items.map((item) => [
            item.productId,
            { ...DEFAULT_LINE, quantity: Math.max(1, item.quantityReturnable) },
          ])
        )
      );
      setFormError('');
    } catch (error) {
      setSale(null);
      setLines({});
      setLookupError(apiMessage(error, t('stock.returns.lookupError')));
    } finally {
      setSearching(false);
    }
  };

  const updateLine = (productId, patch) =>
    setLines((prev) => ({ ...prev, [productId]: { ...prev[productId], ...patch } }));

  const returnableItems = useMemo(
    () => (sale?.items || []).filter((item) => item.quantityReturnable > 0),
    [sale]
  );

  const selectedItems = useMemo(
    () => returnableItems.filter((item) => lines[item.productId]?.selected),
    [returnableItems, lines]
  );

  const allSelected = returnableItems.length > 0 && selectedItems.length === returnableItems.length;

  const toggleAll = () => {
    const selected = !allSelected;
    setLines((prev) => {
      const next = { ...prev };
      returnableItems.forEach((item) => {
        next[item.productId] = { ...next[item.productId], selected };
      });
      return next;
    });
  };

  /* Appliquer un motif ou un traitement à toutes les lignes retenues : un colis abîmé produit
   * six lignes avec le même motif, qu'on ne veut pas saisir six fois. */
  const applyToSelection = (patch) =>
    setLines((prev) => {
      const next = { ...prev };
      selectedItems.forEach((item) => {
        next[item.productId] = { ...next[item.productId], ...patch };
      });
      return next;
    });

  const productById = useMemo(
    () => Object.fromEntries(products.map((product) => [String(product.id), product])),
    [products]
  );

  /**
   * Montant remboursé pour une quantité rendue, calculé comme le serveur le fera : la part du
   * total payé qui revient aux unités rendues. Multiplier le prix unitaire — arrondi au centime
   * pour l'affichage — perdrait un centime sur les lignes qui ne se divisent pas en parts
   * entières, et l'aperçu annoncerait alors autre chose que le retour enregistré.
   */
  const refundFor = (item, quantity) => {
    const sold = item.quantitySold || 0;
    if (sold <= 0) return 0;
    return Math.round(((item.lineTotal ?? 0) * quantity * 100) / sold) / 100;
  };

  const totals = useMemo(() => {
    let quantity = 0;
    let refund = 0;
    selectedItems.forEach((item) => {
      const line = lines[item.productId];
      const amount = Number(line.quantity) || 0;
      quantity += amount;
      if (line.treatment === 'REFUND') refund += refundFor(item, amount);
    });
    return { lines: selectedItems.length, quantity, refund };
  }, [selectedItems, lines]);

  /**
   * Effets de stock d'une ligne, tels que le backend les appliquera : l'article rendu revient
   * toujours en stock, et l'échange en ressort l'article de remplacement. À l'identique, les
   * deux mouvements se compensent — le récapitulatif le montre plutôt que d'annoncer une
   * hausse qui n'aura pas lieu.
   */
  const stockEffects = (item, line) => {
    const quantity = Number(line.quantity) || 0;
    const current = item.stockQuantity ?? 0;
    if (line.treatment !== 'EXCHANGE') {
      return [{ name: item.productName, from: current, to: current + quantity }];
    }
    const replacement = line.replacementProductId ? productById[String(line.replacementProductId)] : null;
    if (!replacement || String(replacement.id) === String(item.productId)) {
      return [{ name: item.productName, from: current, to: current }];
    }
    return [
      { name: item.productName, from: current, to: current + quantity },
      {
        name: replacement.name,
        from: replacement.stockQuantity ?? 0,
        to: (replacement.stockQuantity ?? 0) - quantity,
      },
    ];
  };

  const validate = () => {
    if (selectedItems.length === 0) return t('stock.returns.selectAtLeastOne');
    for (const item of selectedItems) {
      const line = lines[item.productId];
      const quantity = Number(line.quantity);
      if (!Number.isInteger(quantity) || quantity < 1) {
        return t('stock.returns.quantityInvalid', { product: item.productName });
      }
      if (quantity > item.quantityReturnable) {
        return t('stock.returns.quantityTooHigh', {
          product: item.productName,
          max: item.quantityReturnable,
        });
      }
      // Un échange contre un autre article sort ce dernier du stock : le refuser ici évite
      // un aller-retour serveur pour une InsufficientStockException prévisible.
      if (line.treatment === 'EXCHANGE' && line.replacementProductId) {
        const replacement = productById[String(line.replacementProductId)];
        if (
          replacement &&
          String(replacement.id) !== String(item.productId) &&
          (replacement.stockQuantity ?? 0) < quantity
        ) {
          return t('stock.returns.replacementOutOfStock', {
            product: replacement.name,
            available: replacement.stockQuantity ?? 0,
          });
        }
      }
    }
    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const error = validate();
    if (error) {
      setFormError(error);
      return;
    }
    setFormError('');

    const payload = {
      orderId: sale.orderId,
      notes: notes.trim() || null,
      items: selectedItems.map((item) => {
        const line = lines[item.productId];
        return {
          productId: item.productId,
          quantity: Number(line.quantity),
          reason: line.reason,
          treatment: line.treatment,
          replacementProductId:
            line.treatment === 'EXCHANGE' && line.replacementProductId
              ? Number(line.replacementProductId)
              : null,
        };
      }),
    };

    try {
      setSubmitting(true);
      const { data } = await api.post('/stock/returns', payload);
      onSuccess(data);
    } catch (error) {
      setFormError(apiMessage(error, t('stock.returns.submitError')));
    } finally {
      setSubmitting(false);
    }
  };

  /* ---- Étape 1 : retrouver la vente ---- */

  if (!sale) {
    return (
      <form onSubmit={handleLookup} className="space-y-5">
        <p className="text-sm text-gray-500 dark:text-gray-400 -mt-1">{t('stock.returns.lookupHint')}</p>

        <FormInput
          label={t('stock.returns.referenceLabel')}
          name="saleReference"
          value={reference}
          onChange={(e) => {
            setReference(e.target.value);
            setLookupError('');
          }}
          placeholder={t('stock.returns.referencePlaceholder')}
          icon={Receipt}
          error={lookupError}
          hint={t('stock.returns.referenceHint')}
          autoFocus
          maxLength={50}
        />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="primary" icon={Search} loading={searching}>
            {t('stock.returns.findSale')}
          </Button>
        </div>
      </form>
    );
  }

  /* ---- Étape 2 : sélectionner les articles rendus ---- */

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      {/* Vente retrouvée */}
      <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/40 p-4 space-y-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0 space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold text-gray-900 dark:text-gray-100">{sale.orderNumber}</span>
              <span className={badgeClass(ORDER_STATUS_TONE[sale.orderStatus])}>
                {t(`status.order.${sale.orderStatus}`, { defaultValue: sale.orderStatus })}
              </span>
              {sale.invoiceNumber && (
                <span className="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
                  <Receipt className="w-3.5 h-3.5" aria-hidden="true" />
                  {sale.invoiceNumber}
                </span>
              )}
            </div>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-gray-600 dark:text-gray-400">
              <span className="inline-flex items-center gap-1.5">
                <User className="w-3.5 h-3.5 text-gray-400" aria-hidden="true" />
                {sale.clientName || t('stock.returns.walkInClient')}
              </span>
              <span>{formatDate(sale.orderDate)}</span>
              <span className="font-medium text-gray-900 dark:text-gray-100">
                {formatCurrency(sale.orderAmount)}
              </span>
            </div>
          </div>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            icon={X}
            onClick={() => {
              setSale(null);
              setLines({});
              setFormError('');
            }}
          >
            {t('stock.returns.changeSale')}
          </Button>
        </div>

        {sale.previousReturns > 0 && (
          <p className="flex items-center gap-2 text-sm text-amber-700 dark:text-amber-400">
            <AlertTriangle className="w-4 h-4 shrink-0" aria-hidden="true" />
            {t('stock.returns.previousReturns', { count: sale.previousReturns })}
          </p>
        )}
      </div>

      {returnableItems.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-8 text-center">
          <Package className="empty-state-icon" aria-hidden="true" />
          <p className="font-medium text-gray-700 dark:text-gray-300">
            {t('stock.returns.nothingReturnableTitle')}
          </p>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            {t('stock.returns.nothingReturnableHint')}
          </p>
        </div>
      ) : (
        <>
          {/* Actions groupées : une même cause produit souvent plusieurs lignes identiques. */}
          <div className="flex flex-wrap items-center gap-3">
            <label className="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-gray-300 cursor-pointer">
              <input
                type="checkbox"
                checked={allSelected}
                onChange={toggleAll}
                className="w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
              />
              {t('stock.returns.selectAll')}
            </label>
            <span className="text-sm text-gray-400" aria-hidden="true">|</span>
            <select
              value=""
              onChange={(e) => e.target.value && applyToSelection({ reason: e.target.value })}
              disabled={selectedItems.length === 0}
              aria-label={t('stock.returns.applyReasonToAll')}
              className="input-field w-auto py-1.5 text-sm"
            >
              <option value="">{t('stock.returns.applyReasonToAll')}</option>
              {REASONS.map((reason) => (
                <option key={reason} value={reason}>
                  {t(`stock.returns.reasons.${reason}`)}
                </option>
              ))}
            </select>
            <select
              value=""
              onChange={(e) => e.target.value && applyToSelection({ treatment: e.target.value })}
              disabled={selectedItems.length === 0}
              aria-label={t('stock.returns.applyTreatmentToAll')}
              className="input-field w-auto py-1.5 text-sm"
            >
              <option value="">{t('stock.returns.applyTreatmentToAll')}</option>
              {TREATMENTS.map((treatment) => (
                <option key={treatment.value} value={treatment.value}>
                  {t(`stock.returns.treatments.${treatment.value}`)}
                </option>
              ))}
            </select>
          </div>

          {/* Articles de la vente. Une fiche par ligne plutôt qu'un tableau : la saisie ouvre
              trois contrôles par article, plus le remplaçant en cas d'échange. */}
          <div className="space-y-3">
            {sale.items.map((item) => {
              const line = lines[item.productId] ?? DEFAULT_LINE;
              const exhausted = item.quantityReturnable <= 0;
              const selected = line.selected && !exhausted;

              return (
                <div
                  key={item.productId}
                  className={`rounded-xl border p-4 transition-colors ${
                    selected
                      ? 'border-primary-300 dark:border-primary-500/40 bg-primary-50/40 dark:bg-primary-500/5'
                      : 'border-gray-200 dark:border-gray-700'
                  } ${exhausted ? 'opacity-60' : ''}`}
                >
                  <div className="flex items-start gap-3">
                    <input
                      type="checkbox"
                      checked={selected}
                      disabled={exhausted}
                      onChange={(e) => updateLine(item.productId, { selected: e.target.checked })}
                      aria-label={`${t('stock.returns.selectArticle')} — ${item.productName}`}
                      className="mt-1 w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 disabled:opacity-50"
                    />
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-baseline justify-between gap-2">
                        <span className="font-semibold text-gray-900 dark:text-gray-100">
                          {item.productName}
                        </span>
                        <span className="text-sm tabular-nums text-gray-600 dark:text-gray-400">
                          {formatCurrency(item.unitPrice)} / {item.unit || t('stock.returns.unitFallback')}
                        </span>
                      </div>
                      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-1 text-xs text-gray-500 dark:text-gray-400 tabular-nums">
                        <span>{item.productCode || '—'}</span>
                        <span>{t('stock.returns.sold', { n: item.quantitySold })}</span>
                        {item.quantityReturned > 0 && (
                          <span>{t('stock.returns.alreadyReturned', { n: item.quantityReturned })}</span>
                        )}
                        <span
                          className={
                            exhausted
                              ? 'text-gray-400'
                              : 'font-medium text-gray-700 dark:text-gray-300'
                          }
                        >
                          {t('stock.returns.returnable', { n: item.quantityReturnable })}
                        </span>
                      </div>
                    </div>
                  </div>

                  {selected && (
                    <div className="mt-4 pl-7 space-y-3">
                      <div className="flex flex-wrap items-end gap-4">
                        {/* Quantité rendue, bornée au retournable côté saisie comme côté serveur. */}
                        <div className="space-y-1.5">
                          <span className="block text-xs font-medium text-gray-500 dark:text-gray-400">
                            {t('stock.returns.quantityLabel')}
                          </span>
                          <div className="inline-flex items-center rounded-lg border border-gray-300 dark:border-gray-600 overflow-hidden">
                            <button
                              type="button"
                              onClick={() =>
                                updateLine(item.productId, {
                                  quantity: Math.max(1, (Number(line.quantity) || 1) - 1),
                                })
                              }
                              className="px-2.5 py-2 text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40"
                              disabled={(Number(line.quantity) || 1) <= 1}
                              aria-label={t('stock.returns.decrease')}
                            >
                              <Minus className="w-4 h-4" aria-hidden="true" />
                            </button>
                            <input
                              type="number"
                              min="1"
                              max={item.quantityReturnable}
                              value={line.quantity}
                              onChange={(e) => updateLine(item.productId, { quantity: e.target.value })}
                              aria-label={`${t('stock.returns.quantityLabel')} — ${item.productName}`}
                              className="w-16 text-center bg-transparent tabular-nums font-semibold text-gray-900 dark:text-gray-100 focus:outline-none"
                            />
                            <button
                              type="button"
                              onClick={() =>
                                updateLine(item.productId, {
                                  quantity: Math.min(
                                    item.quantityReturnable,
                                    (Number(line.quantity) || 0) + 1
                                  ),
                                })
                              }
                              className="px-2.5 py-2 text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40"
                              disabled={(Number(line.quantity) || 0) >= item.quantityReturnable}
                              aria-label={t('stock.returns.increase')}
                            >
                              <Plus className="w-4 h-4" aria-hidden="true" />
                            </button>
                          </div>
                        </div>

                        <div className="space-y-1.5 min-w-[12rem]">
                          <label
                            htmlFor={`reason-${item.productId}`}
                            className="block text-xs font-medium text-gray-500 dark:text-gray-400"
                          >
                            {t('stock.returns.reasonLabel')}
                          </label>
                          <select
                            id={`reason-${item.productId}`}
                            value={line.reason}
                            onChange={(e) => updateLine(item.productId, { reason: e.target.value })}
                            className="input-field py-2 text-sm"
                          >
                            {REASONS.map((reason) => (
                              <option key={reason} value={reason}>
                                {t(`stock.returns.reasons.${reason}`)}
                              </option>
                            ))}
                          </select>
                        </div>

                        {/* Traitement en segments : trois choix exclusifs, visibles d'un coup
                            d'œil et atteignables en un clic. */}
                        <div className="space-y-1.5">
                          <span className="block text-xs font-medium text-gray-500 dark:text-gray-400">
                            {t('stock.returns.treatmentLabel')}
                          </span>
                          <div
                            role="radiogroup"
                            aria-label={`${t('stock.returns.treatmentLabel')} — ${item.productName}`}
                            className="inline-flex rounded-lg border border-gray-300 dark:border-gray-600 p-0.5 gap-0.5"
                          >
                            {TREATMENTS.map((treatment) => {
                              const active = line.treatment === treatment.value;
                              return (
                                <button
                                  key={treatment.value}
                                  type="button"
                                  role="radio"
                                  aria-checked={active}
                                  onClick={() => updateLine(item.productId, { treatment: treatment.value })}
                                  title={t(`stock.returns.treatmentHints.${treatment.value}`)}
                                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md transition-colors ${
                                    active
                                      ? 'bg-primary-600 text-white'
                                      : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                                  }`}
                                >
                                  <treatment.icon className="w-4 h-4" aria-hidden="true" />
                                  {t(`stock.returns.treatments.${treatment.value}`)}
                                </button>
                              );
                            })}
                          </div>
                        </div>
                      </div>

                      {line.treatment === 'EXCHANGE' && (
                        <div className="space-y-1.5 max-w-md">
                          <span className="block text-xs font-medium text-gray-500 dark:text-gray-400">
                            {t('stock.returns.replacementLabel')}
                          </span>
                          <SearchableSelect
                            options={products}
                            value={line.replacementProductId}
                            onChange={(value) =>
                              updateLine(item.productId, { replacementProductId: value })
                            }
                            getOptionValue={(p) => p.id}
                            getOptionLabel={(p) => p.name}
                            getOptionSearch={(p) => `${p.code || ''} ${p.barcode || ''}`}
                            placeholder={t('stock.returns.replacementPlaceholder')}
                            noResultsText={t('stock.noProductFound')}
                            minChars={1}
                            inputClassName="input-field pl-10 pr-9 py-2 text-sm"
                            renderOption={(p) => (
                              <span className="flex flex-col">
                                <span className="font-medium truncate">{p.name}</span>
                                <span className="text-xs text-gray-500">
                                  {[p.code, `${p.stockQuantity ?? 0} ${p.unit || ''}`.trim()]
                                    .filter(Boolean)
                                    .join(' · ')}
                                </span>
                              </span>
                            )}
                          />
                          <p className="text-xs text-gray-500 dark:text-gray-400">
                            {t('stock.returns.replacementHint')}
                          </p>
                        </div>
                      )}

                      {/* Effet exact de la ligne : ce qui bouge en stock, ce qui est remboursé. */}
                      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-600 dark:text-gray-400 tabular-nums">
                        {stockEffects(item, line).map((effect) => (
                          <span key={effect.name} className="inline-flex items-center gap-1.5">
                            <Package className="w-3.5 h-3.5 text-gray-400" aria-hidden="true" />
                            {effect.name}
                            <span className="text-gray-400">{effect.from} →</span>
                            <span
                              className={`font-semibold ${
                                effect.to > effect.from
                                  ? 'text-green-600 dark:text-green-400'
                                  : effect.to < effect.from
                                    ? 'text-red-600 dark:text-red-400'
                                    : 'text-gray-500'
                              }`}
                            >
                              {effect.to}
                            </span>
                          </span>
                        ))}
                        {line.treatment === 'REFUND' && (
                          <span className="inline-flex items-center gap-1.5 font-semibold text-gray-900 dark:text-gray-100">
                            <Banknote className="w-3.5 h-3.5 text-gray-400" aria-hidden="true" />
                            {t('stock.returns.refundLine', {
                              amount: formatCurrency(refundFor(item, Number(line.quantity) || 0)),
                            })}
                          </span>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          <FormInput
            label={t('stock.returns.notesLabel')}
            name="returnNotes"
            type="textarea"
            rows={2}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder={t('stock.returns.notesPlaceholder')}
            maxLength={500}
          />

          {/* Récapitulatif : le chiffre que l'utilisateur vient vérifier avant de valider. */}
          <div className="flex flex-wrap items-center justify-between gap-4 px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700">
            <span className="text-sm text-gray-600 dark:text-gray-400">
              {t('stock.returns.summary', { lines: totals.lines, quantity: totals.quantity })}
            </span>
            <span className="flex items-center gap-2 text-sm">
              <span className="text-gray-500 dark:text-gray-400">{t('stock.returns.refundTotal')}</span>
              <span className="text-lg font-bold text-gray-900 dark:text-gray-100 tabular-nums">
                {formatCurrency(totals.refund)}
              </span>
            </span>
          </div>
        </>
      )}

      {formError && (
        <p className="text-sm text-red-600 dark:text-red-400" role="alert">
          {formError}
        </p>
      )}

      <div className="flex justify-end gap-3 pt-1">
        <Button type="button" variant="secondary" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button
          type="submit"
          variant="primary"
          icon={RotateCcw}
          loading={submitting}
          disabled={selectedItems.length === 0}
        >
          {t('stock.returns.confirm')}
        </Button>
      </div>
    </form>
  );
};

export default ReturnWizard;
