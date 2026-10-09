package com.umar.ecommerce.cart.service;

import com.umar.ecommerce.cart.entity.Cart;
import com.umar.ecommerce.cart.entity.CartItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record StoredLine(
        UUID catalogVariantId,
        String sku,
        int quantity,
        String displayName,
        String imageUrl,
        BigDecimal unitPrice,
        String currency,
        Instant snapshotAt
) {
    static StoredLine from(CartItem item) {
        return new StoredLine(
                item.getCatalogVariantId(),
                item.getSku(),
                item.getQuantity(),
                item.getDisplayName(),
                item.getImageUrl(),
                item.getUnitPrice(),
                item.getCurrency(),
                item.getSnapshotAt()
        );
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
