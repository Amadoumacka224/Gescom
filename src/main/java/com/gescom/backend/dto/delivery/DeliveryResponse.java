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
    public static DeliveryResponse from(Delivery delivery) {
        if (delivery == null) return null;
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getDeliveryNumber(),
                OrderResponse.from(delivery.getOrder()),
                delivery.getDeliveryAddress(),
                delivery.getDeliveryCity(),
                delivery.getDeliveryPostalCode(),
                delivery.getDeliveryCountry(),
                delivery.getContactName(),
                delivery.getContactPhone(),
                delivery.getStatus(),
                delivery.getScheduledDate(),
                delivery.getDeliveredDate(),
                delivery.getDeliveredBy(),
                delivery.getNotes(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}
