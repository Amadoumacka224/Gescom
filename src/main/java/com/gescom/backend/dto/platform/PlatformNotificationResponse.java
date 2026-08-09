package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;

public record PlatformNotificationResponse(
        Long id,
        String type,
        String severity,
        String title,
        String message,
        Long companyId,
        String companyName,
        String entity,
        Long entityId,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
