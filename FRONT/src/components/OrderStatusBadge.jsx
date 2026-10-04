import { useTranslation } from 'react-i18next';
import { ORDER_STATUS_TONE, badgeClass, resolveOrderStatusKey } from '../constants/statusBadges';

/**
 * Badge de statut d'une commande, qui tient compte du paiement de la facture.
 *
 * Une commande facturée et payée affiche « Payée » (« Acompte versé » si payée en partie),
 * même si son statut reste INVOICED jusqu'à la livraison. Ce calcul était copié (et pas
 * pareil) entre la caisse et la supervision, il est maintenant dans `constants/statusBadges`
 * avec les couleurs.
 *
 * `invoice` sert à passer une facture chargée à part (détail d'une commande). Sinon on utilise
 * `order.invoiceStatus` renvoyé par les listes.
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
