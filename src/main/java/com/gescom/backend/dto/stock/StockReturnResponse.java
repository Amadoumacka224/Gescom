package com.gescom.backend.dto.stock;

import com.gescom.backend.dto.client.ClientResponse;
import com.gescom.backend.dto.user.UserResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Retour client tel qu'il apparaît dans la liste.
 *
 * La vente d'origine est donnée juste par ses numéros et pas par un {@code OrderResponse}
 * complet : on a besoin de savoir de quelle vente il s'agit, pas de renvoyer toutes ses
 * lignes. Les lignes du retour, par contre, sont détaillées, c'est le contenu du document.
 * Elles ne sont pas dans les réponses de liste, où on ne charge que l'en-tête.
 *
 * Le client est renvoyé en entier (pas juste son nom) parce que l'avoir généré depuis la
 * fiche reprend son adresse, comme la facture.
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
        // Taux de TVA de la facture d'origine, pour que l'avoir utilise celui de la vente et
        // pas le taux actuel des paramètres. Null si la vente n'a jamais été facturée.
        BigDecimal taxRate,
        Integer totalQuantity,
        BigDecimal refundAmount,
        String notes,
        UserResponse createdBy,
        LocalDateTime createdAt,
        List<StockReturnItemResponse> items
) {
}
