package com.gescom.backend.controller;

import com.gescom.backend.dto.settings.SettingsRequest;
import com.gescom.backend.entity.Settings;
import com.gescom.backend.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Parametres", description = "Reglages de l'entreprise (devise, TVA, fuseau)")
@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasAnyRole('ADMIN','CAISSIER')")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Lecture autorisée aux caissiers : les écrans de facturation y lisent la TVA et le délai
     * de paiement par défaut. Quand l'accès leur était refusé, ils utilisaient des valeurs en
     * dur et pouvaient facturer avec un autre taux que celui configuré.
     */
    @GetMapping
    public ResponseEntity<Settings> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Settings> updateSettings(@Valid @RequestBody SettingsRequest request) {
        return ResponseEntity.ok(settingsService.updateSettings(request));
    }
}
