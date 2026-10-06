package com.umar.ecommerce.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Compact active variant pricing data for a product summary")
public record VariantPriceResponse(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        String currency,
        String imageUrl
) {
}
