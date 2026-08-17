package com.gescom.backend.dto.order;

import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Critères de recherche de l'écran Commandes. Tous facultatifs, tous combinables.
 *
 * @param q             recherche libre : numéro de commande, client sous toutes ses formes,
 *                      opérateur, notes, et libellé/code/code-barres des articles commandés
 * @param status        statut de la commande
 * @param payment       statut de la facture liée ; {@code notInvoiced} traite le cas
 *                      « pas encore facturée », qui est une ABSENCE de facture et non un statut
 * @param notInvoiced   vrai pour ne garder que les commandes sans facture vivante
 * @param clientId      client précis
 * @param clientType    PARTICULIER ou ENTREPRISE
 * @param city          ville du client, valeur exacte
 * @param productId     commandes comportant cet article
 * @param categoryId    commandes comportant un article de cette famille
 * @param createdById   opérateur ayant saisi la vente
 * @param dateFrom      borne basse de création, incluse
 * @param dateTo        borne haute de création, incluse — la journée entière est prise
 * @param amountMin     montant TTC minimum — voir {@code OrderService.searchOrders} pour la
 *                      définition exacte de ce montant, qui n'est pas une colonne
 * @param amountMax     montant TTC maximum
 * @param notes         fragment recherché dans les notes seules
 * @param onlyDiscounted ne garder que les ventes portant une remise
 */
public record OrderSearchCriteria(
        String q,
        Order.OrderStatus status,
        Invoice.InvoiceStatus payment,
        boolean notInvoiced,
        Long clientId,
        Client.ClientType clientType,
        String city,
        Long productId,
        Long categoryId,
        Long createdById,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal amountMin,
        BigDecimal amountMax,
        String notes,
        boolean onlyDiscounted
) {
}
