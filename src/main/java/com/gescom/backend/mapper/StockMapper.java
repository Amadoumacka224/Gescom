package com.gescom.backend.mapper;

import com.gescom.backend.dto.stock.StockMovementResponse;
import com.gescom.backend.dto.stock.StockReturnItemResponse;
import com.gescom.backend.dto.stock.StockReturnResponse;
import com.gescom.backend.entity.Client;
import com.gescom.backend.entity.StockMovement;
import com.gescom.backend.entity.StockReturn;
import com.gescom.backend.entity.StockReturnItem;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Conversions du stock : mouvements du registre et retours clients.
 *
 * Les deux vues décrivent le même flux — un retour produit des mouvements — et partagent les
 * mêmes référentiels (produit, utilisateur, client), d'où un seul mapper.
 */
@Component
public class StockMapper {

    private final ReferenceMapper referenceMapper;

    public StockMapper(ReferenceMapper referenceMapper) {
        this.referenceMapper = referenceMapper;
    }

    // ---------------------------------------------------------------- Mouvements

    public StockMovementResponse toResponse(StockMovement movement) {
        if (movement == null) return null;
        return new StockMovementResponse(
                movement.getId(),
                referenceMapper.toResponse(movement.getProduct()),
                movement.getType(),
                movement.getQuantity(),
                movement.getPreviousStock(),
                movement.getNewStock(),
                movement.getUnitCost(),
                movement.getReason(),
                movement.getReference(),
                referenceMapper.toResponse(movement.getUser()),
                movement.getCreatedAt()
        );
    }

    // ---------------------------------------------------------------- Retours

    /** Réponse d'entête, sans les lignes : format des listes paginées, qui ne les chargent pas. */
    public StockReturnResponse toReturnSummary(StockReturn stockReturn) {
        return toReturnResponse(stockReturn, null);
    }

    /** Réponse complète, lignes comprises. */
    public StockReturnResponse toReturnResponse(StockReturn stockReturn) {
        if (stockReturn == null) return null;
        return toReturnResponse(stockReturn,
                stockReturn.getItems().stream().map(this::toItemResponse).toList());
    }

    private StockReturnResponse toReturnResponse(StockReturn stockReturn, List<StockReturnItemResponse> items) {
        if (stockReturn == null) return null;
        Client client = stockReturn.getOrder() != null ? stockReturn.getOrder().getClient() : null;
        return new StockReturnResponse(
                stockReturn.getId(),
                stockReturn.getReturnNumber(),
                stockReturn.getOrder() != null ? stockReturn.getOrder().getId() : null,
                stockReturn.getOrder() != null ? stockReturn.getOrder().getOrderNumber() : null,
                stockReturn.getInvoice() != null ? stockReturn.getInvoice().getId() : null,
                stockReturn.getInvoice() != null ? stockReturn.getInvoice().getInvoiceNumber() : null,
                referenceMapper.toResponse(client),
                ReferenceMapper.clientName(client),
                stockReturn.getInvoice() != null ? stockReturn.getInvoice().getTaxRate() : null,
                stockReturn.getTotalQuantity(),
                stockReturn.getRefundAmount(),
                stockReturn.getNotes(),
                referenceMapper.toResponse(stockReturn.getCreatedBy()),
                stockReturn.getCreatedAt(),
                items
        );
    }

    private StockReturnItemResponse toItemResponse(StockReturnItem item) {
        return new StockReturnItemResponse(
                item.getId(),
                referenceMapper.toResponse(item.getProduct()),
                referenceMapper.toResponse(item.getReplacementProduct()),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getRefundAmount(),
                item.getReason(),
                item.getTreatment()
        );
    }
}
