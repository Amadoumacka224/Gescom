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
}
