package com.gescom.backend.controller;

import com.gescom.backend.dto.category.CategoryRequest;
import com.gescom.backend.dto.category.CategoryResponse;
import com.gescom.backend.entity.Category;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.CategoryMapper;
import com.gescom.backend.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    public CategoryController(CategoryService categoryService, CategoryMapper categoryMapper) {
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories().stream()
                .map(categoryMapper::toResponse).toList());
    }

    @GetMapping("/active")
    public ResponseEntity<List<CategoryResponse>> getActiveCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories().stream()
                .map(categoryMapper::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryById(id)
                .map(categoryMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("category", id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        Category created = categoryService.createCategory(categoryMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id,
                                                            @Valid @RequestBody CategoryRequest request) {
        Category details = categoryMapper.toEntity(request);
        return ResponseEntity.ok(categoryMapper.toResponse(categoryService.updateCategory(id, details)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> toggleCategoryStatus(@PathVariable Long id) {
        categoryService.toggleCategoryStatus(id);
        return ResponseEntity.ok(Map.of("message", "Statut de la catégorie modifié avec succès"));
    }
}
