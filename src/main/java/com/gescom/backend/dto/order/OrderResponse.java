package com.gescom.backend.dto.order;

import com.gescom.backend.dto.client.ClientResponse;
import com.gescom.backend.dto.user.UserResponse;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        ClientResponse client,
        UserResponse createdBy,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        BigDecimal discount,
        // Net HT à facturer : totalAmount − discount.
        BigDecimal finalAmount,
        Order.OrderStatus status,
        // Statut de la facture liée (null si aucune) — permet d'afficher « Payée » sur une commande
        // facturée puis réglée, son statut de commande restant INVOICED jusqu'à la livraison.
        Invoice.InvoiceStatus invoiceStatus,
        // Total TTC de cette facture (null si aucune). Les montants de la commande sont hors taxes :
        // sans ce champ, une liste de commandes ne peut afficher que du HT, là où le client, lui,
        // paie le TTC — et les deux écrans annoncent alors deux chiffres différents.
        BigDecimal invoiceTotalAmount,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
