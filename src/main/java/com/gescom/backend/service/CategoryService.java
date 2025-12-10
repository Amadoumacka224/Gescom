package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Category;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ActivityLogService activityLogService;

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
            // Don't fail business operation if logging fails
        }
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public List<Category> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    public Optional<Category> getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    public Optional<Category> getCategoryByCode(String code) {
        return categoryRepository.findByCode(code);
    }

    public Category createCategory(Category category) {
        // Vérifier si le nom existe déjà
        if (category.getName() != null && categoryRepository.findByName(category.getName()).isPresent()) {
            throw new RuntimeException("Une catégorie avec ce nom existe déjà");
        }

        // Vérifier si le code existe déjà
        if (category.getCode() != null && !category.getCode().isEmpty()
            && categoryRepository.findByCode(category.getCode()).isPresent()) {
            throw new RuntimeException("Une catégorie avec ce code existe déjà");
        }

        Category savedCategory = categoryRepository.save(category);

        // Log activity
        logActivity(ActivityLog.ActionType.CREATE, "Category", savedCategory.getId(),
            "Création de la catégorie " + savedCategory.getName());

        return savedCategory;
    }

    public Category updateCategory(Long id, Category categoryDetails) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catégorie non trouvée avec l'id: " + id));

        // Vérifier si le nouveau nom existe déjà (pour une autre catégorie)
        if (categoryDetails.getName() != null) {
            Optional<Category> existingCategory = categoryRepository.findByName(categoryDetails.getName());
            if (existingCategory.isPresent() && !existingCategory.get().getId().equals(id)) {
                throw new RuntimeException("Une catégorie avec ce nom existe déjà");
            }
            category.setName(categoryDetails.getName());
        }

        // Vérifier si le nouveau code existe déjà (pour une autre catégorie)
        if (categoryDetails.getCode() != null && !categoryDetails.getCode().isEmpty()) {
            Optional<Category> existingCategory = categoryRepository.findByCode(categoryDetails.getCode());
            if (existingCategory.isPresent() && !existingCategory.get().getId().equals(id)) {
                throw new RuntimeException("Une catégorie avec ce code existe déjà");
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

        // Log activity
        logActivity(ActivityLog.ActionType.UPDATE, "Category", savedCategory.getId(),
            "Modification de la catégorie " + savedCategory.getName());

        return savedCategory;
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catégorie non trouvée avec l'id: " + id));
        String categoryName = category.getName();
        categoryRepository.delete(category);

        // Log activity
        logActivity(ActivityLog.ActionType.DELETE, "Category", id,
            "Suppression de la catégorie " + categoryName);
    }

    public void toggleCategoryStatus(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Catégorie non trouvée avec l'id: " + id));
        category.setActive(!category.getActive());
        categoryRepository.save(category);

        // Log activity
        String status = category.getActive() ? "activée" : "désactivée";
        logActivity(ActivityLog.ActionType.UPDATE, "Category", id,
            "Catégorie " + category.getName() + " " + status);
    }
}
