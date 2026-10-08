package com.umar.ecommerce.inventory.repository;

import com.umar.ecommerce.inventory.entity.StockItem;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

public final class StockItemSpecifications {

    private StockItemSpecifications() {
    }

    public static Specification<StockItem> search(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = search.trim();
        UUID identifier = parseUuid(term);
        String sku = "%" + term.toLowerCase(Locale.ROOT) + "%";
        return (root, query, builder) -> {
            var skuMatch = builder.like(builder.lower(root.get("sku")), sku);
            if (identifier == null) {
                return skuMatch;
            }
            return builder.or(
                    skuMatch,
                    builder.equal(root.get("id"), identifier),
                    builder.equal(root.get("catalogVariantId"), identifier)
            );
        };
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
