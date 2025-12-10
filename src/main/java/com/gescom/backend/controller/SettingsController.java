package com.gescom.backend.controller;

import com.gescom.backend.model.Settings;
import com.gescom.backend.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*", maxAge = 3600, allowedHeaders = "*")
public class SettingsController {

    @Autowired
    private SettingsService settingsService;

    @GetMapping
    public ResponseEntity<Settings> getSettings() {
        Settings settings = settingsService.getSettings();
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    public ResponseEntity<Settings> updateSettings(@RequestBody Settings settings) {
        Settings updatedSettings = settingsService.updateSettings(settings);
        return ResponseEntity.ok(updatedSettings);
    }
}
