package com.gescom.controller;

import com.gescom.entity.Settings;
import com.gescom.service.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration pour le contrôleur Settings
 * Valide les endpoints et l'interface web
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class SettingsControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // Initialiser les paramètres de test
        settingsService.initializeDefaultSettings();
    }

    @Test
    void testSettingsListPage() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"))
                .andExpect(model().attributeExists("settings"))
                .andExpect(model().attributeExists("categories"))
                .andExpect(model().attributeExists("stats"));
    }

    @Test
    void testSettingsListWithSearch() throws Exception {
        mockMvc.perform(get("/admin/settings")
                        .param("search", "app")
                        .param("view", "list"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"))
                .andExpect(model().attribute("searchQuery", "app"))
                .andExpect(model().attribute("currentView", "list"));
    }

    @Test
    void testSettingsListWithFilters() throws Exception {
        mockMvc.perform(get("/admin/settings")
                        .param("category", "GENERAL")
                        .param("isSystem", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedCategory", Settings.SettingCategory.GENERAL))
                .andExpect(model().attribute("isSystemFilter", true));
    }

    @Test
    void testSettingsNewPage() throws Exception {
        mockMvc.perform(get("/admin/settings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings-form"))
                .andExpect(model().attributeExists("setting"))
                .andExpect(model().attributeExists("categories"))
                .andExpect(model().attributeExists("valueTypes"));
    }

    @Test
    void testCreateSetting() throws Exception {
        mockMvc.perform(post("/admin/settings")
                        .param("key", "test.new.setting")
                        .param("value", "test value")
                        .param("description", "Test description")
                        .param("category", "GENERAL")
                        .param("valueType", "STRING")
                        .param("isSystem", "false")
                        .param("sortOrder", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void testCreateSettingWithValidationError() throws Exception {
        mockMvc.perform(post("/admin/settings")
                        .param("key", "") // Clé vide - erreur de validation
                        .param("value", "test value")
                        .param("description", "Test description"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings-form"))
                .andExpect(model().hasErrors());
    }

    @Test
    void testEditSettingPage() throws Exception {
        // Récupérer un paramètre existant
        Settings setting = settingsService.setValue("test.edit", "edit value");
        
        mockMvc.perform(get("/admin/settings/edit/{id}", setting.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings-form"))
                .andExpect(model().attributeExists("setting"))
                .andExpect(model().attribute("setting", hasProperty("key", is("test.edit"))));
    }

    @Test
    void testUpdateSetting() throws Exception {
        Settings setting = settingsService.setValue("test.update", "original value");
        
        mockMvc.perform(post("/admin/settings/edit/{id}", setting.getId())
                        .param("key", "test.update")
                        .param("value", "updated value")
                        .param("description", "Updated description")
                        .param("category", "GENERAL")
                        .param("valueType", "STRING")
                        .param("isSystem", "false")
                        .param("sortOrder", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void testDuplicateSetting() throws Exception {
        Settings setting = settingsService.setValue("test.duplicate", "duplicate value");
        
        mockMvc.perform(get("/admin/settings/duplicate/{id}", setting.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings-form"))
                .andExpect(model().attributeExists("setting"))
                .andExpect(model().attribute("setting", hasProperty("key", nullValue())))
                .andExpect(model().attribute("setting", hasProperty("value", is("duplicate value"))));
    }

    @Test
    void testDeleteSetting() throws Exception {
        Settings setting = settingsService.setValue("test.delete", "delete value");
        
        mockMvc.perform(post("/admin/settings/delete/{id}", setting.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void testDeleteSystemSettingFails() throws Exception {
        // Essayer de supprimer un paramètre système
        Settings systemSetting = settingsService.getSettingByKey("app.name").orElseThrow();
        
        mockMvc.perform(post("/admin/settings/delete/{id}", systemSetting.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testInitializeDefaults() throws Exception {
        mockMvc.perform(post("/admin/settings/initialize"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void testRefreshCache() throws Exception {
        mockMvc.perform(post("/admin/settings/refresh-cache"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void testValidateSettings() throws Exception {
        mockMvc.perform(post("/admin/settings/validate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void testExportSettings() throws Exception {
        mockMvc.perform(get("/admin/settings/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].key", notNullValue()))
                .andExpect(jsonPath("$[0].value", notNullValue()));
    }

    // === TESTS API REST ===

    @Test
    void testApiGetAllSettings() throws Exception {
        mockMvc.perform(get("/api/settings")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }

    @Test
    void testApiGetSettingByKey() throws Exception {
        mockMvc.perform(get("/api/settings/app.name")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.key", is("app.name")))
                .andExpect(jsonPath("$.value", is("GESCOM")));
    }

    @Test
    void testApiGetNonExistentSetting() throws Exception {
        mockMvc.perform(get("/api/settings/non.existent.key")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testApiCreateSetting() throws Exception {
        Settings newSetting = new Settings();
        newSetting.setKey("api.test.setting");
        newSetting.setValue("api test value");
        newSetting.setDescription("API test description");
        newSetting.setCategory(Settings.SettingCategory.GENERAL);
        newSetting.setValueType(Settings.ValueType.STRING);

        mockMvc.perform(post("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newSetting)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.key", is("api.test.setting")))
                .andExpect(jsonPath("$.value", is("api test value")));
    }

    @Test
    void testApiUpdateSetting() throws Exception {
        Settings setting = settingsService.setValue("api.update.test", "original");
        setting.setDescription("Updated via API");

        mockMvc.perform(put("/api/settings/{id}", setting.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setting)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.description", is("Updated via API")));
    }

    @Test
    void testApiDeleteSetting() throws Exception {
        Settings setting = settingsService.setValue("api.delete.test", "to delete");

        mockMvc.perform(delete("/api/settings/{id}", setting.getId()))
                .andExpect(status().isNoContent());

        // Vérifier que le paramètre a été supprimé
        mockMvc.perform(get("/api/settings/{key}", "api.delete.test")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testApiSearchSettings() throws Exception {
        mockMvc.perform(get("/api/settings/search")
                        .param("q", "app")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[*].key", everyItem(containsString("app"))));
    }

    @Test
    void testApiGetStats() throws Exception {
        mockMvc.perform(get("/api/settings/stats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalSettings", greaterThan(0)))
                .andExpect(jsonPath("$.systemSettings", greaterThan(0)))
                .andExpect(jsonPath("$.userSettings", greaterThanOrEqualTo(0)));
    }

    @Test
    void testPagination() throws Exception {
        mockMvc.perform(get("/admin/settings")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("settingsPage"))
                .andExpect(model().attribute("currentPage", 0));
    }

    @Test
    void testSettingsNotFound() throws Exception {
        mockMvc.perform(get("/admin/settings/edit/999999"))
                .andExpect(status().isNotFound());
    }
}