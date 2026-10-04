package com.gescom.backend.dto.stock;

import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vente retrouvée à partir d'un numéro de commande ou de facture, prête pour un retour.
 *
 * Contient tout ce que l'écran de retour doit afficher : la vente, le client, la facture s'il
 * y en a une, et surtout les lignes avec la quantité qu'on peut encore retourner (c'est elle
 * qui limite la saisie).
 */
public record ReturnLookupResponse(
        Long orderId,
        String orderNumber,
        Order.OrderStatus orderStatus,
        LocalDateTime orderDate,
        BigDecimal orderAmount,
        Long clientId,
        String clientName,
        String cashierName,
        Long invoiceId,
        String invoiceNumber,
        Invoice.InvoiceStatus invoiceStatus,
        LocalDate invoiceDate,
        // Nombre de retours déjà faits sur cette vente : on voit tout de suite qu'il y a déjà
        // eu un retour, sans ouvrir l'historique.
        long previousReturns,
        List<ReturnableItemResponse> items
) {
}
