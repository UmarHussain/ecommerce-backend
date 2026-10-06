package com.umar.ecommerce.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Complete product variant representation")
public record ProductVariantResponse(
        UUID id,
        UUID productId,
        String sku,
        String name,
        BigDecimal price,
        String currency,
        String imageUrl,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
