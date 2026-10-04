import { useState, useEffect, useCallback, useMemo } from 'react';
import api from '../services/api';

// Pas de backend pour l'état « lu », on le garde dans le navigateur.
const SEEN_KEY = 'gescom:notifications:seen';

const loadSeen = () => {
  try {
    const raw = localStorage.getItem(SEEN_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
};

const clientName = (order) =>
  order.client?.name
  || `${order.client?.firstName || ''} ${order.client?.lastName || ''}`.trim()
  || 'Client anonyme';

const orderNumber = (order) =>
  order.orderNumber || `CMD${String(order.id).padStart(5, '0')}`;

/**
 * Notifications calculées à partir des données déjà fournies par l'API (il n'y a pas de
 * système de notifications côté serveur) :
 *   - commandes en attente de confirmation (statut PENDING), pour tous les rôles ;
 *   - produits en rupture ou sous le seuil d'alerte, seulement pour l'ADMIN car la page
 *     /stock lui est réservée.
 *
 * Le hook renvoie des objets (kind + data) : le texte et la traduction sont faits dans le
 * composant (react-i18next), pour garder le multilingue.
 *
 * L'état « lu » est gardé dans le localStorage : une notification est non lue tant que son id
 * (toujours le même, ex. `order-42`) n'a pas été marqué comme vu.
 */
export const useNotifications = (user, { pollMs = 60000 } = {}) => {
  const isAdmin = user?.role === 'ADMIN';
  const [notifications, setNotifications] = useState([]);
  const [seen, setSeen] = useState(loadSeen);
  const [loading, setLoading] = useState(false);

  const build = useCallback((orders, products) => {
    const list = [];

    (orders || [])
      .filter((o) => o.status === 'PENDING')
      .forEach((o) => {
        list.push({
          id: `order-${o.id}`,
          kind: 'order',
          priority: 1,
          time: o.createdAt || o.orderDate || null,
          link: '/orders',
          data: { number: orderNumber(o), client: clientName(o) },
        });
      });

    (products || [])
      .filter((p) => p.active !== false)
      .forEach((p) => {
        const qty = p.stockQuantity ?? 0;
        const threshold = p.minStockAlert ?? 0;
        if (qty === 0) {
          list.push({
            id: `stock-out-${p.id}`,
            kind: 'stock-out',
            priority: 0,
            time: null,
            link: '/stock',
            data: { product: p.name, qty, threshold },
          });
        } else if (threshold > 0 && qty <= threshold) {
          list.push({
            id: `stock-low-${p.id}`,
            kind: 'stock-low',
            priority: 2,
            time: null,
            link: '/stock',
            data: { product: p.name, qty, threshold },
          });
        }
      });

    // D'abord les ruptures, puis les commandes à confirmer (les plus récentes en premier), puis le stock bas.
    list.sort((a, b) => {
      if (a.priority !== b.priority) return a.priority - b.priority;
      return new Date(b.time || 0) - new Date(a.time || 0);
    });
    return list;
  }, []);

  const refresh = useCallback(async () => {
    if (!user) {
      setNotifications([]);
      return;
    }
    try {
      setLoading(true);
      const requests = [api.get('/orders')];
      if (isAdmin) requests.push(api.get('/products'));
      const [ordersRes, productsRes] = await Promise.all(requests);
      setNotifications(build(ordersRes.data, productsRes?.data));
    } catch (error) {
      // Si les notifications échouent, on log juste l'erreur sans afficher de toast.
      console.error('Notifications: échec du chargement', error);
    } finally {
      setLoading(false);
    }
  }, [user, isAdmin, build]);

  useEffect(() => {
    refresh();
    if (!pollMs) return undefined;
    const id = setInterval(refresh, pollMs);
    return () => clearInterval(id);
  }, [refresh, pollMs]);

  const unreadCount = useMemo(
    () => notifications.filter((n) => !seen.includes(n.id)).length,
    [notifications, seen]
  );

  // Ouvrir le panneau = tout marquer comme lu. On enregistre les ids présents
  // (les notifications traitées disparaissent toutes seules, donc ça ne grossit pas).
  const markAllRead = useCallback(() => {
    const ids = notifications.map((n) => n.id);
    setSeen(ids);
    try {
      localStorage.setItem(SEEN_KEY, JSON.stringify(ids));
    } catch {
      /* localStorage indisponible : tant pis, l'état « lu » ne sera pas sauvegardé */
    }
  }, [notifications]);

  return { notifications, unreadCount, loading, markAllRead, refresh };
};

export default useNotifications;
