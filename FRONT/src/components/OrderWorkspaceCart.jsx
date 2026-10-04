import { useState } from 'react';
import {
  AlertCircle, Minus, NotebookPen, Package, Percent, Plus, ShoppingCart, Trash2, User,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import SearchableSelect from './SearchableSelect';
import { computeItemsGross, computeItemsTotal, computeLineTotal, lineGrossTotal } from '../utils/orderTotals';
import { formatCurrency } from '../utils/format';

/**
 * Panier d'une commande : client, lignes, remises et totaux.
 *
 * Il reste affiché pendant tout le traitement. Le bas du panneau (`children`) affiche l'étape
 * en cours (valider, confirmer, facturer, encaisser). Les lignes ne sont modifiables qu'en
 * brouillon ou en attente : après, le stock est sorti et elles servent pour la facture
 * (voir `OrderService.updateOrder`, qui demande PENDING). Le panier passe alors en lecture
 * seule au lieu de disparaître, car on en a besoin pour encaisser.
 *
 * Le panneau a trois zones : en-tête, lignes, pied. Seule la liste des articles s'agrandit.
 * L'en-tête et les totaux ont une hauteur fixe, le pied est limité à 55 % du panneau, et le
 * reste va aux lignes, pour voir le plus d'articles possible sans scroller.
 *
 * Avant la facture, la TVA affichée est une estimation avec le taux des paramètres. Elle
 * n'est ni envoyée ni enregistrée : le vrai taux est saisi au moment de facturer. C'est écrit
 * dans le libellé pour qu'on ne s'étonne pas de voir le TTC changer.
 */
const OrderWorkspaceCart = ({
  order,
  invoice,
  items,
  products,
  clients,
  clientMode,
  clientId,
  notes,
  editable,
  taxRate,
  onClientModeChange,
  onClientChange,
  onCreateClient,
  onNotesChange,
  onQtyStep,
  onLineChange,
  onRemoveLine,
  onApplyGlobalDiscount,
  children,
}) => {
  const { t } = useTranslation();
  const [notesOpen, setNotesOpen] = useState(!!notes);
  const [globalDiscount, setGlobalDiscount] = useState('');

  const grossTotal = computeItemsGross(items);
  const netTotal = computeItemsTotal(items);
  const discountTotal = grossTotal - netTotal;
  const itemCount = items.reduce((n, it) => n + (parseInt(it.quantity) || 0), 0);

  // Une fois facturée, on prend les montants de la facture (remise éventuelle et vrai taux
  // de TVA). Avant, on fait une estimation.
  const invoiced = !!invoice;
  // À l'étape de facturation, le taux est saisi juste en dessous et l'étape affiche son propre
  // « Total à facturer ». Si on affichait ici une estimation, il y aurait deux TTC différents
  // à l'écran : on affiche donc seulement le HT.
  const pendingInvoice = !invoiced && order?.status === 'CONFIRMED';
  const effectiveRate = invoiced ? Number(invoice.taxRate || 0) : Number(taxRate || 0);
  const commercialDiscount = invoiced ? Number(invoice.discount || 0) : 0;
  const taxBase = (invoiced ? Number(invoice.subtotal || 0) : netTotal) - commercialDiscount;
  const taxAmount = invoiced ? Number(invoice.taxAmount || 0) : taxBase * (effectiveRate / 100);
  const totalTTC = invoiced ? Number(invoice.totalAmount || 0) : taxBase + taxAmount;

  const orderClient = order?.client;

  const applyGlobalDiscount = () => {
    const value = parseFloat(globalDiscount);
    if (Number.isNaN(value)) return;
    onApplyGlobalDiscount(Math.min(Math.max(value, 0), 100));
    setGlobalDiscount('');
  };

  // Largeur fixe seulement quand il est à côté du catalogue (`lg`). En dessous, le panier
  // prend toute la largeur et sa hauteur suit le contenu (avec `h-full` il prenait toute la
  // hauteur du modal et poussait le catalogue hors de l'écran).
  return (
    <aside className="w-full lg:w-[380px] shrink-0 lg:h-full flex flex-col bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
      {/* ───────── En-tête : panier et client ───────── */}
      <div className="shrink-0 px-3 py-2.5 border-b border-gray-200 dark:border-gray-700 bg-gray-50/80 dark:bg-gray-900/40 space-y-2">
        <div className="flex items-center justify-between gap-2">
          <h3 className="subsection-title flex items-center gap-2 min-w-0">
            <ShoppingCart className="w-4 h-4 text-primary-600 dark:text-primary-400 shrink-0" aria-hidden="true" />
            <span className="truncate">{order ? order.orderNumber : t('orders.cart.title')}</span>
          </h3>
          <span className="text-xs font-medium text-gray-400 dark:text-gray-500 shrink-0 tabular-nums">
            {t('orders.cart.itemCount', { count: itemCount })}
          </span>
        </div>

        {/* Client : modifiable tant que la commande n'est pas créée. Après, le backend ne
            permet plus de changer le client (OrderUpdateRequest ne contient que les lignes),
            donc on l'affiche en texte simple. */}
        {order ? (
          <div className="flex items-center gap-2 px-2.5 py-1.5 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 text-xs">
            <User className="w-3.5 h-3.5 text-primary-600 dark:text-primary-400 shrink-0" aria-hidden="true" />
            {orderClient ? (
              <span className="min-w-0 truncate">
                <span className="font-bold text-gray-900 dark:text-gray-100">
                  {orderClient.firstName} {orderClient.lastName}
                </span>
                {orderClient.company && (
                  <span className="font-medium text-primary-600 dark:text-primary-400"> · {orderClient.company}</span>
                )}
              </span>
            ) : (
              <span className="text-gray-500 dark:text-gray-400">{t('orders.walkInClient')}</span>
            )}
          </div>
        ) : (
          <>
            <div className="flex w-full p-0.5 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg">
              <button
                type="button"
                onClick={() => onClientModeChange('registered')}
                className={`flex-1 px-3 py-1.5 text-xs font-semibold rounded-md transition-all ${
                  clientMode === 'registered'
                    ? 'bg-primary-600 text-white shadow-sm'
                    : 'text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-gray-100'
                }`}
              >
                {t('orders.cart.clientRegistered')}
              </button>
              <button
                type="button"
                onClick={() => onClientModeChange('guest')}
                className={`flex-1 px-3 py-1.5 text-xs font-semibold rounded-md transition-all ${
                  clientMode === 'guest'
                    ? 'bg-primary-600 text-white shadow-sm'
                    : 'text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-gray-100'
                }`}
              >
                {t('orders.cart.clientGuest')}
              </button>
            </div>

            {clientMode === 'guest' ? (
              <p className="flex items-center gap-2 px-1 text-xs text-gray-500 dark:text-gray-400">
                <AlertCircle className="w-3.5 h-3.5 shrink-0 text-gray-400" aria-hidden="true" />
                {t('orders.cart.guestHint')}
              </p>
            ) : (
              <div className="space-y-1">
                <div className="flex items-center justify-between">
                  <label className="block text-[11px] font-semibold text-gray-700 dark:text-gray-300">
                    {t('orders.client')} <span className="text-red-600">*</span>
                  </label>
                  <button
                    type="button"
                    onClick={onCreateClient}
                    className="inline-flex items-center gap-1 text-[11px] font-semibold text-primary-600 hover:text-primary-700 dark:text-primary-400"
                  >
                    <Plus className="w-3.5 h-3.5" aria-hidden="true" /> {t('common.new')}
                  </button>
                </div>
                <SearchableSelect
                  options={clients}
                  value={clientId}
                  onChange={onClientChange}
                  getOptionValue={(client) => client.id}
                  getOptionLabel={(client) =>
                    `${client.firstName} ${client.lastName}${client.company ? ` • ${client.company}` : ''}`
                  }
                  getOptionSearch={(client) => `${client.email} ${client.phone || ''}`}
                  placeholder={t('orders.cart.clientSearchPlaceholder')}
                  noResultsText={t('orders.cart.noClientFound')}
                  minChars={1}
                  inputClassName="w-full pl-10 pr-9 py-1.5 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg text-sm font-medium text-gray-900 dark:text-gray-100 focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-200 transition-all"
                  renderOption={(client) => (
                    <span className="flex flex-col">
                      <span className="font-medium">
                        {client.firstName} {client.lastName}
                        {client.company && (
                          <span className="ml-2 text-xs text-primary-600">{client.company}</span>
                        )}
                      </span>
                      <span className="text-xs text-gray-500">{client.email}</span>
                    </span>
                  )}
                />
                {/* Pas de rappel sous le champ : le combobox affiche déjà le client choisi
                    (nom et société). Ça faisait une ligne de perdue. */}
              </div>
            )}
          </>
        )}

        {/* Note libre de la commande (champ `notes` côté backend). Fermée par défaut car peu
            utilisée, mais avant il fallait aller sur un autre écran pour noter par exemple
            « à livrer avant vendredi ». */}
        {editable ? (
          <div>
            <button
              type="button"
              onClick={() => setNotesOpen((v) => !v)}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 transition-colors"
            >
              <NotebookPen className="w-3.5 h-3.5" aria-hidden="true" />
              {notesOpen ? t('orders.cart.hideNote') : t('orders.cart.addNote')}
            </button>
            {notesOpen && (
              <textarea
                rows={1}
                maxLength={500}
                value={notes}
                onChange={(e) => onNotesChange(e.target.value)}
                placeholder={t('orders.cart.notePlaceholder')}
                className="mt-1.5 w-full px-2.5 py-1.5 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg text-xs text-gray-900 dark:text-gray-100 focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-200 transition-all resize-none"
              />
            )}
          </div>
        ) : (
          notes && (
            <p className="flex items-start gap-1.5 text-xs text-gray-500 dark:text-gray-400">
              <NotebookPen className="w-3.5 h-3.5 shrink-0 mt-0.5" aria-hidden="true" />
              <span className="min-w-0">{notes}</span>
            </p>
          )
        )}
      </div>

      {/* ───────── Lignes ─────────
          C'est la seule zone qui s'agrandit : elle prend toute la place laissée par l'en-tête
          et le pied. Chaque ligne tient sur trois rangs (article / prix et stock / quantité,
          remise et total) au lieu de quatre.

          `flex-1` seulement à partir de `lg`, quand le panneau a une hauteur fixe. En dessous,
          `flex-1` bloquait la zone à son `min-h` de 120 px (une ligne et demie), alors qu'en
          hauteur auto elle suit son contenu et toute la colonne scrolle ensemble. */}
      <div className="min-h-[120px] lg:flex-1 overflow-y-auto p-1.5 space-y-1">
        {items.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center text-gray-400 dark:text-gray-500 py-8">
            <ShoppingCart className="w-9 h-9 mb-2 text-gray-300 dark:text-gray-600" aria-hidden="true" />
            <p className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('orders.cart.empty')}</p>
            <p className="text-xs mt-0.5">{t('orders.cart.emptyHint')}</p>
          </div>
        ) : (
          items.map((item, index) => {
            const product = products.find((p) => p.id === parseInt(item.productId)) || item.product;
            const exceedsStock = editable && product && parseInt(item.quantity) > product.stockQuantity;
            const gross = lineGrossTotal(item);
            const name = product?.name || t('common.product');
            // Montant de la ligne après remise, avec le prix barré juste avant quand il y a
            // une remise. On voit la différence sans ajouter de ligne.
            const lineAmount = (
              <span className="ml-auto flex items-baseline gap-1 shrink-0">
                {parseFloat(item.discount) > 0 && (
                  <span className="text-[10px] text-gray-400 dark:text-gray-500 line-through tabular-nums">
                    {formatCurrency(gross)}
                  </span>
                )}
                <span className="text-sm font-bold text-gray-900 dark:text-gray-100 tabular-nums">
                  {formatCurrency(computeLineTotal(item))}
                </span>
              </span>
            );

            return (
              <div
                key={item.productId ?? index}
                className="flex gap-2 p-1.5 bg-gray-50 dark:bg-gray-900/40 rounded-lg border border-gray-200 dark:border-gray-700"
              >
                <div className="w-10 h-10 rounded-md overflow-hidden bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shrink-0 flex items-center justify-center">
                  {product?.imageUrl ? (
                    <img src={product.imageUrl} alt="" className="w-full h-full object-cover" />
                  ) : (
                    <Package className="w-5 h-5 text-gray-300 dark:text-gray-600" aria-hidden="true" />
                  )}
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-start justify-between gap-1.5">
                    {/* Sur une seule ligne, le nom complet en infobulle : un nom long ne
                        pousse plus les autres articles hors de l'écran. */}
                    <p
                      title={name}
                      className="text-[13px] font-semibold text-gray-900 dark:text-gray-100 leading-tight truncate"
                    >
                      {name}
                    </p>
                    {editable && (
                      <button
                        type="button"
                        onClick={() => onRemoveLine(index)}
                        className="p-0.5 -mr-0.5 text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-500/10 rounded-md transition-colors shrink-0"
                        title={t('orders.cart.removeLine')}
                        aria-label={t('orders.cart.removeLine')}
                      >
                        <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                      </button>
                    )}
                  </div>

                  {editable ? (
                    <>
                      <p className="text-[11px] leading-tight text-gray-400 dark:text-gray-500 tabular-nums truncate">
                        {formatCurrency(item.unitPrice)}{' '}
                        {product?.unit
                          ? t('orders.cart.perUnit', { unit: product.unit })
                          : t('orders.cart.perPiece')}
                        {product && !exceedsStock
                          && t('orders.cart.stockSuffix', { qty: product.stockQuantity })}
                        {exceedsStock && (
                          <span className="font-semibold text-red-600 dark:text-red-400">
                            {' · '}{t('orders.cart.maxStock', { qty: product.stockQuantity })}
                          </span>
                        )}
                      </p>

                      <div className="mt-1 flex items-center gap-1.5">
                        <div className="inline-flex items-center bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg overflow-hidden shrink-0">
                          <button
                            type="button"
                            onClick={() => onQtyStep(index, product, -1)}
                            className="w-6 h-6 flex items-center justify-center text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                            aria-label={t('orders.cart.decreaseQty')}
                          >
                            <Minus className="w-3.5 h-3.5" aria-hidden="true" />
                          </button>
                          <span className="w-7 text-center text-sm font-bold text-gray-900 dark:text-gray-100 tabular-nums">
                            {item.quantity}
                          </span>
                          <button
                            type="button"
                            onClick={() => onQtyStep(index, product, 1)}
                            className="w-6 h-6 flex items-center justify-center text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                            aria-label={t('orders.cart.increaseQty')}
                          >
                            <Plus className="w-3.5 h-3.5" aria-hidden="true" />
                          </button>
                        </div>

                        <div className="relative shrink-0">
                          <input
                            type="number"
                            min="0"
                            max="100"
                            step="0.01"
                            value={item.discount}
                            onChange={(e) => onLineChange(index, 'discount', e.target.value)}
                            className="w-14 pl-1.5 pr-4 py-0.5 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg text-xs font-semibold text-gray-900 dark:text-gray-100 text-right focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-200 transition-all"
                            title={t('orders.cart.lineDiscountTooltip')}
                            aria-label={t('orders.cart.lineDiscountLabel')}
                          />
                          <span className="absolute right-1.5 top-1/2 -translate-y-1/2 text-[10px] text-gray-400 pointer-events-none">
                            %
                          </span>
                        </div>

                        {lineAmount}
                      </div>
                    </>
                  ) : (
                    // Lignes figées : « quantité × prix » remplace le prix unitaire,
                    // deux rangs suffisent.
                    <div className="flex items-baseline gap-2">
                      <span className="text-[11px] text-gray-500 dark:text-gray-400 tabular-nums truncate">
                        {item.quantity} × {formatCurrency(item.unitPrice)}
                        {parseFloat(item.discount) > 0
                          && t('orders.cart.discountSuffix', { rate: parseFloat(item.discount).toFixed(2) })}
                      </span>
                      {lineAmount}
                    </div>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* ───────── Totaux et étape en cours ─────────
          Deux blocs : les montants, qui ne scrollent jamais, et l'étape en cours, qui scrolle
          seule quand son formulaire est long (facturation). Avant, tout le pied scrollait et
          le total pouvait sortir de l'écran. Le pied est aussi limité à 55 % du panneau pour
          laisser la place aux articles. */}
      <div className="shrink-0 flex flex-col min-h-0 max-h-[55%] border-t border-gray-200 dark:border-gray-700 bg-gray-50/80 dark:bg-gray-900/40">
        <div className="shrink-0 px-3 pt-2.5 pb-2 space-y-2">
          {/* Remise appliquée en une fois sur toutes les lignes : en caisse, on négocie une
              remise sur tout le panier. Elle remplit simplement le champ `discount` de chaque
              ligne, rien de nouveau côté métier. */}
          {editable && items.length > 0 && (
            <div className="flex items-center gap-2">
              <Percent className="w-3.5 h-3.5 text-gray-400 shrink-0" aria-hidden="true" />
              <label htmlFor="cart-global-discount" className="text-xs text-gray-500 dark:text-gray-400">
                {t('orders.cart.globalDiscount')}
              </label>
              <input
                id="cart-global-discount"
                type="number"
                min="0"
                max="100"
                step="0.01"
                value={globalDiscount}
                onChange={(e) => setGlobalDiscount(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); applyGlobalDiscount(); } }}
                placeholder="0"
                className="w-14 ml-auto px-2 py-1 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg text-xs font-semibold text-gray-900 dark:text-gray-100 text-right focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-200"
              />
              <button
                type="button"
                onClick={applyGlobalDiscount}
                disabled={globalDiscount === ''}
                className="px-2 py-1 text-xs font-semibold text-primary-700 dark:text-primary-300 bg-primary-50 dark:bg-primary-500/10 rounded-lg hover:bg-primary-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                {t('common.apply')}
              </button>
            </div>
          )}

          {/* Détail du prix, dans le même ordre que le calcul côté serveur. */}
          <dl className="space-y-0.5 text-xs">
            <div className="flex items-center justify-between">
              <dt className="text-gray-500 dark:text-gray-400">{t('orders.subtotalExclTax')}</dt>
              <dd className="font-semibold text-gray-700 dark:text-gray-200 tabular-nums">{formatCurrency(grossTotal)}</dd>
            </div>
            {discountTotal > 0.001 && (
              <div className="flex items-center justify-between">
                <dt className="text-gray-500 dark:text-gray-400">{t('orders.cart.lineDiscounts')}</dt>
                <dd className="font-semibold text-red-600 dark:text-red-400 tabular-nums">
                  −{formatCurrency(discountTotal)}
                </dd>
              </div>
            )}
            {commercialDiscount > 0.001 && (
              <div className="flex items-center justify-between">
                <dt className="text-gray-500 dark:text-gray-400">{t('orders.cart.commercialDiscount')}</dt>
                <dd className="font-semibold text-red-600 dark:text-red-400 tabular-nums">
                  −{formatCurrency(commercialDiscount)}
                </dd>
              </div>
            )}
            {!pendingInvoice && (
              <div className="flex items-center justify-between">
                <dt className="text-gray-500 dark:text-gray-400">
                  {t('orders.taxWithRate', { rate: effectiveRate.toFixed(2) })}
                  {!invoiced && t('orders.cart.estimatedSuffix')}
                </dt>
                <dd className="font-semibold text-gray-700 dark:text-gray-200 tabular-nums">
                  {formatCurrency(taxAmount)}
                </dd>
              </div>
            )}
          </dl>

          <div className="flex items-end justify-between gap-3 pt-1 border-t border-gray-200 dark:border-gray-700">
            <div className="text-[11px] text-gray-400 dark:text-gray-500 leading-snug">
              {invoiced && <span>{t('orders.cart.invoicedAmount', { number: invoice.invoiceNumber })}</span>}
              {pendingInvoice && <span>{t('orders.cart.taxSetBelow')}</span>}
              {!invoiced && !pendingInvoice && <span>{t('orders.cart.taxEstimatedNote')}</span>}
            </div>
            <div className="text-right shrink-0">
              <span className="block text-[10px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500">
                {pendingInvoice ? t('orders.totalExclTax') : t('orders.totalInclTax')}
              </span>
              <span className="text-2xl font-black text-primary-600 dark:text-primary-400 tabular-nums leading-tight">
                {formatCurrency(pendingInvoice ? netTotal : totalTTC)}
              </span>
            </div>
          </div>
        </div>

        {/* Étape en cours : c'est la seule partie qui scrolle si le formulaire est trop long
            (facturation, encaissement). Le bouton reste accessible et les totaux restent
            visibles. */}
        <div className="min-h-0 overflow-y-auto px-3 pb-3 pt-2">
          {children}
        </div>
      </div>
    </aside>
  );
};

export default OrderWorkspaceCart;
