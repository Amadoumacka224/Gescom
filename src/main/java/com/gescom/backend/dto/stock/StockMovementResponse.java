package com.gescom.backend.dto.stock;

import com.gescom.backend.dto.product.ProductResponse;
import com.gescom.backend.dto.user.UserResponse;
import com.gescom.backend.entity.StockMovement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockMovementResponse(
        Long id,
        ProductResponse product,
        StockMovement.MovementType type,
        Integer quantity,
        Integer previousStock,
        Integer newStock,
        BigDecimal unitCost,
        String reason,
        String reference,
        UserResponse user,
        LocalDateTime createdAt
) {
    public static StockMovementResponse from(StockMovement movement) {
        if (movement == null) return null;
        return new StockMovementResponse(
                movement.getId(),
                ProductResponse.from(movement.getProduct()),
                movement.getType(),
                movement.getQuantity(),
                movement.getPreviousStock(),
                movement.getNewStock(),
                movement.getUnitCost(),
                movement.getReason(),
                movement.getReference(),
                movement.getUser() != null ? UserResponse.from(movement.getUser()) : null,
                movement.getCreatedAt()
        );
    }
}
