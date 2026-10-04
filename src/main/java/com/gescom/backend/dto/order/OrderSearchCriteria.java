package com.gescom.backend.dto.order;

import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.Invoice;
import com.gescom.backend.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Filtres de l'écran Commandes. Tous facultatifs et combinables.
 *
 * @param q             recherche libre : numéro de commande, client (nom, société…),
 *                      utilisateur, notes, et nom/code/code-barres des articles
 * @param status        statut de la commande
 * @param payment       statut de la facture liée ; {@code notInvoiced} gère le cas
 *                      « pas encore facturée », qui veut dire PAS de facture, ce n'est pas un statut
 * @param notInvoiced   vrai pour garder seulement les commandes sans facture active
 * @param clientId      un client précis
 * @param clientType    PARTICULIER ou ENTREPRISE
 * @param city          ville du client, valeur exacte
 * @param productId     commandes qui contiennent cet article
 * @param categoryId    commandes qui contiennent un article de cette catégorie
 * @param createdById   utilisateur qui a saisi la vente
 * @param dateFrom      date de création minimum, incluse
 * @param dateTo        date de création maximum, incluse (toute la journée)
 * @param amountMin     montant TTC minimum (voir {@code OrderService.searchOrders} pour savoir
 *                      comment ce montant est calculé, ce n'est pas une colonne)
 * @param amountMax     montant TTC maximum
 * @param notes         texte cherché seulement dans les notes
 * @param onlyDiscounted garder seulement les ventes avec une remise
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
