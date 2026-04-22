package com.gescom.backend.dto.order;

import com.gescom.backend.dto.client.ClientResponse;
import com.gescom.backend.dto.user.UserResponse;
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
        BigDecimal tax,
        BigDecimal finalAmount,
        Order.OrderStatus status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrderResponse from(Order order) {
        if (order == null) return null;
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                ClientResponse.from(order.getClient()),
                order.getCreatedBy() != null ? UserResponse.from(order.getCreatedBy()) : null,
                order.getItems() != null
                        ? order.getItems().stream().map(OrderItemResponse::from).toList()
                        : List.of(),
                order.getTotalAmount(),
                order.getDiscount(),
                order.getTax(),
                order.getFinalAmount(),
                order.getStatus(),
                order.getNotes(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
