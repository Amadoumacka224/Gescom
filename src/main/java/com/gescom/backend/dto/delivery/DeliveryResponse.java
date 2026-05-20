package com.gescom.backend.dto.delivery;

import com.gescom.backend.dto.order.OrderResponse;
import com.gescom.backend.entity.Delivery;

import java.time.LocalDateTime;

public record DeliveryResponse(
        Long id,
        String deliveryNumber,
        OrderResponse order,
        String deliveryAddress,
        String deliveryCity,
        String deliveryPostalCode,
        String deliveryCountry,
        String contactName,
        String contactPhone,
        Delivery.DeliveryStatus status,
        LocalDateTime scheduledDate,
        LocalDateTime deliveredDate,
        String deliveredBy,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
