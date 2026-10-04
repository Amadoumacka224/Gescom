package com.gescom.backend.dto.client;

import com.gescom.backend.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Réponse à une demande d'accès RGPD (art. 15) : toutes les données qu'on a sur UN client,
 * ses coordonnées et son historique d'achats.
 *
 * Le détail des lignes de commande n'est pas inclus exprès : ça concerne des produits et des
 * prix, pas la personne, et le client les a déjà sur ses factures.
 */
public record ClientDataExport(
        LocalDateTime generatedAt,
        ClientResponse client,
        List<OrderHistoryEntry> orders
) {

    /** Une commande, avec seulement ce qui concerne le client : quoi, quand, statut, combien. */
    public record OrderHistoryEntry(
            String orderNumber,
            LocalDateTime createdAt,
            Order.OrderStatus status,
            BigDecimal finalAmount
    ) {
    }
}
