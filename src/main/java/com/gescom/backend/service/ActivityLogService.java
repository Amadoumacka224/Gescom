package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.repository.ActivityLogRepository;
import com.gescom.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ActivityLogService {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private UserRepository userRepository;

    public List<ActivityLog> getAllActivities() {
        return activityLogRepository.findAllOrderByCreatedAtDesc();
    }

    public Optional<ActivityLog> getActivityById(Long id) {
        return activityLogRepository.findById(id);
    }

    public List<ActivityLog> getActivitiesByUser(Long userId) {
        return activityLogRepository.findByUserId(userId);
    }

    public List<ActivityLog> getActivitiesByActionType(ActivityLog.ActionType actionType) {
        return activityLogRepository.findByActionType(actionType);
    }

    public List<ActivityLog> getActivitiesByEntity(String entity) {
        return activityLogRepository.findByEntity(entity);
    }

    public List<ActivityLog> getActivitiesByDateRange(LocalDateTime start, LocalDateTime end) {
        return activityLogRepository.findByCreatedAtBetween(start, end);
    }

    public List<ActivityLog> getCaissierActivities() {
        return activityLogRepository.findCaissierActivities();
    }

    public ActivityLog logActivity(Long userId, ActivityLog.ActionType actionType, String entity, Long entityId, String description, String details, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ActivityLog log = new ActivityLog();
        log.setUser(user);
        log.setActionType(actionType);
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setDetails(details);
        log.setIpAddress(ipAddress);

        return activityLogRepository.save(log);
    }

    public void deleteActivity(Long id) {
        ActivityLog log = activityLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity log not found"));
        activityLogRepository.delete(log);
    }
}
