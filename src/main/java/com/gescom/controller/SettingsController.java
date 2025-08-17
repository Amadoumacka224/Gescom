package com.gescom.controller;

import com.gescom.entity.Settings;
import com.gescom.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.util.StringUtils;

import jakarta.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Contrôleur pour la gestion des paramètres système
 * Design professionnel avec toutes les fonctionnalités
 */
@Controller
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {

    @Autowired
    private SettingsService settingsService;

    /**
     * Page principale des paramètres avec recherche et pagination
     */
    @GetMapping
    public String index(@RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "20") int size,
                       @RequestParam(value = "sort", defaultValue = "category") String sort,
                       @RequestParam(value = "direction", defaultValue = "asc") String direction,
                       @RequestParam(value = "category", required = false) String category,
                       @RequestParam(value = "search", required = false) String search,
                       @RequestParam(value = "isSystem", required = false) Boolean isSystem,
                       @RequestParam(value = "view", defaultValue = "grouped") String view,
                       Model model) {
        
        try {
            // Création du Pageable
            Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
            
            // Conversion de la catégorie
            Settings.SettingCategory categoryEnum = null;
            if (StringUtils.hasText(category)) {
                try {
                    categoryEnum = Settings.SettingCategory.valueOf(category.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Catégorie invalide, ignorer
                }
            }
            
            // Recherche avec pagination
            Page<Settings> settingsPage = settingsService.searchSettings(search, categoryEnum, isSystem, pageable);
            
            // Groupement par catégorie si demandé
            if ("grouped".equals(view)) {
                Map<Settings.SettingCategory, List<Settings>> groupedSettings = settingsPage.getContent()
                        .stream()
                        .collect(Collectors.groupingBy(Settings::getCategory, LinkedHashMap::new, Collectors.toList()));
                model.addAttribute("groupedSettings", groupedSettings);
            } else {
                model.addAttribute("settings", settingsPage.getContent());
            }
            
            // Attributs pour la pagination
            model.addAttribute("settingsPage", settingsPage);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", settingsPage.getTotalPages());
            model.addAttribute("totalElements", settingsPage.getTotalElements());
            model.addAttribute("size", size);
            model.addAttribute("sort", sort);
            model.addAttribute("direction", direction);
            
            // Paramètres de recherche
            model.addAttribute("searchQuery", search);
            model.addAttribute("selectedCategory", categoryEnum);
            model.addAttribute("isSystemFilter", isSystem);
            model.addAttribute("currentView", view);
            
            // Données pour les formulaires
            model.addAttribute("categories", Settings.SettingCategory.values());
            model.addAttribute("valueTypes", Settings.ValueType.values());
            model.addAttribute("stats", settingsService.getSettingsStatistics());
            
            // URLs de pagination
            model.addAttribute("baseUrl", "/admin/settings");
            model.addAttribute("searchParams", buildSearchParams(search, category, isSystem, view, size, sort, direction));
            
            return "admin/settings";
            
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des paramètres : " + e.getMessage());
            model.addAttribute("categories", Settings.SettingCategory.values());
            model.addAttribute("stats", settingsService.getSettingsStatistics());
            return "admin/settings";
        }
    }

    /**
     * Formulaire de création d'un nouveau paramètre
     */
    @GetMapping("/new")
    public String newSetting(Model model) {
        model.addAttribute("setting", new Settings());
        model.addAttribute("categories", Settings.SettingCategory.values());
        model.addAttribute("valueTypes", Settings.ValueType.values());
        model.addAttribute("isEdit", false);
        return "admin/settings-form";
    }

    /**
     * Formulaire d'édition d'un paramètre
     */
    @GetMapping("/edit/{id}")
    public String editSetting(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Settings> settingOpt = settingsService.getSettingById(id);
        if (settingOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Paramètre introuvable");
            return "redirect:/admin/settings";
        }
        
        Settings setting = settingOpt.get();
        model.addAttribute("setting", setting);
        model.addAttribute("categories", Settings.SettingCategory.values());
        model.addAttribute("valueTypes", Settings.ValueType.values());
        model.addAttribute("isEdit", true);
        model.addAttribute("isSystemSetting", setting.getIsSystem());
        return "admin/settings-form";
    }

    /**
     * Duplication d'un paramètre
     */
    @GetMapping("/duplicate/{id}")
    public String duplicateSetting(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Settings> settingOpt = settingsService.getSettingById(id);
        if (settingOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Paramètre introuvable");
            return "redirect:/admin/settings";
        }
        
        Settings original = settingOpt.get();
        Settings duplicate = new Settings();
        duplicate.setKey(original.getKey() + "_copy");
        duplicate.setValue(original.getValue());
        duplicate.setDescription("Copie de : " + original.getDescription());
        duplicate.setCategory(original.getCategory());
        duplicate.setValueType(original.getValueType());
        duplicate.setIsSystem(false); // Les copies ne sont jamais système
        
        model.addAttribute("setting", duplicate);
        model.addAttribute("categories", Settings.SettingCategory.values());
        model.addAttribute("valueTypes", Settings.ValueType.values());
        model.addAttribute("isEdit", false);
        model.addAttribute("isDuplicate", true);
        return "admin/settings-form";
    }

    /**
     * Sauvegarde d'un paramètre (nouveau ou modification)
     */
    @PostMapping("/save")
    public String saveSetting(@Valid @ModelAttribute Settings setting, 
                             BindingResult bindingResult,
                             Model model, 
                             RedirectAttributes redirectAttributes) {
        
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", Settings.SettingCategory.values());
            model.addAttribute("valueTypes", Settings.ValueType.values());
            model.addAttribute("isEdit", setting.getId() != null);
            return "admin/settings-form";
        }

        try {
            // Validation spécifique
            if (!setting.isValidValue()) {
                model.addAttribute("error", "La valeur est invalide pour le type sélectionné");
                model.addAttribute("categories", Settings.SettingCategory.values());
                model.addAttribute("valueTypes", Settings.ValueType.values());
                model.addAttribute("isEdit", setting.getId() != null);
                return "admin/settings-form";
            }
            
            // Vérification d'unicité de la clé (pour nouveaux paramètres)
            if (setting.getId() == null) {
                Optional<Settings> existing = settingsService.getSettingByKey(setting.getKey());
                if (existing.isPresent()) {
                    model.addAttribute("error", "Un paramètre avec cette clé existe déjà");
                    model.addAttribute("categories", Settings.SettingCategory.values());
                    model.addAttribute("valueTypes", Settings.ValueType.values());
                    model.addAttribute("isEdit", false);
                    return "admin/settings-form";
                }
            }
            
            // Gestion des paramètres système (protection)
            if (setting.getId() != null) {
                Optional<Settings> existingOpt = settingsService.getSettingById(setting.getId());
                if (existingOpt.isPresent()) {
                    Settings existing = existingOpt.get();
                    if (existing.getIsSystem()) {
                        // Pour les paramètres système, on ne peut modifier que la valeur
                        existing.setValue(setting.getValue());
                        settingsService.saveSetting(existing);
                        redirectAttributes.addFlashAttribute("success", "Paramètre système mis à jour avec succès");
                        return "redirect:/admin/settings";
                    }
                }
            }
            
            // Sauvegarde normale
            settingsService.saveSetting(setting);
            redirectAttributes.addFlashAttribute("success", 
                setting.getId() != null ? "Paramètre mis à jour avec succès" : "Paramètre créé avec succès");
            
            return "redirect:/admin/settings";
            
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors de la sauvegarde : " + e.getMessage());
            model.addAttribute("categories", Settings.SettingCategory.values());
            model.addAttribute("valueTypes", Settings.ValueType.values());
            model.addAttribute("isEdit", setting.getId() != null);
            return "admin/settings-form";
        }
    }

    /**
     * Suppression d'un paramètre
     */
    @PostMapping("/delete/{id}")
    public String deleteSetting(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            boolean deleted = settingsService.deleteSetting(id);
            if (deleted) {
                redirectAttributes.addFlashAttribute("success", "Paramètre supprimé avec succès");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Impossible de supprimer ce paramètre (paramètre système ou introuvable)");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression : " + e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    /**
     * Suppression multiple de paramètres
     */
    @PostMapping("/delete-multiple")
    public String deleteMultipleSettings(@RequestParam("selectedIds") List<Long> ids, 
                                        RedirectAttributes redirectAttributes) {
        try {
            int deletedCount = 0;
            int skippedCount = 0;
            
            for (Long id : ids) {
                if (settingsService.deleteSetting(id)) {
                    deletedCount++;
                } else {
                    skippedCount++;
                }
            }
            
            if (deletedCount > 0) {
                redirectAttributes.addFlashAttribute("success", 
                    deletedCount + " paramètre(s) supprimé(s)" + 
                    (skippedCount > 0 ? " (" + skippedCount + " ignoré(s))" : ""));
            } else {
                redirectAttributes.addFlashAttribute("warning", "Aucun paramètre n'a pu être supprimé");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression : " + e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    /**
     * Réinitialisation des paramètres par défaut
     */
    @PostMapping("/initialize")
    public String initializeDefaultSettings(RedirectAttributes redirectAttributes) {
        try {
            settingsService.initializeDefaultSettings();
            redirectAttributes.addFlashAttribute("success", "Paramètres par défaut initialisés avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'initialisation : " + e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    /**
     * Validation des paramètres
     */
    @PostMapping("/validate")
    public String validateSettings(RedirectAttributes redirectAttributes) {
        try {
            List<Settings> invalidSettings = settingsService.validateAllSettings();
            if (invalidSettings.isEmpty()) {
                redirectAttributes.addFlashAttribute("success", "Tous les paramètres sont valides");
            } else {
                redirectAttributes.addFlashAttribute("warning", 
                    invalidSettings.size() + " paramètre(s) ont des valeurs invalides");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la validation : " + e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    /**
     * Rafraîchissement du cache
     */
    @PostMapping("/refresh-cache")
    public String refreshCache(RedirectAttributes redirectAttributes) {
        try {
            settingsService.refreshCache();
            redirectAttributes.addFlashAttribute("success", "Cache rafraîchi avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors du rafraîchissement : " + e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    // === API REST POUR AJAX ===

    /**
     * API pour récupérer une valeur de paramètre
     */
    @GetMapping("/api/value/{key}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSettingValue(@PathVariable String key) {
        try {
            String value = settingsService.getValue(key);
            Optional<Settings> setting = settingsService.getSettingByKey(key);
            
            Map<String, Object> response = new HashMap<>();
            response.put("key", key);
            response.put("value", value);
            response.put("exists", value != null);
            response.put("success", true);
            
            if (setting.isPresent()) {
                response.put("valueType", setting.get().getValueType());
                response.put("description", setting.get().getDescription());
                response.put("category", setting.get().getCategory());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API pour mettre à jour une valeur de paramètre via AJAX
     */
    @PostMapping("/api/value/{key}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateSettingValue(@PathVariable String key, 
                                                                 @RequestParam String value) {
        try {
            // Récupérer le paramètre existant
            Optional<Settings> settingOpt = settingsService.getSettingByKey(key);
            if (settingOpt.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Paramètre introuvable");
                return ResponseEntity.notFound().build();
            }
            
            Settings setting = settingOpt.get();
            
            // Vérifier si c'est un paramètre système (protection)
            if (setting.getIsSystem()) {
                // Pour les paramètres système, vérification supplémentaire
                // On peut permettre la modification mais avec validation stricte
            }
            
            // Mettre à jour la valeur
            setting.setValue(value);
            
            // Validation
            if (!setting.isValidValue()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Valeur invalide pour le type " + setting.getValueType().getDisplayName());
                return ResponseEntity.badRequest().body(response);
            }
            
            // Sauvegarder
            Settings saved = settingsService.saveSetting(setting);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("key", key);
            response.put("value", saved.getValue());
            response.put("displayValue", saved.getDisplayValue());
            response.put("updated", true);
            response.put("updatedAt", saved.getUpdatedAt());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API pour la recherche en temps réel
     */
    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchSettings(@RequestParam String query) {
        try {
            List<Settings> settings = settingsService.searchSettings(query);
            List<Map<String, Object>> result = settings.stream()
                    .limit(10) // Limiter pour les performances
                    .map(s -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", s.getId());
                        item.put("key", s.getKey());
                        item.put("value", s.getDisplayValue());
                        item.put("description", s.getDescription());
                        item.put("category", s.getCategory().getDisplayName());
                        item.put("categoryIcon", s.getCategory().getIcon());
                        return item;
                    })
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }
    }

    /**
     * Export des paramètres en JSON
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportSettings() {
        try {
            Map<String, Object> export = settingsService.exportSettings();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", "gescom-settings-export.json");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(export);
                    
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Erreur lors de l'export : " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Statistiques en temps réel
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = settingsService.getSettingsStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // === MÉTHODES UTILITAIRES ===

    /**
     * Construit les paramètres de recherche pour la pagination
     */
    private String buildSearchParams(String search, String category, Boolean isSystem, 
                                   String view, int size, String sort, String direction) {
        List<String> params = new ArrayList<>();
        
        if (StringUtils.hasText(search)) {
            params.add("search=" + search);
        }
        if (StringUtils.hasText(category)) {
            params.add("category=" + category);
        }
        if (isSystem != null) {
            params.add("isSystem=" + isSystem);
        }
        if (StringUtils.hasText(view)) {
            params.add("view=" + view);
        }
        if (size != 20) {
            params.add("size=" + size);
        }
        if (!"category".equals(sort)) {
            params.add("sort=" + sort);
        }
        if (!"asc".equals(direction)) {
            params.add("direction=" + direction);
        }
        
        return params.isEmpty() ? "" : "?" + String.join("&", params);
    }
}