package com.gescom.backend.dto.client;

import com.gescom.backend.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Réponse à une demande d'accès RGPD (art. 15) : l'ensemble des données détenues sur UN client,
 * ses coordonnées comme son historique d'achat.
 *
 * Le détail des lignes de commande n'y figure volontairement pas : il porte sur des produits et
 * des prix, pas sur la personne, et le demandeur en dispose déjà via ses factures.
 */
public record ClientDataExport(
        LocalDateTime generatedAt,
        ClientResponse client,
        List<OrderHistoryEntry> orders
) {

    /** Une commande, réduite à ce qui concerne le client : quoi, quand, où en est-elle, combien. */
    public record OrderHistoryEntry(
            String orderNumber,
            LocalDateTime createdAt,
            Order.OrderStatus status,
            BigDecimal finalAmount
    ) {
    }
}
