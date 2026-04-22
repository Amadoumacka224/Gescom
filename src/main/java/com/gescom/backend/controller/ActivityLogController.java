package com.gescom.backend.controller;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.service.ActivityLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLog>> getAllActivities() {
        return ResponseEntity.ok(activityLogService.getAllActivities());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ActivityLog> getActivityById(@PathVariable Long id) {
        return activityLogService.getActivityById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @userSecurity.isCurrentUser(#userId)")
    public ResponseEntity<List<ActivityLog>> getActivitiesByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(activityLogService.getActivitiesByUser(userId));
    }

    @GetMapping("/action/{actionType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLog>> getActivitiesByActionType(@PathVariable ActivityLog.ActionType actionType) {
        return ResponseEntity.ok(activityLogService.getActivitiesByActionType(actionType));
    }

    @GetMapping("/entity/{entity}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLog>> getActivitiesByEntity(@PathVariable String entity) {
        return ResponseEntity.ok(activityLogService.getActivitiesByEntity(entity));
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLog>> getActivitiesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(activityLogService.getActivitiesByDateRange(start, end));
    }

    @GetMapping("/caissiers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLog>> getCaissierActivities() {
        return ResponseEntity.ok(activityLogService.getCaissierActivities());
    }

    @PostMapping
    public ResponseEntity<ActivityLog> logActivity(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        ActivityLog.ActionType actionType = ActivityLog.ActionType.valueOf(request.get("actionType").toString());
        String entity = request.get("entity").toString();
        Long entityId = request.containsKey("entityId") && request.get("entityId") != null
                ? Long.valueOf(request.get("entityId").toString())
                : null;
        String description = request.containsKey("description") ? request.get("description").toString() : null;
        String details = request.containsKey("details") ? request.get("details").toString() : null;
        String ipAddress = request.containsKey("ipAddress") ? request.get("ipAddress").toString() : null;

        ActivityLog log = activityLogService.logActivity(userId, actionType, entity, entityId, description, details, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(log);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        activityLogService.deleteActivity(id);
        return ResponseEntity.noContent().build();
    }
}
