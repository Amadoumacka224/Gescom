package com.gescom.backend.dto.platform;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaasPaymentResponse(
        Long id,
        Long companyId,
        String companyName,
        Long subscriptionId,
        String reference,
        BigDecimal amount,
        String currency,
        String status,
        String method,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        LocalDateTime paidAt,
        String failureMessage,
        LocalDateTime createdAt
) {
}
