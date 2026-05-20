package com.gescom.backend.mapper;

import com.gescom.backend.dto.stock.StockMovementResponse;
import com.gescom.backend.entity.StockMovement;
import org.springframework.stereotype.Component;

@Component
public class StockMovementMapper {

    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    public StockMovementMapper(ProductMapper productMapper, UserMapper userMapper) {
        this.productMapper = productMapper;
        this.userMapper = userMapper;
    }

    public StockMovementResponse toResponse(StockMovement movement) {
        if (movement == null) return null;
        return new StockMovementResponse(
                movement.getId(),
                productMapper.toResponse(movement.getProduct()),
                movement.getType(),
                movement.getQuantity(),
                movement.getPreviousStock(),
                movement.getNewStock(),
                movement.getUnitCost(),
                movement.getReason(),
                movement.getReference(),
                movement.getUser() != null ? userMapper.toResponse(movement.getUser()) : null,
                movement.getCreatedAt()
        );
    }
}
