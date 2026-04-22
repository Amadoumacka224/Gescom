package com.gescom.backend.dto.product;

import com.gescom.backend.dto.category.CategoryResponse;
import com.gescom.backend.entity.Product;

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
    public static ProductResponse from(Product product) {
        if (product == null) return null;
        return new ProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getDescription(),
                product.getPurchasePrice(),
                product.getSellingPrice(),
                CategoryResponse.from(product.getCategory()),
                product.getUnit(),
                product.getStockQuantity(),
                product.getMinStockAlert(),
                product.getBarcode(),
                product.getImageUrl(),
                product.getActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
