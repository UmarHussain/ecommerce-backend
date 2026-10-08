package com.umar.ecommerce.inventory.dto.response;

import com.umar.ecommerce.inventory.domain.OperationScope;
import com.umar.ecommerce.inventory.domain.ReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Immutable stock history row")
public record StockAdjustmentResponse(
        UUID id,
        UUID stockItemId,
        OperationScope operationType,
        int delta,
        int beforeOnHand,
        int afterOnHand,
        int reservedSnapshot,
        long resultingVersion,
        ReasonCode reasonCode,
        String note,
        String reference,
        String actorIssuer,
        String actorSubject,
        Instant createdAt
) {
}
