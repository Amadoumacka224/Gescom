package com.gescom.backend.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Données du tableau de bord.
 *
 * Un record typé plutôt qu'une {@code Map<String, Object>} : l'écran lit vingt-quatre champs,
 * et avec une Map on ne sait ni lesquels ni leur type. Renommer une clé cassait le tableau de
 * bord sans aucune erreur, et Swagger ne documentait rien.
 *
 * <h2>Trois règles à respecter, car l'écran affiche les chiffres côte à côte</h2>
 *
 * <ol>
 *   <li>les cinq compteurs de commandes font {@code totalOrders} au total ;</li>
 *   <li>les quatre compteurs de factures font {@code totalInvoices} au total ;</li>
 *   <li>{@code pendingAmount = invoicedAmount − totalRevenue}, et les trois montants portent
 *       sur les MÊMES factures : celles qui ne sont pas annulées. Une facture annulée ne
 *       compte plus. Compter ce qui a été payé dessus sans compter ce qui reste donnerait un
 *       taux qui ne veut rien dire.</li>
 * </ol>
 *
 * Ces règles sont maintenant toujours vraies : chaque bloc vient d'UNE seule requête, alors
 * qu'avant le calcul en mémoire devait les corriger à la main.
 *
 * @param totalSales     montant des commandes, sans les commandes annulées
 * @param ordersToSchedule commandes facturées sans livraison (à planifier)
 * @param topStockProducts produits avec le plus de stock ; ce n'est PAS un classement des ventes
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
     * Ligne du bloc « dernières commandes ». {@code invoiceStatus} sert à afficher « Payée »
     * pour une commande facturée et payée, dont le statut reste INVOICED jusqu'à la livraison.
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

    /** Ligne des deux classements de stock. */
    public record StockLine(Long id, String name, int stock) {
    }
}
