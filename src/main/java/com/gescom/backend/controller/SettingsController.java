package com.gescom.backend.controller;

import com.gescom.backend.dto.settings.SettingsRequest;
import com.gescom.backend.entity.Settings;
import com.gescom.backend.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasAnyRole('ADMIN','CAISSIER')")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Lecture ouverte aux caissiers : les écrans de facturation y prennent le taux de TVA et le
     * délai de paiement par défaut. Leur refuser l'accès les faisait retomber sur des valeurs
     * codées en dur, donc facturer à un autre taux que celui configuré.
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
