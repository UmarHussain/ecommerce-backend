package com.umar.ecommerce.inventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Stock pool for one catalog variant. Names are snapshots taken at setup.")
public record StockItemResponse(
        UUID id,
        UUID catalogVariantId,
        String sku,
        int onHand,
        int reserved,
        int available,
        long version,
        String productNameSnapshot,
        String variantNameSnapshot,
        Instant createdAt,
        Instant updatedAt
) {
}
