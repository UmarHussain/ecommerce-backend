package com.umar.ecommerce.cart.dto.response;

import com.umar.ecommerce.cart.service.CatalogLineState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CartItemResponse(
        UUID catalogVariantId,
        String sku,
        int quantity,
        String displayName,
        String imageUrl,
        BigDecimal unitPrice,
        String currency,
        Instant snapshotAt,
        BigDecimal lineTotal,
        CatalogLineState catalogState,
        BigDecimal currentUnitPrice
) {
}
