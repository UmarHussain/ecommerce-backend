package com.umar.ecommerce.inventory.catalog;

import java.util.UUID;

/**
 * Point-in-time catalog identity used only to set up stock. Catalog remains
 * authoritative for names, SKU, and activation after this snapshot is stored.
 */
public record CatalogVariantSnapshot(
        UUID variantId,
        UUID productId,
        String sku,
        String variantName,
        String productName,
        boolean variantActive,
        boolean productActive,
        boolean categoryActive
) {
    public boolean sellableChain() {
        return variantActive && productActive && categoryActive;
    }
}
