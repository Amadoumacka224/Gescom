package com.gescom.backend.controller;

import com.gescom.backend.dto.activity.ActivityLogResponse;
import com.gescom.backend.dto.activity.ActivityLogSummary;
import com.gescom.backend.dto.common.PageResponse;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.mapper.ReferenceMapper;
import com.gescom.backend.service.ActivityLogService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Consultation (lecture seule) du journal d'activité. Les entrées sont produites
 * automatiquement côté serveur par les services métier ; il n'existe volontairement
 * PAS d'endpoint de création ni de suppression exposé, pour préserver l'intégrité de
 * l'audit : une piste que l'administrateur peut élaguer ne prouve plus rien, et c'est
 * précisément lui que le journal doit tracer. Une entrée est donc définitive.
 *
 * Les listes sont paginées ({@link PageResponse}) : c'est le seul registre qui croît sans
 * borne, et le renvoyer d'un bloc chargeait déjà près d'un mégaoctet par appel. Le filtrage
 * est fait en base, faute de quoi il ne porterait que sur la page reçue.
 */
@Tag(name = "Journal d'activite", description = "Tracabilite des operations de l'entreprise")
@RestController
@RequestMapping("/api/activities")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class ActivityLogController {

    private final ActivityLogService activityLogService;
    private final ReferenceMapper referenceMapper;

    public ActivityLogController(ActivityLogService activityLogService, ReferenceMapper referenceMapper) {
        this.activityLogService = activityLogService;
        this.referenceMapper = referenceMapper;
    }

    /** Page du journal. Tous les critères sont optionnels et se combinent. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<ActivityLogResponse>> getAllActivities(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) ActivityLog.ActionType actionType,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                activityLogService.searchActivities(userId, actionType, entity, start, end, search, pageable),
                referenceMapper::toResponse));
    }

    /** Indicateurs sur l'ensemble du journal (la page affichée n'en dit rien). */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ActivityLogSummary> getSummary() {
        return ResponseEntity.ok(activityLogService.getSummary());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ActivityLogResponse> getActivityById(@PathVariable Long id) {
        return activityLogService.getActivityById(id)
                .map(referenceMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("activity", id));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#userId)")
    public ResponseEntity<PageResponse<ActivityLogResponse>> getActivitiesByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                activityLogService.getActivitiesByUser(userId, pageable), referenceMapper::toResponse));
    }

    @GetMapping("/action/{actionType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<ActivityLogResponse>> getActivitiesByActionType(
            @PathVariable ActivityLog.ActionType actionType,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                activityLogService.getActivitiesByActionType(actionType, pageable), referenceMapper::toResponse));
    }

    @GetMapping("/entity/{entity}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<ActivityLogResponse>> getActivitiesByEntity(
            @PathVariable String entity,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                activityLogService.getActivitiesByEntity(entity, pageable), referenceMapper::toResponse));
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<ActivityLogResponse>> getActivitiesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                activityLogService.getActivitiesByDateRange(start, end, pageable), referenceMapper::toResponse));
    }

    @GetMapping("/caissiers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<ActivityLogResponse>> getCaissierActivities(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                activityLogService.getCaissierActivities(pageable), referenceMapper::toResponse));
    }
}
