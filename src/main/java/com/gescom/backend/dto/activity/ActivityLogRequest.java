package com.gescom.backend.dto.activity;

import com.gescom.backend.entity.ActivityLog;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActivityLogRequest(
        @NotNull(message = "Le type d'action est obligatoire")
        ActivityLog.ActionType actionType,

        @NotBlank(message = "L'entité cible est obligatoire")
        @Size(max = 100)
        String entity,

        Long entityId,

        @Size(max = 500)
        String description,

        String details,

        @Size(max = 50)
        String ipAddress
) {
}
