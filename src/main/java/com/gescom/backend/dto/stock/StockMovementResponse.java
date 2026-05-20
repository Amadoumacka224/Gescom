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
}
