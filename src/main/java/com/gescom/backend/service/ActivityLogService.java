package com.gescom.backend.service;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import com.gescom.backend.exception.ResourceNotFoundException;
import com.gescom.backend.repository.ActivityLogRepository;
import com.gescom.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository, UserRepository userRepository) {
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getAllActivities() {
        return activityLogRepository.findAllOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<ActivityLog> getActivityById(Long id) {
        return activityLogRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getActivitiesByUser(Long userId) {
        return activityLogRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getActivitiesByActionType(ActivityLog.ActionType actionType) {
        return activityLogRepository.findByActionType(actionType);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getActivitiesByEntity(String entity) {
        return activityLogRepository.findByEntity(entity);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getActivitiesByDateRange(LocalDateTime start, LocalDateTime end) {
        return activityLogRepository.findByCreatedAtBetween(start, end);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getCaissierActivities() {
        return activityLogRepository.findCaissierActivities();
    }

    public ActivityLog logActivity(Long userId, ActivityLog.ActionType actionType, String entity, Long entityId, String description, String details, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

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
                .orElseThrow(() -> new ResourceNotFoundException("Log d'activité", id));
        activityLogRepository.delete(log);
    }
}
