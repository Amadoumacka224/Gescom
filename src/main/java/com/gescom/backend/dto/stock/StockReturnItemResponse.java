package com.gescom.backend.dto.stock;

import com.gescom.backend.dto.product.ProductResponse;
import com.gescom.backend.entity.StockReturnItem;

import java.math.BigDecimal;

public record StockReturnItemResponse(
        Long id,
        ProductResponse product,
        ProductResponse replacementProduct,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal refundAmount,
        StockReturnItem.ReturnReason reason,
        StockReturnItem.ReturnTreatment treatment
) {
}
