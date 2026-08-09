package com.gescom.backend.controller;

import com.gescom.backend.dto.platform.PlatformAccountRequest;
import com.gescom.backend.dto.platform.PlatformSettingsRequest;
import com.gescom.backend.dto.platform.PlatformSettingsResponse;
import com.gescom.backend.entity.PlatformSettings;
import com.gescom.backend.entity.User;
import com.gescom.backend.service.PlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Reglages de la plateforme et compte du proprietaire.
 *
 * A ne pas confondre avec {@code SettingsController}, qui sert le parametrage metier d'une
 * entreprise cliente — raison sociale, TVA, prefixe de facture.
 */
@RestController
@RequestMapping("/api/platform/settings")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme - Parametres", description = "Reglages de la plateforme et compte proprietaire")
public class PlatformSettingsController {

    private final PlatformSettingsService platformSettingsService;

    public PlatformSettingsController(PlatformSettingsService platformSettingsService) {
        this.platformSettingsService = platformSettingsService;
    }

    @GetMapping
    @Operation(summary = "Reglages courants et identite du compte proprietaire")
    public ResponseEntity<PlatformSettingsResponse> get(@AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(
                platformSettingsService.toResponse(platformSettingsService.getSettings(), owner));
    }

    @PutMapping
    @Operation(summary = "Modifier les seuils du tableau de bord",
               description = "Fenetre de renouvellement, alerte de fin d'essai, historique et penalites de sante")
    public ResponseEntity<PlatformSettingsResponse> update(@Valid @RequestBody PlatformSettingsRequest request,
                                                           @AuthenticationPrincipal User owner) {
        PlatformSettings settings = platformSettingsService.updateSettings(request);
        return ResponseEntity.ok(platformSettingsService.toResponse(settings, owner));
    }

    /**
     * Modifie le compte proprietaire.
     *
     * Le principal est pris du contexte de securite et non d'un identifiant de l'URL : cette
     * route ne peut donc modifier que le compte de l'appelant, ce qui la met hors de portee
     * d'une manipulation d'identifiant.
     */
    @PatchMapping("/account")
    @Operation(summary = "Modifier son email ou son mot de passe",
               description = "Le mot de passe actuel est exige dans tous les cas")
    public ResponseEntity<PlatformSettingsResponse> updateAccount(
            @Valid @RequestBody PlatformAccountRequest request,
            @AuthenticationPrincipal User owner) {
        User updated = platformSettingsService.updateAccount(owner, request);
        return ResponseEntity.ok(
                platformSettingsService.toResponse(platformSettingsService.getSettings(), updated));
    }
}
