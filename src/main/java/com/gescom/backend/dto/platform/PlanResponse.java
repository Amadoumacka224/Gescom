package com.gescom.backend.dto.platform;

import java.math.BigDecimal;

public record PlanResponse(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal monthlyPrice,
        BigDecimal yearlyPrice,
        Integer maxUsers,
        Integer maxProducts,
        Integer trialDays,
        Boolean active,
        Integer sortOrder
) {
}
