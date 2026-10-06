package com.umar.ecommerce.user.web.dto;

import com.umar.ecommerce.user.entity.IdentityOperation;

import java.time.Instant;
import java.util.UUID;

public record OperationResponse(
        UUID id,
        String status,
        String operationType,
        UUID userId,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static OperationResponse from(IdentityOperation operation) {
        return new OperationResponse(
                operation.getId(),
                operation.getStatus().name(),
                operation.getOperationType().name(),
                operation.getResultUserId(),
                operation.getLastError(),
                operation.getCreatedAt(),
                operation.getUpdatedAt()
        );
    }
}
