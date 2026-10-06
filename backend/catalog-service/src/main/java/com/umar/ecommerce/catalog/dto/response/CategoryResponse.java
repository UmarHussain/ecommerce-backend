package com.umar.ecommerce.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Category representation")
public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
