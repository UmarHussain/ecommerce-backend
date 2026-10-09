package com.umar.ecommerce.cart.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Canonical public catalog variant. Price and name are a display snapshot,
 * not a checkout quote.
 */
public record CatalogVariant(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        String currency,
        String imageUrl
) {
}
