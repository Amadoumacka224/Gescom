package com.gescom.service;

import com.gescom.entity.Settings;
import com.gescom.repository.SettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion des paramètres avec cache et fonctionnalités avancées
 */
@Service
@Transactional
public class SettingsService {
    @Autowired
    private SettingsRepository settingsRepository;

    // Cache en mémoire pour les performances
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean cacheInitialized = false;

    /**
     * Initialise le cache si nécessaire
     */
    private void initializeCacheIfNeeded() {
        if (!cacheInitialized) {
            synchronized (this) {
                if (!cacheInitialized) {
                    refreshCache();
                    cacheInitialized = true;
                }
            }
        }
    }

    /**
     * Rafraîchit le cache complet
     */
    public void refreshCache() {
        cache.clear();
        List<Settings> allSettings = settingsRepository.findAll();
        for (Settings setting : allSettings) {
            if (setting.getValue() != null) {
                cache.put(setting.getKey(), setting.getValue());
            }
        }
    }

    /**
     * Invalide le cache pour une clé
     */
    private void invalidateCache(String key) {
        cache.remove(key);
    }

    // === MÉTHODES DE RÉCUPÉRATION AVEC CACHE ===

    /**
     * Récupère une valeur avec cache
     */
    public String getValue(String key) {
        initializeCacheIfNeeded();
        return cache.get(key);
    }

    /**
     * Récupère une valeur avec valeur par défaut
     */
    public String getValue(String key, String defaultValue) {
        String value = getValue(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Récupère une valeur booléenne
     */
    public Boolean getBooleanValue(String key, Boolean defaultValue) {
        String value = getValue(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    /**
     * Récupère une valeur entière
     */
    public Integer getIntegerValue(String key, Integer defaultValue) {
        String value = getValue(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Récupère une valeur décimale
     */
    public Double getDecimalValue(String key, Double defaultValue) {
        String value = getValue(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Récupère une liste de valeurs
     */
    public List<String> getListValue(String key, List<String> defaultValue) {
        String value = getValue(key);
        if (value == null || value.trim().isEmpty()) return defaultValue;
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // === MÉTHODES DE MODIFICATION ===

    /**
     * Définit une valeur avec mise à jour du cache
     */
    public Settings setValue(String key, String value) {
        Settings setting = settingsRepository.findByKey(key)
                .orElse(new Settings(key, value));
        
        setting.setValue(value);
        Settings saved = settingsRepository.save(setting);
        
        // Mise à jour du cache
        if (value != null) {
            cache.put(key, value);
        } else {
            invalidateCache(key);
        }
        
        return saved;
    }

    /**
     * Définit une valeur booléenne
     */
    public Settings setBooleanValue(String key, Boolean value) {
        Settings setting = settingsRepository.findByKey(key)
                .orElse(new Settings(key, ""));
        
        setting.setBooleanValue(value);
        Settings saved = settingsRepository.save(setting);
        cache.put(key, value.toString());
        return saved;
    }

    /**
     * Définit une valeur entière
     */
    public Settings setIntegerValue(String key, Integer value) {
        Settings setting = settingsRepository.findByKey(key)
                .orElse(new Settings(key, ""));
        
        setting.setIntegerValue(value);
        Settings saved = settingsRepository.save(setting);
        cache.put(key, value.toString());
        return saved;
    }

    /**
     * Définit une valeur décimale
     */
    public Settings setDecimalValue(String key, Double value) {
        Settings setting = settingsRepository.findByKey(key)
                .orElse(new Settings(key, ""));
        
        setting.setDecimalValue(value);
        Settings saved = settingsRepository.save(setting);
        cache.put(key, value.toString());
        return saved;
    }

    /**
     * Définit une liste de valeurs
     */
    public Settings setListValue(String key, List<String> values) {
        String value = String.join(",", values);
        return setValue(key, value);
    }

    // === MÉTHODES CRUD COMPLÈTES ===

    /**
     * Crée ou met à jour un paramètre complet
     */
    public Settings createOrUpdateSetting(String key, String value, String description, 
                                        Settings.SettingCategory category, Settings.ValueType valueType) {
        Settings setting = settingsRepository.findByKey(key)
                .orElse(new Settings());
        
        setting.setKey(key);
        setting.setValue(value);
        setting.setDescription(description);
        setting.setCategory(category);
        setting.setValueType(valueType);
        
        // Définir l'ordre de tri si nouveau paramètre
        if (setting.getId() == null) {
            Integer nextOrder = settingsRepository.findNextSortOrderForCategory(category);
            setting.setSortOrder(nextOrder);
        }
        
        Settings saved = settingsRepository.save(setting);
        
        // Mise à jour du cache
        if (value != null) {
            cache.put(key, value);
        }
        
        return saved;
    }

    /**
     * Sauvegarde un paramètre avec validation
     */
    public Settings saveSetting(Settings setting) {
        // Validation de la valeur
        if (!setting.isValidValue()) {
            throw new IllegalArgumentException("Valeur invalide pour le type " + setting.getValueType());
        }
        
        // Définir l'ordre de tri si nouveau paramètre
        if (setting.getId() == null && setting.getSortOrder() == null) {
            Integer nextOrder = settingsRepository.findNextSortOrderForCategory(setting.getCategory());
            setting.setSortOrder(nextOrder);
        }
        
        Settings saved = settingsRepository.save(setting);
        
        // Mise à jour du cache
        if (saved.getValue() != null) {
            cache.put(saved.getKey(), saved.getValue());
        } else {
            invalidateCache(saved.getKey());
        }
        
        return saved;
    }

    // === MÉTHODES DE RÉCUPÉRATION ===

    /**
     * Récupère tous les paramètres triés
     */
    public List<Settings> getAllSettings() {
        return settingsRepository.findAllByOrderByCategoryAscSortOrderAscKeyAsc();
    }

    /**
     * Récupère les paramètres par catégorie
     */
    public List<Settings> getSettingsByCategory(Settings.SettingCategory category) {
        return settingsRepository.findByCategoryOrderBySortOrderAscKeyAsc(category);
    }

    /**
     * Récupère les paramètres groupés par catégorie
     */
    public Map<Settings.SettingCategory, List<Settings>> getSettingsGroupedByCategory() {
        return getAllSettings().stream()
                .collect(Collectors.groupingBy(Settings::getCategory, LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * Recherche avec filtres et pagination
     */
    public Page<Settings> searchSettings(String keyword, Settings.SettingCategory category, 
                                       Boolean isSystem, Pageable pageable) {
        return settingsRepository.findWithFilters(keyword, category, isSystem, pageable);
    }

    /**
     * Recherche simple par mot-clé
     */
    public List<Settings> searchSettings(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return getAllSettings();
        }
        return settingsRepository.searchByKeyword(keyword.trim());
    }

    /**
     * Récupère un paramètre par ID
     */
    public Optional<Settings> getSettingById(Long id) {
        return settingsRepository.findById(id);
    }

    /**
     * Récupère un paramètre par clé
     */
    public Optional<Settings> getSettingByKey(String key) {
        return settingsRepository.findByKey(key);
    }

    // === MÉTHODES DE SUPPRESSION ===

    /**
     * Supprime un paramètre (sauf système)
     */
    public boolean deleteSetting(Long id) {
        Optional<Settings> settingOpt = settingsRepository.findById(id);
        if (settingOpt.isPresent() && !settingOpt.get().getIsSystem()) {
            Settings setting = settingOpt.get();
            settingsRepository.deleteById(id);
            invalidateCache(setting.getKey());
            return true;
        }
        return false;
    }

    /**
     * Supprime un paramètre par clé (sauf système)
     */
    public boolean deleteSettingByKey(String key) {
        Optional<Settings> settingOpt = settingsRepository.findByKey(key);
        if (settingOpt.isPresent() && !settingOpt.get().getIsSystem()) {
            settingsRepository.delete(settingOpt.get());
            invalidateCache(key);
            return true;
        }
        return false;
    }

    // === STATISTIQUES ET RAPPORTS ===

    /**
     * Récupère les statistiques complètes
     */
    public Map<String, Object> getSettingsStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Comptages de base
        stats.put("totalSettings", settingsRepository.countTotal());
        stats.put("systemSettings", settingsRepository.countSystem());
        stats.put("userSettings", settingsRepository.countUser());
        
        // Comptage par catégorie
        List<SettingsRepository.CategoryCount> categoryStats = settingsRepository.countByCategory();
        Map<String, Long> categoryCount = categoryStats.stream()
                .collect(Collectors.toMap(
                    stat -> stat.getCategory().getDisplayName(),
                    SettingsRepository.CategoryCount::getCount,
                    (existing, replacement) -> existing,
                    LinkedHashMap::new
                ));
        stats.put("categoryCount", categoryCount);
        
        // Paramètres modifiés récemment (7 jours)
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        stats.put("recentlyModifiedCount", settingsRepository.countModifiedSince(since));
        
        // Paramètres avec valeurs invalides
        List<Settings> invalidSettings = settingsRepository.findInvalidSettings();
        stats.put("invalidSettingsCount", invalidSettings.size());
        
        // Santé du cache
        stats.put("cacheSize", cache.size());
        stats.put("cacheInitialized", cacheInitialized);
        
        return stats;
    }

    /**
     * Récupère les paramètres modifiés récemment
     */
    public List<Settings> getRecentlyModifiedSettings(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return settingsRepository.findRecentlyModified(since);
    }

    /**
     * Valide tous les paramètres
     */
    public List<Settings> validateAllSettings() {
        return settingsRepository.findInvalidSettings();
    }

    // === IMPORT/EXPORT ===

    /**
     * Exporte tous les paramètres
     */
    public Map<String, Object> exportSettings() {
        List<Settings> settings = getAllSettings();
        Map<String, Object> export = new HashMap<>();
        
        export.put("exported_at", LocalDateTime.now());
        export.put("total_settings", settings.size());
        export.put("application", "GESCOM");
        export.put("version", "1.0.0");
        
        // Grouper par catégorie
        Map<String, List<Map<String, Object>>> categorizedSettings = settings.stream()
                .collect(Collectors.groupingBy(
                    s -> s.getCategory().name(),
                    LinkedHashMap::new,
                    Collectors.mapping(s -> {
                        Map<String, Object> settingMap = new HashMap<>();
                        settingMap.put("key", s.getKey());
                        settingMap.put("value", s.getValue());
                        settingMap.put("description", s.getDescription());
                        settingMap.put("valueType", s.getValueType().name());
                        settingMap.put("isSystem", s.getIsSystem());
                        settingMap.put("sortOrder", s.getSortOrder());
                        return settingMap;
                    }, Collectors.toList())
                ));
        
        export.put("settings", categorizedSettings);
        return export;
    }

    /**
     * Sauvegarde en masse
     */
    @Transactional
    public void saveAllSettings(List<Settings> settings) {
        List<Settings> saved = settingsRepository.saveAll(settings);
        
        // Mise à jour du cache en masse
        for (Settings setting : saved) {
            if (setting.getValue() != null) {
                cache.put(setting.getKey(), setting.getValue());
            }
        }
    }

    // === INITIALISATION DES PARAMÈTRES PAR DÉFAUT ===

    /**
     * Initialise les paramètres par défaut du système
     */
    @Transactional
    public void initializeDefaultSettings() {
        // Paramètres généraux
        initializeIfNotExists("app.name", "GESCOM", "Nom de l'application", 
                Settings.SettingCategory.GENERAL, Settings.ValueType.STRING, true, 1);
        
        initializeIfNotExists("app.version", "1.0.0", "Version de l'application", 
                Settings.SettingCategory.SYSTEM, Settings.ValueType.STRING, true, 1);
        
        initializeIfNotExists("app.environment", "production", "Environnement d'exécution", 
                Settings.SettingCategory.SYSTEM, Settings.ValueType.STRING, true, 2);
        
        initializeIfNotExists("app.debug", "false", "Mode debug activé", 
                Settings.SettingCategory.SYSTEM, Settings.ValueType.BOOLEAN, true, 3);
        
        // Paramètres entreprise
        initializeIfNotExists("company.name", "Votre Entreprise", "Nom de l'entreprise", 
                Settings.SettingCategory.COMPANY, Settings.ValueType.STRING, false, 1);
        
        initializeIfNotExists("company.address", "", "Adresse de l'entreprise", 
                Settings.SettingCategory.COMPANY, Settings.ValueType.TEXT, false, 2);
        
        initializeIfNotExists("company.phone", "", "Téléphone de l'entreprise", 
                Settings.SettingCategory.COMPANY, Settings.ValueType.STRING, false, 3);
        
        initializeIfNotExists("company.email", "", "Email de l'entreprise", 
                Settings.SettingCategory.COMPANY, Settings.ValueType.EMAIL, false, 4);
        
        initializeIfNotExists("company.website", "", "Site web de l'entreprise", 
                Settings.SettingCategory.COMPANY, Settings.ValueType.URL, false, 5);
        
        initializeIfNotExists("company.logo", "", "Logo de l'entreprise", 
                Settings.SettingCategory.COMPANY, Settings.ValueType.FILE_PATH, false, 6);
        
        // Paramètres facturation
        initializeIfNotExists("invoice.prefix", "FACT", "Préfixe des factures", 
                Settings.SettingCategory.INVOICE, Settings.ValueType.STRING, false, 1);
        
        initializeIfNotExists("invoice.next_number", "1", "Prochain numéro de facture", 
                Settings.SettingCategory.INVOICE, Settings.ValueType.INTEGER, false, 2);
        
        initializeIfNotExists("invoice.auto_send", "false", "Envoi automatique des factures", 
                Settings.SettingCategory.INVOICE, Settings.ValueType.BOOLEAN, false, 3);
        
        initializeIfNotExists("invoice.payment_terms", "30", "Délai de paiement (jours)", 
                Settings.SettingCategory.INVOICE, Settings.ValueType.INTEGER, false, 4);
        
        // Paramètres fiscaux
        initializeIfNotExists("tax.default_rate", "20.0", "Taux de TVA par défaut", 
                Settings.SettingCategory.TAX, Settings.ValueType.DECIMAL, false, 1);
        
        initializeIfNotExists("tax.company_number", "", "Numéro de TVA intracommunautaire", 
                Settings.SettingCategory.TAX, Settings.ValueType.STRING, false, 2);
        
        // Paramètres email
        initializeIfNotExists("email.smtp_host", "", "Serveur SMTP", 
                Settings.SettingCategory.EMAIL, Settings.ValueType.STRING, false, 1);
        
        initializeIfNotExists("email.smtp_port", "587", "Port SMTP", 
                Settings.SettingCategory.EMAIL, Settings.ValueType.INTEGER, false, 2);
        
        initializeIfNotExists("email.smtp_username", "", "Nom d'utilisateur SMTP", 
                Settings.SettingCategory.EMAIL, Settings.ValueType.STRING, false, 3);
        
        initializeIfNotExists("email.smtp_password", "", "Mot de passe SMTP", 
                Settings.SettingCategory.EMAIL, Settings.ValueType.PASSWORD, false, 4);
        
        initializeIfNotExists("email.from_address", "", "Adresse d'expédition", 
                Settings.SettingCategory.EMAIL, Settings.ValueType.EMAIL, false, 5);
        
        // Paramètres notifications
        initializeIfNotExists("notifications.email_enabled", "true", "Notifications par email", 
                Settings.SettingCategory.NOTIFICATION, Settings.ValueType.BOOLEAN, false, 1);
        
        initializeIfNotExists("notifications.low_stock", "true", "Alertes stock bas", 
                Settings.SettingCategory.NOTIFICATION, Settings.ValueType.BOOLEAN, false, 2);
        
        // Paramètres sécurité
        initializeIfNotExists("security.session_timeout", "30", "Délai d'expiration session (min)", 
                Settings.SettingCategory.SECURITY, Settings.ValueType.INTEGER, false, 1);
        
        initializeIfNotExists("security.password_min_length", "8", "Longueur minimale mot de passe", 
                Settings.SettingCategory.SECURITY, Settings.ValueType.INTEGER, false, 2);
        
        initializeIfNotExists("security.max_login_attempts", "3", "Tentatives de connexion max", 
                Settings.SettingCategory.SECURITY, Settings.ValueType.INTEGER, false, 3);
        
        // Paramètres apparence
        initializeIfNotExists("appearance.theme", "default", "Thème de l'interface", 
                Settings.SettingCategory.APPEARANCE, Settings.ValueType.STRING, false, 1);
        
        initializeIfNotExists("appearance.primary_color", "#0d6efd", "Couleur principale", 
                Settings.SettingCategory.APPEARANCE, Settings.ValueType.COLOR, false, 2);
        
        initializeIfNotExists("appearance.items_per_page", "20", "Éléments par page", 
                Settings.SettingCategory.APPEARANCE, Settings.ValueType.INTEGER, false, 3);
        
        // Rafraîchir le cache après initialisation
        refreshCache();
    }

    /**
     * Initialise un paramètre s'il n'existe pas
     */
    private void initializeIfNotExists(String key, String value, String description, 
                                     Settings.SettingCategory category, Settings.ValueType valueType, 
                                     boolean isSystem, int sortOrder) {
        if (!settingsRepository.existsByKey(key)) {
            Settings setting = new Settings(key, value, description, category, valueType);
            setting.setIsSystem(isSystem);
            setting.setSortOrder(sortOrder);
            
            // Cryptage automatique des mots de passe
            if (valueType == Settings.ValueType.PASSWORD) {
                setting.setIsEncrypted(true);
            }
            
            settingsRepository.save(setting);
        }
    }
}