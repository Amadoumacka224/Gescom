import {
  Boxes, Calendar, FileText, Mail, MapPin, Package, Phone, User, UserCircle,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import OrderStatusBadge from './OrderStatusBadge';
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
 */

/** Date et heure dans la convention de la langue active. */
const formatDateTime = (value) =>
  value
    ? new Date(value).toLocaleDateString(i18n.t('export.locale'), {
        day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
      })
    : '—';

const Field = ({ icon: Icon, label, children }) => (
  <div className="min-w-0">
    <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500">{label}</p>
    <p className="mt-0.5 flex items-center gap-1.5 text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
      {Icon && <Icon className="w-3.5 h-3.5 text-gray-400 shrink-0" aria-hidden="true" />}
      {children}
    </p>
  </div>
);

const OrderWorkspaceRecap = ({ order, invoice, invoiceLoading }) => {
  const { t } = useTranslation();
  const items = order.items || [];
  const unitCount = items.reduce((n, i) => n + (parseInt(i.quantity) || 0), 0);

  return (
    <div className="flex-1 min-w-0 overflow-y-auto pr-1 space-y-3">
      {/* Identité de la commande */}
      <section className="bg-gray-50 dark:bg-gray-900/40 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <Field label={t('orders.orderNumber')}>{order.orderNumber}</Field>
          <Field icon={Calendar} label={t('orders.recap.createdOn')}>{formatDateTime(order.createdAt)}</Field>
          <Field icon={UserCircle} label={t('orders.recap.createdBy')}>
            {order.createdBy
              ? [order.createdBy.firstName, order.createdBy.lastName].filter(Boolean).join(' ') || order.createdBy.username
              : '—'}
          </Field>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500">{t('orders.status')}</p>
            <div className="mt-1">
              <OrderStatusBadge order={order} invoice={invoice} />
            </div>
          </div>
        </div>
      </section>

      {/* Client */}
      <section className="bg-gray-50 dark:bg-gray-900/40 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
        <h3 className="subsection-title flex items-center gap-2 mb-3">
          <User className="w-4 h-4 text-primary-600 dark:text-primary-400" aria-hidden="true" />
          {t('orders.client')}
        </h3>
        {order.client ? (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <Field label={t('clients.name')}>{order.client.firstName} {order.client.lastName}</Field>
            <Field icon={Mail} label={t('common.email')}>{order.client.email || '—'}</Field>
            <Field icon={Phone} label={t('common.phone')}>{order.client.phone || '—'}</Field>
            <Field icon={MapPin} label={t('common.address')}>{order.client.address || '—'}</Field>
          </div>
        ) : (
          <p className="flex items-center gap-3 text-sm">
            <span className="badge-neutral">{t('orders.walkInClient')}</span>
            <span className="text-gray-500 dark:text-gray-400">{t('orders.walkInClientHint')}</span>
          </p>
        )}
      </section>

      {/* Lignes vendues */}
      <section className="bg-gray-50 dark:bg-gray-900/40 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
        <h3 className="subsection-title flex items-center gap-2 px-4 py-3 border-b border-gray-200 dark:border-gray-700">
          <Package className="w-4 h-4 text-primary-600 dark:text-primary-400" aria-hidden="true" />
          {t('orders.recap.itemsHeading', { count: items.length })}
        </h3>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="text-[11px] uppercase tracking-wide text-gray-400 dark:text-gray-500">
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
                      <span className="block text-[11px] text-gray-400 dark:text-gray-500">{item.product.code}</span>
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
        <section className="bg-gray-50 dark:bg-gray-900/40 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
          <h3 className="subsection-title flex items-center gap-2 mb-3">
            <FileText className="w-4 h-4 text-primary-600 dark:text-primary-400" aria-hidden="true" />
            {t('invoices.sectionInvoice')}
          </h3>
          {invoice ? (
            <>
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <Field label={t('orders.recap.invoiceNumber')}>{invoice.invoiceNumber}</Field>
                <Field label={t('orders.recap.issuedOn')}>
                  {formatDate(invoice.invoiceDate)}
                </Field>
                <Field label={t('orders.recap.dueOn')}>
                  {formatDate(invoice.dueDate)}
                </Field>
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500">
                    {t('orders.recap.settlement')}
                  </p>
                  <div className="mt-1">
                    <span className={badgeClass(INVOICE_STATUS_TONE[invoice.status])}>
                      {invoice.status ? t(`status.invoice.${invoice.status}`, invoice.status) : '—'}
                    </span>
                  </div>
                </div>
              </div>
              <dl className="mt-4 pt-3 border-t border-gray-200 dark:border-gray-700 grid grid-cols-2 lg:grid-cols-4 gap-4 text-sm">
                <div>
                  <dt className="text-xs text-gray-500 dark:text-gray-400">{t('orders.subtotalExclTax')}</dt>
                  <dd className="font-semibold text-gray-900 dark:text-gray-100 tabular-nums">
                    {formatCurrency(invoice.subtotal)}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-gray-500 dark:text-gray-400">{t('orders.taxWithRate', { rate: Number(invoice.taxRate || 0).toFixed(2) })}</dt>
                  <dd className="font-semibold text-gray-900 dark:text-gray-100 tabular-nums">
                    {formatCurrency(invoice.taxAmount)}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-gray-500 dark:text-gray-400">{t('orders.totalInclTax')}</dt>
                  <dd className="font-semibold text-gray-900 dark:text-gray-100 tabular-nums">
                    {formatCurrency(invoice.totalAmount)}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-gray-500 dark:text-gray-400">{t('orders.remainingDue')}</dt>
                  <dd className="font-bold text-amber-600 dark:text-amber-400 tabular-nums">
                    {formatCurrency(invoice.remainingAmount)}
                  </dd>
                </div>
              </dl>
            </>
          ) : (
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('orders.recap.loadingInvoice')}</p>
          )}
        </section>
      )}
    </div>
  );
};

export default OrderWorkspaceRecap;
