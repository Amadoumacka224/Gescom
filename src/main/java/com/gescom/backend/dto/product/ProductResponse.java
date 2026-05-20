package com.gescom.backend.dto.product;

import com.gescom.backend.dto.category.CategoryResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal purchasePrice,
        BigDecimal sellingPrice,
        CategoryResponse category,
        String unit,
        Integer stockQuantity,
        Integer minStockAlert,
        String barcode,
        String imageUrl,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
