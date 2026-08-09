import {
  Boxes, Calendar, FileText, Mail, MapPin, Package, Phone, User, UserCircle,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import OrderStatusBadge from './OrderStatusBadge';
import AmountRow from './AmountRow';
import InfoRow from './InfoRow';
import KeyFact from './KeyFact';
import { INVOICE_STATUS_TONE, badgeClass } from '../constants/statusBadges';
import { formatCurrency, formatDate } from '../utils/format';
import i18n from '../i18n';

/**
 * Dossier de la commande, affiché à la place de la grille produits une fois les lignes figées.
 *
 * Passé la confirmation, proposer un catalogue serait un piège : les articles ne peuvent plus
 * changer (`OrderService.updateOrder` exige PENDING). La colonne centrale devient donc ce
 * qu'on a réellement besoin de relire à ce stade — pièce comptable, client, lignes vendues et
 * état du règlement — pendant que le panier, à droite, continue de piloter les étapes.
 *
 * La lecture suit la même hiérarchie que les autres écrans de détail : ce que le dossier vaut
 * et où il en est d'abord, le détail ensuite.
 */

/** Date et heure dans la convention de la langue active. */
const formatDateTime = (value) =>
  value
    ? new Date(value).toLocaleDateString(i18n.t('export.locale'), {
        day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
      })
    : '—';

/** Titre de section du dossier, même gabarit pour les trois blocs. */
const SectionTitle = ({ icon: Icon, children, action }) => (
  <h3 className="flex flex-wrap items-center gap-2 px-4 py-3 border-b border-gray-200 dark:border-gray-700">
    {Icon && <Icon className="w-4 h-4 text-primary-600 dark:text-primary-400 shrink-0" aria-hidden="true" />}
    <span className="subsection-title">{children}</span>
    {action && <span className="ml-auto">{action}</span>}
  </h3>
);

const OrderWorkspaceRecap = ({ order, invoice, invoiceLoading }) => {
  const { t } = useTranslation();
  const items = order.items || [];
  const unitCount = items.reduce((n, i) => n + (parseInt(i.quantity) || 0), 0);
  const client = order.client;

  // Une facture annulée reste affichée — c'est une information — mais elle ne porte plus ni le
  // montant de référence ni le reliquat : il n'y a plus rien à encaisser dessus.
  const liveInvoice = invoice && invoice.status !== 'CANCELED' ? invoice : null;
  const grossTotal = Number(order.totalAmount || 0);
  const orderDiscount = Number(order.discount || 0);
  const netTotal = Number(order.finalAmount ?? (grossTotal - orderDiscount));
  const paid = Number(liveInvoice?.paidAmount || 0);
  const remaining = liveInvoice
    ? Number(liveInvoice.remainingAmount ?? (Number(liveInvoice.totalAmount || 0) - paid))
    : 0;

  const author = order.createdBy
    ? [order.createdBy.firstName, order.createdBy.lastName].filter(Boolean).join(' ')
      || order.createdBy.username
    : '—';

  const clientAddress = [
    client?.address,
    [client?.postalCode, client?.city].filter(Boolean).join(' '),
    client?.country,
  ].filter((part) => part && part.trim()).join(', ');

  // Mêmes règles d'empilement que le catalogue : sous `lg`, le panier sert sa hauteur avant ce
  // panneau, qui tombait sinon à quelques dizaines de pixels sur téléphone — et à zéro en
  // paysage, faisant disparaître les lignes et le récapitulatif de facture. Il ne se comprime
  // donc plus et laisse défiler la colonne entière ; son propre défilement ne sert qu'à partir
  // de `lg`, où il partage une hauteur fixe avec le panier.
  return (
    <div className="min-w-0 shrink-0 min-h-[24rem] lg:flex-1 lg:shrink lg:min-h-0 lg:overflow-y-auto pr-1 space-y-4">
      {/* En-tête du dossier : l'état et le montant, c'est-à-dire ce qu'on vient vérifier.
          Le montant n'y figurait pas du tout tant qu'aucune facture n'était émise. */}
      <section className="bg-gray-50 dark:bg-gray-900/40 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <OrderStatusBadge order={order} invoice={invoice} />
              <span className="font-mono text-xs text-gray-500 dark:text-gray-400">
                {order.orderNumber}
              </span>
            </div>
            <p className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
              <span className="inline-flex items-center gap-1.5">
                <Calendar className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                {t('orders.recap.createdOn')} {formatDateTime(order.createdAt)}
              </span>
              <span className="inline-flex items-center gap-1.5 min-w-0">
                <UserCircle className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                {t('orders.recap.createdBy')} {author}
              </span>
            </p>
          </div>

          <div className="shrink-0 text-right">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500">
              {liveInvoice ? t('orders.totalInclTax') : t('orders.totalExclTax')}
            </p>
            <p className="mt-1 text-2xl font-bold tabular-nums text-gray-900 dark:text-gray-100">
              {formatCurrency(liveInvoice ? liveInvoice.totalAmount : netTotal)}
            </p>
            {remaining > 0.001 && (
              <p className="mt-0.5 text-xs tabular-nums text-gray-500 dark:text-gray-400">
                {t('orders.remainingDue')} · {formatCurrency(remaining)}
              </p>
            )}
          </div>
        </div>
      </section>

      {/* Client */}
      <section className="bg-gray-50 dark:bg-gray-900/40 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
        <SectionTitle icon={User}>{t('orders.client')}</SectionTitle>
        {client ? (
          /* Deux colonnes : à gauche qui est le client, à droite comment le joindre et où le
             livrer. Répartir ainsi occupe la largeur du dossier avec de l'information réelle,
             là où une colonne unique laissait la moitié droite vide — sans y remettre des
             boutons de contact, hors sujet sur un dossier relu en cours de vente. */
          <div className="grid gap-4 p-4 sm:grid-cols-2 sm:gap-6">
            <div className="min-w-0">
              <p className="truncate text-base font-semibold text-gray-900 dark:text-gray-100">
                {`${client.firstName || ''} ${client.lastName || ''}`.trim()}
              </p>
              {client.company && (
                <p className="mt-0.5 truncate text-sm text-gray-500 dark:text-gray-400">{client.company}</p>
              )}
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <span className={client.type === 'ENTREPRISE' ? 'badge-accent' : 'badge-info'}>
                  {client.type === 'ENTREPRISE' ? t('clients.typeBusiness') : t('clients.typeIndividual')}
                </span>
                {/* Ancienneté du compte : sur une vente, savoir si l'on a affaire à un client
                    de longue date ou tout juste créé change la conversation. */}
                {client.createdAt && (
                  <span className="text-xs text-gray-400 dark:text-gray-500">
                    {t('clients.columnSince')} {formatDate(client.createdAt)}
                  </span>
                )}
              </div>
            </div>

            <dl className="min-w-0 space-y-3">
              <InfoRow icon={Mail} label={t('common.email')} value={client.email} />
              <InfoRow icon={Phone} label={t('common.phone')} value={client.phone} />
              {/* Adresse complète : seule la rue était affichée, ce qui ne suffit ni à livrer
                  ni à facturer. */}
              <InfoRow icon={MapPin} label={t('common.address')} value={clientAddress} />
            </dl>
          </div>
        ) : (
          <p className="flex flex-wrap items-center gap-3 p-4 text-sm">
            <span className="badge-neutral">{t('orders.walkInClient')}</span>
            <span className="text-gray-500 dark:text-gray-400">{t('orders.walkInClientHint')}</span>
          </p>
        )}
      </section>

      {/* Lignes vendues */}
      <section className="bg-gray-50 dark:bg-gray-900/40 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
        <SectionTitle icon={Package}>
          {t('orders.recap.itemsHeading', { count: items.length })}
        </SectionTitle>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-100/70 dark:bg-gray-900/60">
              <tr className="text-[11px] uppercase tracking-wide text-gray-500 dark:text-gray-400">
                <th scope="col" className="px-4 py-2 text-left font-semibold">{t('common.product')}</th>
                <th scope="col" className="px-4 py-2 text-right font-semibold">{t('orders.recap.price')}</th>
                <th scope="col" className="px-4 py-2 text-right font-semibold">{t('common.discount')}</th>
                <th scope="col" className="px-4 py-2 text-right font-semibold">{t('orders.recap.qtyShort')}</th>
                <th scope="col" className="px-4 py-2 text-right font-semibold">{t('common.total')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
              {items.map((item) => (
                <tr key={item.id ?? `${item.product?.id}-${item.quantity}`}>
                  <td className="px-4 py-2.5 text-sm text-gray-900 dark:text-gray-100">
                    <span className="font-medium">{item.product?.name || t('common.product')}</span>
                    {item.product?.code && (
                      <span className="block font-mono text-[11px] text-gray-400 dark:text-gray-500">
                        {item.product.code}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-2.5 text-sm text-right text-gray-600 dark:text-gray-300 tabular-nums">
                    {formatCurrency(item.unitPrice)}
                  </td>
                  <td className="px-4 py-2.5 text-sm text-right text-gray-600 dark:text-gray-300 tabular-nums">
                    {parseFloat(item.discount) > 0 ? `${parseFloat(item.discount).toFixed(2)} %` : '—'}
                  </td>
                  <td className="px-4 py-2.5 text-sm text-right text-gray-600 dark:text-gray-300 tabular-nums">
                    {item.quantity}
                  </td>
                  <td className="px-4 py-2.5 text-sm text-right font-semibold text-gray-900 dark:text-gray-100 tabular-nums">
                    {formatCurrency(item.totalPrice ?? (item.unitPrice * item.quantity))}
                  </td>
                </tr>
              ))}
            </tbody>
            {/* Totaux au pied du tableau, comme sur un document commercial : les lignes
                s'additionnaient sans que leur somme n'apparaisse nulle part. */}
            <tfoot className="border-t-2 border-gray-200 dark:border-gray-600">
              <tr>
                <td colSpan="4" className="px-4 py-2 text-right text-sm text-gray-600 dark:text-gray-400">
                  {t('orders.subtotalExclTax')}
                </td>
                <td className="px-4 py-2 text-right text-sm font-medium text-gray-900 dark:text-gray-100 tabular-nums">
                  {formatCurrency(grossTotal)}
                </td>
              </tr>
              {orderDiscount > 0 && (
                <tr>
                  <td colSpan="4" className="px-4 py-2 text-right text-sm text-gray-600 dark:text-gray-400">
                    {t('common.discount')}
                  </td>
                  <td className="px-4 py-2 text-right text-sm font-medium text-red-600 dark:text-red-400 tabular-nums">
                    −{formatCurrency(orderDiscount)}
                  </td>
                </tr>
              )}
              <tr>
                <td colSpan="4" className="px-4 py-2.5 text-right text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {t('orders.totalExclTax')}
                </td>
                <td className="px-4 py-2.5 text-right text-base font-bold text-gray-900 dark:text-gray-100 tabular-nums">
                  {formatCurrency(netTotal)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
        {/* Le stock est sorti à la confirmation, sous verrou, et restitué à l'annulation :
            le dire ici évite la question « faut-il ressaisir un mouvement de stock ? ». */}
        <p className="flex items-center gap-2 px-4 py-2.5 border-t border-gray-200 dark:border-gray-700 text-xs text-gray-500 dark:text-gray-400">
          <Boxes className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
          {t('orders.recap.stockNote', { count: unitCount })}
        </p>
      </section>

      {/* Facture */}
      {(invoice || invoiceLoading) && (
        <section className="bg-gray-50 dark:bg-gray-900/40 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
          <SectionTitle
            icon={FileText}
            action={invoice && (
              <span className={badgeClass(INVOICE_STATUS_TONE[invoice.status])}>
                {invoice.status ? t(`status.invoice.${invoice.status}`, invoice.status) : '—'}
              </span>
            )}
          >
            {t('invoices.sectionInvoice')}
          </SectionTitle>

          {invoice ? (
            <div className="p-4 space-y-4">
              <dl className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <KeyFact label={t('orders.recap.invoiceNumber')} value={invoice.invoiceNumber} />
                <KeyFact icon={Calendar} label={t('orders.recap.issuedOn')} value={formatDate(invoice.invoiceDate)} />
                <KeyFact icon={Calendar} label={t('orders.recap.dueOn')} value={formatDate(invoice.dueDate)} />
              </dl>

              {/* Colonne de montants unique, comme le récapitulatif du détail d'une facture :
                  les quatre chiffres étaient alignés côte à côte, sans hiérarchie entre le
                  total et ce qui reste dû. */}
              <div className="divide-y divide-gray-200 rounded-xl border border-gray-200 bg-white sm:ml-auto sm:max-w-sm dark:divide-gray-700 dark:border-gray-700 dark:bg-gray-800/40">
                <AmountRow label={t('orders.subtotalExclTax')} value={formatCurrency(invoice.subtotal)} />
                <AmountRow
                  label={t('orders.taxWithRate', { rate: Number(invoice.taxRate || 0).toFixed(2) })}
                  value={formatCurrency(invoice.taxAmount)}
                />
                <AmountRow label={t('orders.totalInclTax')} value={formatCurrency(invoice.totalAmount)} emphasis />
                {paid > 0 && (
                  <AmountRow
                    label={t('invoices.alreadyPaid')}
                    value={formatCurrency(paid)}
                    tone="text-green-600 dark:text-green-400"
                  />
                )}
                <AmountRow label={t('orders.remainingDue')} value={formatCurrency(remaining)} emphasis />
              </div>
            </div>
          ) : (
            <p className="p-4 text-sm text-gray-500 dark:text-gray-400">{t('orders.recap.loadingInvoice')}</p>
          )}
        </section>
      )}
    </div>
  );
};

export default OrderWorkspaceRecap;
