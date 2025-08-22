package com.gescom.service;

import com.gescom.entity.Settings;
import com.gescom.repository.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour le service Settings
 * Valide toutes les fonctionnalités du module de paramétrage
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SettingsServiceTest {

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private SettingsRepository settingsRepository;

    @BeforeEach
    void setUp() {
        // Nettoyer les données de test
        settingsRepository.deleteAll();
        
        // Initialiser quelques paramètres de test
        settingsService.initializeDefaultSettings();
    }

    @Test
    void testInitializeDefaultSettings() {
        // Tester l'initialisation des paramètres par défaut
        assertTrue(settingsRepository.existsByKey("app.name"));
        assertTrue(settingsRepository.existsByKey("company.name"));
        assertTrue(settingsRepository.existsByKey("appearance.theme"));
        
        // Vérifier les nouvelles options ajoutées
        assertTrue(settingsRepository.existsByKey("appearance.show_tooltips"));
        assertTrue(settingsRepository.existsByKey("localization.default_language"));
        assertTrue(settingsRepository.existsByKey("performance.cache_enabled"));
    }

    @Test
    void testGetValue() {
        // Test récupération valeur string
        String appName = settingsService.getValue("app.name");
        assertNotNull(appName);
        assertEquals("GESCOM", appName);

        // Test avec valeur par défaut
        String nonExistent = settingsService.getValue("non.existent", "default");
        assertEquals("default", nonExistent);
    }

    @Test
    void testBooleanValue() {
        // Test récupération valeur boolean
        Boolean debugMode = settingsService.getBooleanValue("app.debug", false);
        assertNotNull(debugMode);
        assertFalse(debugMode);

        // Test nouvelles méthodes utilitaires
        assertTrue(settingsService.areTooltipsEnabled());
        assertFalse(settingsService.isCompactModeEnabled());
        assertTrue(settingsService.isAuditLogEnabled());
    }

    @Test
    void testIntegerValue() {
        // Test récupération valeur integer
        Integer itemsPerPage = settingsService.getIntegerValue("appearance.items_per_page", 10);
        assertNotNull(itemsPerPage);
        assertEquals(20, itemsPerPage);

        // Test nouvelles méthodes utilitaires
        assertEquals(100, settingsService.getApiRateLimit());
        assertEquals(1000, settingsService.getMaxResults());
    }

    @Test
    void testSetValue() {
        // Test modification de valeur
        Settings setting = settingsService.setValue("test.key", "test.value");
        assertNotNull(setting);
        assertEquals("test.value", setting.getValue());

        // Vérifier que la valeur est bien mise à jour
        String retrievedValue = settingsService.getValue("test.key");
        assertEquals("test.value", retrievedValue);
    }

    @Test
    void testLocalizedSettings() {
        // Test paramètres de localisation
        assertEquals("fr", settingsService.getDefaultLanguage());
        assertEquals("Europe/Paris", settingsService.getTimezone());
        assertEquals("dd/MM/yyyy", settingsService.getDateFormat());
        assertEquals("EUR", settingsService.getDefaultCurrency());
    }

    @Test
    void testPerformanceSettings() {
        // Test paramètres de performance
        assertEquals(300, settingsService.getCacheDuration());
        assertEquals(1000, settingsService.getMaxResults());
    }

    @Test
    void testReportSettings() {
        // Test paramètres de reporting
        assertEquals("PDF", settingsService.getDefaultReportFormat());
        assertTrue(settingsService.isReportAutoArchiveEnabled());
    }

    @Test
    void testBackupSettings() {
        // Test paramètres de sauvegarde
        assertFalse(settingsService.isAutoBackupEnabled());
        assertEquals("daily", settingsService.getBackupFrequency());
    }

    @Test
    void testIntegrationSettings() {
        // Test paramètres d'intégration
        assertFalse(settingsService.isApiEnabled());
        assertEquals(100, settingsService.getApiRateLimit());
    }

    @Test
    void testSearchByKey() {
        // Test recherche de paramètres par clé
        Optional<Settings> result = settingsService.getSettingByKey("app.name");
        assertTrue(result.isPresent());
        assertEquals("app.name", result.get().getKey());
        assertEquals("GESCOM", result.get().getValue());
    }

    @Test
    void testCache() {
        // Test fonctionnement du cache
        assertDoesNotThrow(() -> settingsService.refreshCache());
        
        // Vérifier qu'après rafraîchissement, les valeurs sont toujours accessibles
        String appName = settingsService.getValue("app.name");
        assertEquals("GESCOM", appName);
    }

    @Test
    void testExportImport() {
        // Test export uniquement (les méthodes internes ne sont pas exposées)
        assertDoesNotThrow(() -> settingsService.exportSettings());
    }

    @Test
    void testDeleteSetting() {
        // Créer un paramètre de test non-système
        Settings testSetting = new Settings("test.delete", "value");
        testSetting.setIsSystem(false);
        testSetting = settingsRepository.save(testSetting);
        
        // Vérifier qu'il existe
        assertTrue(settingsRepository.existsByKey("test.delete"));
        
        // Le supprimer
        settingsService.deleteSetting(testSetting.getId());
        
        // Vérifier qu'il n'existe plus
        assertFalse(settingsRepository.existsByKey("test.delete"));
    }

    @Test
    void testSystemSettingsProtection() {
        // Créer un paramètre système
        Settings systemSetting = new Settings("system.test", "value");
        systemSetting.setIsSystem(true);
        final Settings savedSetting = settingsRepository.save(systemSetting);
        
        // Essayer de le supprimer (devrait renvoyer false)
        boolean deleted = settingsService.deleteSetting(savedSetting.getId());
        assertFalse(deleted, "Un paramètre système ne devrait pas pouvoir être supprimé");
        
        // Vérifier qu'il existe toujours
        assertTrue(settingsRepository.existsById(savedSetting.getId()));
    }

    @Test
    void testRepositoryQueries() {
        // Test requêtes du repository
        assertTrue(settingsRepository.existsByKey("app.name"));
        assertFalse(settingsRepository.existsByKey("non.existent.key"));
        
        // Vérifier qu'il y a des paramètres de différents types
        List<Settings> booleanSettings = settingsRepository.findByValueType(Settings.ValueType.BOOLEAN);
        assertFalse(booleanSettings.isEmpty());
    }

    @Test
    void testPasswordEncryption() {
        // Test que les mots de passe sont bien marqués pour cryptage
        Optional<Settings> passwordSetting = settingsRepository.findByKey("email.smtp_password");
        assertTrue(passwordSetting.isPresent());
        assertTrue(passwordSetting.get().getIsEncrypted());
        assertEquals(Settings.ValueType.PASSWORD, passwordSetting.get().getValueType());
    }

    @Test
    void testRepositoryStats() {
        // Test statistiques via repository
        long totalSettings = settingsRepository.count();
        assertTrue(totalSettings > 0);
        
        long systemSettings = settingsRepository.countSystem();
        assertTrue(systemSettings > 0);
        
        long userSettings = settingsRepository.countUser();
        assertTrue(userSettings >= 0);
    }

    @Test
    void testGetDefaultsForType() {
        // Test récupération des paramètres par type de valeur
        List<Settings> booleanSettings = settingsRepository.findByValueType(Settings.ValueType.BOOLEAN);
        assertNotNull(booleanSettings);
        assertFalse(booleanSettings.isEmpty());
        
        // Vérifier que tous sont bien de type BOOLEAN
        assertTrue(booleanSettings.stream()
                .allMatch(s -> s.getValueType() == Settings.ValueType.BOOLEAN));
    }
}