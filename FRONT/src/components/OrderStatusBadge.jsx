import { useTranslation } from 'react-i18next';
import { ORDER_STATUS_TONE, badgeClass, resolveOrderStatusKey } from '../constants/statusBadges';

/**
 * Badge de statut d'une commande, tenant compte du règlement de sa facture.
 *
 * Une commande facturée dont la facture est soldée s'affiche « Payée » (« Acompte versé » si
 * partiellement réglée), alors que son statut de cycle de vie reste INVOICED jusqu'à la
 * livraison. Cette résolution était dupliquée — et divergente — entre la caisse et la
 * supervision : elle vit désormais dans `constants/statusBadges`, tout comme les couleurs.
 *
 * `invoice` permet de passer une facture chargée séparément (écran de détail d'une commande),
 * sinon on retombe sur `order.invoiceStatus` fourni par les listes.
 */
const OrderStatusBadge = ({ order, invoice = null, icon: Icon = null }) => {
  const { t } = useTranslation();
  const key = resolveOrderStatusKey(order, invoice);

  return (
    <span className={badgeClass(ORDER_STATUS_TONE[key])}>
      {Icon && <Icon className="w-3 h-3" aria-hidden="true" />}
      {t(`status.order.${key}`)}
    </span>
  );
};

export default OrderStatusBadge;
