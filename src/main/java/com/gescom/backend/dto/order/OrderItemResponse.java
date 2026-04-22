package com.gescom.backend.dto.order;

import com.gescom.backend.dto.product.ProductResponse;
import com.gescom.backend.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        ProductResponse product,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BigDecimal discount
) {
    public static OrderItemResponse from(OrderItem item) {
        if (item == null) return null;
        return new OrderItemResponse(
                item.getId(),
                ProductResponse.from(item.getProduct()),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getDiscount()
        );
    }
}
