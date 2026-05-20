package com.gescom.backend.mapper;

import com.gescom.backend.dto.category.CategoryRequest;
import com.gescom.backend.dto.category.CategoryResponse;
import com.gescom.backend.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        if (category == null) return null;
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getCode(),
                category.getActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    public Category toEntity(CategoryRequest request) {
        return applyRequest(new Category(), request);
    }

    public Category applyRequest(Category target, CategoryRequest request) {
        target.setName(request.name());
        target.setDescription(request.description());
        target.setCode(request.code());
        if (request.active() != null) {
            target.setActive(request.active());
        }
        return target;
    }
}
