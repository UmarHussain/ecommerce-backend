package com.umar.ecommerce.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Product summary with compact active variant pricing")
public record ProductSummaryResponse(
        UUID id,
        String name,
        String slug,
        String description,
        CategoryResponse category,
        List<VariantPriceResponse> variants,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public ProductSummaryResponse {
        variants = List.copyOf(variants);
    }
}
