package com.gescom.backend.dto.category;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        String code,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
