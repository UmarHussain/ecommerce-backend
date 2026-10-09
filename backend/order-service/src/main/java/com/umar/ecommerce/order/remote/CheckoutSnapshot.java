package com.umar.ecommerce.order.remote;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CheckoutSnapshot(
        long cartVersion,
        List<CartLine> lines,
        OwnedAddress address,
        List<CatalogLine> catalog
) {
    public record CartLine(
            UUID catalogVariantId,
            String sku,
            int quantity,
            String displayName,
            BigDecimal unitPrice,
            String currency,
            String catalogState
    ) {
    }

    public record OwnedAddress(
            UUID id,
            String label,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode
    ) {
    }

    public record CatalogLine(UUID id, String sku, String name, BigDecimal price, String currency) {
    }

    public record CartSnapshotView(long version, List<CartLine> lines) {
    }
}
