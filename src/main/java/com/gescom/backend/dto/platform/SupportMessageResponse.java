package com.gescom.backend.dto.platform;

import java.time.LocalDateTime;

public record SupportMessageResponse(
        Long id,
        String authorName,
        String authorRole,
        String body,
        boolean internal,
        LocalDateTime createdAt
) {
}
