package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.DuplicateResourceException;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final ActivityLogService activityLogService;

    public CategoryService(CategoryRepository categoryRepository, ActivityLogService activityLogService) {
        this.categoryRepository = categoryRepository;
        this.activityLogService = activityLogService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            return ((User) auth.getPrincipal()).getId();
        }
        return null;
    }

    private void logActivity(ActivityLog.ActionType actionType, String entity, Long entityId, String description) {
        try {
            Long userId = getCurrentUserId();
            if (userId != null) {
                activityLogService.logActivity(userId, actionType, entity, entityId, description, null, null);
            }
        } catch (Exception e) {
            log.warn("Échec du log d'activité: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Category> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Category> getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    @Transactional(readOnly = true)
    public Optional<Category> getCategoryByCode(String code) {
        return categoryRepository.findByCode(code);
    }

    public Category createCategory(Category category) {
        if (category.getName() != null && categoryRepository.findByName(category.getName()).isPresent()) {
            throw new DuplicateResourceException("Catégorie", "nom", category.getName());
        }

        if (category.getCode() != null && !category.getCode().isEmpty()
            && categoryRepository.findByCode(category.getCode()).isPresent()) {
            throw new DuplicateResourceException("Catégorie", "code", category.getCode());
        }

        Category savedCategory = categoryRepository.save(category);

        logActivity(ActivityLog.ActionType.CREATE, "Category", savedCategory.getId(),
            "Création de la catégorie " + savedCategory.getName());

        return savedCategory;
    }

    public Category updateCategory(Long id, Category categoryDetails) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));

        if (categoryDetails.getName() != null) {
            Optional<Category> existingCategory = categoryRepository.findByName(categoryDetails.getName());
            if (existingCategory.isPresent() && !existingCategory.get().getId().equals(id)) {
                throw new DuplicateResourceException("Catégorie", "nom", categoryDetails.getName());
            }
            category.setName(categoryDetails.getName());
        }

        if (categoryDetails.getCode() != null && !categoryDetails.getCode().isEmpty()) {
            Optional<Category> existingCategory = categoryRepository.findByCode(categoryDetails.getCode());
            if (existingCategory.isPresent() && !existingCategory.get().getId().equals(id)) {
                throw new DuplicateResourceException("Catégorie", "code", categoryDetails.getCode());
            }
            category.setCode(categoryDetails.getCode());
        }

        if (categoryDetails.getDescription() != null) {
            category.setDescription(categoryDetails.getDescription());
        }

        if (categoryDetails.getActive() != null) {
            category.setActive(categoryDetails.getActive());
        }

        Category savedCategory = categoryRepository.save(category);

        logActivity(ActivityLog.ActionType.UPDATE, "Category", savedCategory.getId(),
            "Modification de la catégorie " + savedCategory.getName());

        return savedCategory;
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));
        String categoryName = category.getName();
        categoryRepository.delete(category);

        logActivity(ActivityLog.ActionType.DELETE, "Category", id,
            "Suppression de la catégorie " + categoryName);
    }

    public void toggleCategoryStatus(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));
        category.setActive(!category.getActive());
        categoryRepository.save(category);

        String status = category.getActive() ? "activée" : "désactivée";
        logActivity(ActivityLog.ActionType.UPDATE, "Category", id,
            "Catégorie " + category.getName() + " " + status);
    }
}
