package com.umar.ecommerce.payment.dto;

import com.umar.ecommerce.payment.domain.AttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentAttemptResponse(
        UUID operationId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        AttemptStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
