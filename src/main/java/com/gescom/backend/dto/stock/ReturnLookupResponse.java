package com.gescom.backend.dto.stock;

import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vente retrouvée à partir d'un numéro de commande ou de facture, prête à être retournée.
 *
 * Réunit en une réponse ce que l'écran de retour doit afficher : l'identité de la vente, son
 * client, sa facture éventuelle, et surtout ses lignes assorties de la quantité encore
 * retournable — c'est cette dernière qui borne la saisie.
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
        // Nombre de retours déjà enregistrés sur cette vente : signale d'emblée un dossier
        // qui a déjà bougé, sans avoir à ouvrir l'historique.
        long previousReturns,
        List<ReturnableItemResponse> items
) {
}
