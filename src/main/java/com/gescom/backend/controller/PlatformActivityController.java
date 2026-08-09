package com.gescom.backend.controller;

import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.dto.platform.PlatformActivityResponse;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.Company;
import com.gescom.backend.entity.User;
import com.gescom.backend.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Journal d'activite consolide de la plateforme.
 *
 * Reutilise {@code ActivityLogService.searchActivities} tel quel : le filtre de cloisonnement
 * etant inactif pour le SUPER_ADMIN, la meme requete qui ne montre a une entreprise que ses
 * propres traces retourne ici celles de tout le parc. C'est le benefice direct d'un
 * cloisonnement porte par l'infrastructure plutot que recopie dans chaque service.
 */
@RestController
@RequestMapping("/api/platform/activity")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Plateforme - Activite", description = "Journal consolide de toutes les entreprises")
public class PlatformActivityController {

    private final ActivityLogService activityLogService;

    public PlatformActivityController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    @Operation(summary = "Journal d'activite de toutes les entreprises")
    public ResponseEntity<PageResponse<PlatformActivityResponse>> list(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        ActivityLog.ActionType parsed = actionType == null || actionType.isBlank()
                ? null
                : ActivityLog.ActionType.valueOf(actionType.trim().toUpperCase(Locale.ROOT));

        Page<PlatformActivityResponse> result = activityLogService
                .searchActivities(null, parsed, entity, null, null, search,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toResponse);

        return ResponseEntity.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    private PlatformActivityResponse toResponse(ActivityLog log) {
        User user = log.getUser();
        Company company = log.getOwnerCompany();
        return new PlatformActivityResponse(
                log.getId(),
                company != null ? company.getId() : null,
                company != null ? company.getName() : null,
                user != null ? user.getFirstName() + " " + user.getLastName() : null,
                user != null ? user.getRole().name() : null,
                log.getActionType().name(),
                log.getEntity(),
                log.getEntityId(),
                log.getDescription(),
                log.getCreatedAt()
        );
    }
}
