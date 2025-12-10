package com.gescom.backend.controller;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/activities")
@PreAuthorize("hasAnyRole('ADMIN', 'CAISSIER')")
public class ActivityLogController {

    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLog>> getAllActivities() {
        List<ActivityLog> activities = activityLogService.getAllActivities();
        return ResponseEntity.ok(activities);
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
        List<ActivityLog> activities = activityLogService.getActivitiesByUser(userId);
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/action/{actionType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLog>> getActivitiesByActionType(@PathVariable ActivityLog.ActionType actionType) {
        List<ActivityLog> activities = activityLogService.getActivitiesByActionType(actionType);
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/entity/{entity}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLog>> getActivitiesByEntity(@PathVariable String entity) {
        List<ActivityLog> activities = activityLogService.getActivitiesByEntity(entity);
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLog>> getActivitiesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<ActivityLog> activities = activityLogService.getActivitiesByDateRange(start, end);
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/caissiers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ActivityLog>> getCaissierActivities() {
        List<ActivityLog> activities = activityLogService.getCaissierActivities();
        return ResponseEntity.ok(activities);
    }

    @PostMapping
    public ResponseEntity<?> logActivity(@RequestBody Map<String, Object> request) {
        try {
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
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteActivity(@PathVariable Long id) {
        try {
            activityLogService.deleteActivity(id);
            return ResponseEntity.ok().body("Activity log deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
