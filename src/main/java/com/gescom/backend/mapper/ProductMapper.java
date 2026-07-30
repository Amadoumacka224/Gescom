package com.gescom.backend.mapper;

import com.gescom.backend.dto.product.ProductRequest;
import com.gescom.backend.dto.product.ProductResponse;
import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.Product;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.CategoryRepository;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    private final CategoryMapper categoryMapper;
    private final CategoryRepository categoryRepository;

    public ProductMapper(CategoryMapper categoryMapper, CategoryRepository categoryRepository) {
        this.categoryMapper = categoryMapper;
        this.categoryRepository = categoryRepository;
    }

    public ProductResponse toResponse(Product product) {
        if (product == null) return null;
        return new ProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getDescription(),
                product.getPurchasePrice(),
                product.getSellingPrice(),
                categoryMapper.toResponse(product.getCategory()),
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

    public Product toEntity(ProductRequest request) {
        return applyRequest(new Product(), request);
    }

    public Product applyRequest(Product target, ProductRequest request) {
        if (request.code() != null && !request.code().isBlank()) {
            target.setCode(request.code());
        }
        target.setName(request.name());
        target.setDescription(request.description());
        target.setPurchasePrice(request.purchasePrice());
        target.setSellingPrice(request.sellingPrice());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("category", request.categoryId()));
            target.setCategory(category);
        } else {
            target.setCategory(null);
        }

        if (request.unit() != null) target.setUnit(request.unit());
        if (request.stockQuantity() != null) target.setStockQuantity(request.stockQuantity());
        if (request.minStockAlert() != null) target.setMinStockAlert(request.minStockAlert());
        target.setBarcode(request.barcode());
        target.setImageUrl(request.imageUrl());
        if (request.active() != null) target.setActive(request.active());
        return target;
    }
}
