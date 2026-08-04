package com.gescom.backend.mapper;

import com.gescom.backend.dto.stock.StockReturnItemResponse;
import com.gescom.backend.dto.stock.StockReturnResponse;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.StockReturn;
import com.gescom.backend.entity.StockReturnItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StockReturnMapper {

    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final ClientMapper clientMapper;

    public StockReturnMapper(ProductMapper productMapper, UserMapper userMapper, ClientMapper clientMapper) {
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.clientMapper = clientMapper;
    }

    /** Réponse d'entête, sans les lignes : format des listes paginées, qui ne les chargent pas. */
    public StockReturnResponse toSummary(StockReturn stockReturn) {
        return toResponse(stockReturn, null);
    }

    /** Réponse complète, lignes comprises. */
    public StockReturnResponse toResponse(StockReturn stockReturn) {
        if (stockReturn == null) return null;
        return toResponse(stockReturn, stockReturn.getItems().stream().map(this::toItemResponse).toList());
    }

    private StockReturnResponse toResponse(StockReturn stockReturn, List<StockReturnItemResponse> items) {
        if (stockReturn == null) return null;
        Client client = stockReturn.getOrder() != null ? stockReturn.getOrder().getClient() : null;
        return new StockReturnResponse(
                stockReturn.getId(),
                stockReturn.getReturnNumber(),
                stockReturn.getOrder() != null ? stockReturn.getOrder().getId() : null,
                stockReturn.getOrder() != null ? stockReturn.getOrder().getOrderNumber() : null,
                stockReturn.getInvoice() != null ? stockReturn.getInvoice().getId() : null,
                stockReturn.getInvoice() != null ? stockReturn.getInvoice().getInvoiceNumber() : null,
                clientMapper.toResponse(client),
                clientName(client),
                stockReturn.getInvoice() != null ? stockReturn.getInvoice().getTaxRate() : null,
                stockReturn.getTotalQuantity(),
                stockReturn.getRefundAmount(),
                stockReturn.getNotes(),
                stockReturn.getCreatedBy() != null ? userMapper.toResponse(stockReturn.getCreatedBy()) : null,
                stockReturn.getCreatedAt(),
                items
        );
    }

    private StockReturnItemResponse toItemResponse(StockReturnItem item) {
        return new StockReturnItemResponse(
                item.getId(),
                productMapper.toResponse(item.getProduct()),
                item.getReplacementProduct() != null ? productMapper.toResponse(item.getReplacementProduct()) : null,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getRefundAmount(),
                item.getReason(),
                item.getTreatment()
        );
    }

    /**
     * Libellé du client d'une vente. Null pour une vente de passage — le client n'est pas
     * obligatoire sur une commande, et l'écran affiche alors un tiret plutôt qu'un nom vide.
     */
    public static String clientName(Client client) {
        if (client == null) return null;
        String name = ((client.getFirstName() != null ? client.getFirstName() : "") + " "
                + (client.getLastName() != null ? client.getLastName() : "")).trim();
        if (!name.isEmpty()) return name;
        return client.getCompany();
    }
}
