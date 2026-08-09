package com.gescom.backend.controller;

import com.gescom.backend.dto.platform.PlatformDashboardResponse;
import com.gescom.backend.service.PlatformMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tableau de bord du proprietaire de la plateforme.
 *
 * Le prefixe {@code /api/platform} est deja reserve au SUPER_ADMIN par {@code SecurityConfig} ;
 * le {@code @PreAuthorize} de classe redouble la regle au niveau methode, de sorte qu'aucune
 * des deux barrieres ne soit seule a repondre de l'acces.
 */
@RestController
@RequestMapping("/api/platform/dashboard")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme - Tableau de bord", description = "Indicateurs consolides du parc SaaS")
public class PlatformDashboardController {

    private final PlatformMetricsService platformMetricsService;

    public PlatformDashboardController(PlatformMetricsService platformMetricsService) {
        this.platformMetricsService = platformMetricsService;
    }

    @GetMapping
    @Operation(summary = "Indicateurs consolides",
               description = "Entreprises, abonnements, MRR/ARR, paiements, churn, alertes et sante de la plateforme")
    public ResponseEntity<PlatformDashboardResponse> getDashboard() {
        return ResponseEntity.ok(platformMetricsService.buildDashboard());
    }
}
