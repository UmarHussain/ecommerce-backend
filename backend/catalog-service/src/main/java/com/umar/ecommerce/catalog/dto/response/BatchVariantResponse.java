package com.umar.ecommerce.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Active variants found by SKU and normalized SKUs not found")
public record BatchVariantResponse(
        List<ProductVariantResponse> variants,
        List<String> missingSkus
) {
    public BatchVariantResponse {
        variants = List.copyOf(variants);
        missingSkus = List.copyOf(missingSkus);
    }
}
