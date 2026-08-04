package com.gescom.backend.dto.stock;

import com.gescom.backend.dto.client.ClientResponse;
import com.gescom.backend.dto.user.UserResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Retour client tel qu'affiché dans le registre.
 *
 * La vente d'origine est représentée par ses seuls numéros et non par un {@code OrderResponse}
 * complet : le registre a besoin de savoir de quelle vente il s'agit, pas d'en réembarquer
 * toutes les lignes. Les lignes de retour, elles, sont détaillées — elles sont l'objet du
 * document. Elles sont absentes des réponses de liste, où seul l'entête est chargé.
 *
 * Le client est porté en entier — et pas seulement par son libellé — parce que la note de
 * crédit éditée depuis la fiche en reprend l'adresse, comme la facture le fait de son côté.
 */
public record StockReturnResponse(
        Long id,
        String returnNumber,
        Long orderId,
        String orderNumber,
        Long invoiceId,
        String invoiceNumber,
        ClientResponse client,
        String clientName,
        // Taux de TVA de la facture d'origine, pour que l'avoir applique celui de la vente et
        // non le taux courant des réglages. Null si la vente n'a jamais été facturée.
        BigDecimal taxRate,
        Integer totalQuantity,
        BigDecimal refundAmount,
        String notes,
        UserResponse createdBy,
        LocalDateTime createdAt,
        List<StockReturnItemResponse> items
) {
}
