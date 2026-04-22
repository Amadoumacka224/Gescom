package com.gescom.backend.controller;

import com.gescom.backend.dto.activity.ActivityLogRequest;
import com.gescom.backend.dto.activity.ActivityLogResponse;
import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.BusinessException;
import com.gescom.backend.service.ActivityLogService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/activities")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        throw new BusinessException("Aucun utilisateur authentifié");
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLogResponse>> getAllActivities() {
        return ResponseEntity.ok(activityLogService.getAllActivities().stream()
                .map(ActivityLogResponse::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ActivityLogResponse> getActivityById(@PathVariable Long id) {
        return activityLogService.getActivityById(id)
                .map(ActivityLogResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#userId)")
    public ResponseEntity<List<ActivityLogResponse>> getActivitiesByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(activityLogService.getActivitiesByUser(userId).stream()
                .map(ActivityLogResponse::from).toList());
    }

    @GetMapping("/action/{actionType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLogResponse>> getActivitiesByActionType(@PathVariable ActivityLog.ActionType actionType) {
        return ResponseEntity.ok(activityLogService.getActivitiesByActionType(actionType).stream()
                .map(ActivityLogResponse::from).toList());
    }

    @GetMapping("/entity/{entity}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLogResponse>> getActivitiesByEntity(@PathVariable String entity) {
        return ResponseEntity.ok(activityLogService.getActivitiesByEntity(entity).stream()
                .map(ActivityLogResponse::from).toList());
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLogResponse>> getActivitiesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(activityLogService.getActivitiesByDateRange(start, end).stream()
                .map(ActivityLogResponse::from).toList());
    }

    @GetMapping("/caissiers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLogResponse>> getCaissierActivities() {
        return ResponseEntity.ok(activityLogService.getCaissierActivities().stream()
                .map(ActivityLogResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<ActivityLogResponse> logActivity(@Valid @RequestBody ActivityLogRequest request) {
        ActivityLog log = activityLogService.logActivity(
                currentUserId(),
                request.actionType(),
                request.entity(),
                request.entityId(),
                request.description(),
                request.details(),
                request.ipAddress());
        return ResponseEntity.status(HttpStatus.CREATED).body(ActivityLogResponse.from(log));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        activityLogService.deleteActivity(id);
        return ResponseEntity.noContent().build();
    }
}
