package com.gescom.backend.mapper;

import com.gescom.backend.dto.activity.ActivityLogResponse;
import com.gescom.backend.entity.ActivityLog;
import org.springframework.stereotype.Component;

@Component
public class ActivityLogMapper {

    private final UserMapper userMapper;

    public ActivityLogMapper(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public ActivityLogResponse toResponse(ActivityLog log) {
        if (log == null) return null;
        return new ActivityLogResponse(
                log.getId(),
                log.getUser() != null ? userMapper.toResponse(log.getUser()) : null,
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
