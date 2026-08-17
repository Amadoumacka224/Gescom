package com.gescom.backend.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aperçu du tableau de bord.
 *
 * Enregistrement typé plutôt que {@code Map<String, Object>} : l'écran lit vingt-quatre champs,
 * et une carte ne dit ni lesquels, ni de quel type. Renommer une clé y cassait le tableau de
 * bord en silence, et Swagger n'en documentait rien.
 *
 * <h2>Trois invariants que l'écran affiche côte à côte</h2>
 *
 * <ol>
 *   <li>les cinq décomptes de commandes totalisent {@code totalOrders} ;</li>
 *   <li>les quatre décomptes de factures totalisent {@code totalInvoices} ;</li>
 *   <li>{@code pendingAmount = invoicedAmount − totalRevenue}, les trois montants décrivant le
 *       MÊME ensemble : les factures non annulées. Une facture annulée sort des livres — compter
 *       son encaissement sans compter son reliquat donnerait un taux qui n'est celui d'aucun
 *       périmètre.</li>
 * </ol>
 *
 * Ces invariants sont désormais garantis par construction : chaque bloc vient d'UNE requête
 * d'agrégation, là où le calcul en mémoire les rétablissait à la main.
 *
 * @param totalSales     chiffre d'affaires commandé, commandes annulées exclues
 * @param ordersToSchedule commandes facturées sans livraison — le reste à planifier
 * @param topStockProducts produits les mieux approvisionnés ; ce n'est PAS un palmarès de ventes
 */
public record DashboardOverview(
        // --- Commandes ---
        BigDecimal totalSales,
        long totalOrders,
        long pendingOrders,
        long confirmedOrders,
        long invoicedOrders,
        long deliveredOrders,
        long canceledOrders,
        long totalClients,
        long lowStock,

        // --- Factures ---
        long totalInvoices,
        BigDecimal totalRevenue,
        BigDecimal invoicedAmount,
        BigDecimal pendingAmount,
        long unpaidInvoices,
        long partiallyPaidInvoices,
        long paidInvoices,
        long canceledInvoices,

        // --- Livraisons ---
        long totalDeliveries,
        long pendingDeliveries,
        long deliveredDeliveries,
        long ordersToSchedule,

        // --- Listes courtes ---
        List<RecentOrder> recentOrders,
        List<StockLine> topStockProducts,
        List<StockLine> lowStockProducts
) {
    /**
     * Ligne du bloc « dernières commandes ». {@code invoiceStatus} permet d'afficher « Payée »
     * sur une commande facturée puis réglée, dont le statut de commande reste INVOICED jusqu'à
     * la livraison.
     */
    public record RecentOrder(
            Long id,
            String orderNumber,
            String clientName,
            BigDecimal finalAmount,
            String status,
            String createdAt,
            int itemsCount,
            String invoiceStatus
    ) {
    }

    /** Ligne des deux palmarès de stock. */
    public record StockLine(Long id, String name, int stock) {
    }
}
