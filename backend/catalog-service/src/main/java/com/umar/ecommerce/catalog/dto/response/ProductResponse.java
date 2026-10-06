package com.umar.ecommerce.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Complete product representation")
public record ProductResponse(
        UUID id,
        String name,
        String slug,
        String description,
        CategoryResponse category,
        List<ProductVariantResponse> variants,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public ProductResponse {
        variants = List.copyOf(variants);
    }
}
