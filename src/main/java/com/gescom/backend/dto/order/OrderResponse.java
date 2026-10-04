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
        // Statut de la facture liée (null s'il n'y en a pas) : sert à afficher « Payée » pour
        // une commande facturée et payée, dont le statut reste INVOICED jusqu'à la livraison.
        Invoice.InvoiceStatus invoiceStatus,
        // Total TTC de cette facture (null s'il n'y en a pas). Les montants de la commande sont
        // HT : sans ce champ, une liste de commandes ne pourrait afficher que du HT alors que le
        // client paie le TTC, et les deux écrans afficheraient des montants différents.
        BigDecimal invoiceTotalAmount,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
