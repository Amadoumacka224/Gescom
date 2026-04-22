package com.gescom.backend.dto.activity;

import com.gescom.backend.dto.user.UserResponse;
import com.gescom.backend.entity.ActivityLog;

import java.time.LocalDateTime;

public record ActivityLogResponse(
        Long id,
        UserResponse user,
        ActivityLog.ActionType actionType,
        String entity,
        Long entityId,
        String description,
        String ipAddress,
        String details,
        LocalDateTime createdAt
) {
    public static ActivityLogResponse from(ActivityLog log) {
        if (log == null) return null;
        return new ActivityLogResponse(
                log.getId(),
                log.getUser() != null ? UserResponse.from(log.getUser()) : null,
                log.getActionType(),
                log.getEntity(),
                log.getEntityId(),
                log.getDescription(),
                log.getIpAddress(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}
