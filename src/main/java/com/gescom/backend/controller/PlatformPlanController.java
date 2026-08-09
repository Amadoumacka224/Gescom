package com.gescom.backend.controller;

import com.gescom.backend.dto.platform.PlanRequest;
import com.gescom.backend.dto.platform.PlanResponse;
import com.gescom.backend.mapper.PlatformMapper;
import com.gescom.backend.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Catalogue commercial : les formules proposees a la souscription.
 *
 * Modifier un tarif ici ne touche pas aux contrats en cours — {@code Subscription.amount}
 * est fige a la souscription. La revision ne vaut donc que pour les souscriptions a venir.
 */
@RestController
@RequestMapping("/api/platform/plans")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme - Formules", description = "Catalogue des formules d'abonnement")
public class PlatformPlanController {

    private final PlanService planService;
    private final PlatformMapper platformMapper;

    public PlatformPlanController(PlanService planService, PlatformMapper platformMapper) {
        this.planService = planService;
        this.platformMapper = platformMapper;
    }

    @GetMapping
    @Operation(summary = "Catalogue complet", description = "Formules actives et retirees")
    public ResponseEntity<List<PlanResponse>> list() {
        return ResponseEntity.ok(planService.getAll().stream()
                .map(platformMapper::toResponse)
                .toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une formule")
    public ResponseEntity<PlanResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(platformMapper.toResponse(planService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Creer une formule")
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformMapper.toResponse(planService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une formule",
               description = "Le code n'est pas modifiable ; les contrats en cours gardent leur tarif")
    public ResponseEntity<PlanResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody PlanRequest request) {
        return ResponseEntity.ok(platformMapper.toResponse(planService.update(id, request)));
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Retirer ou remettre une formule au catalogue",
               description = "Sans effet sur les contrats en cours, qui continuent de courir")
    public ResponseEntity<PlanResponse> setActive(@PathVariable Long id,
                                                  @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return ResponseEntity.ok(platformMapper.toResponse(planService.setActive(id, active)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une formule",
               description = "Refuse des qu'un abonnement, meme resilie, s'y rattache")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
