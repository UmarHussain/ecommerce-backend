package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.entity.Category;
import com.umar.ecommerce.catalog.entity.Product;
import com.umar.ecommerce.catalog.entity.ProductVariant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMapperTest {

    private final CatalogMapper mapper = new CatalogMapperImpl();

    @Test
    void mapsVersionAuditAndNestedCategoryFromTheCallerVariantList() throws Exception {
        Instant created = Instant.parse("2026-10-08T10:15:30Z");
        Instant updated = Instant.parse("2026-10-08T11:15:30Z");
        Category category = new Category("Electronics", "electronics");
        setAudit(category, UUID.randomUUID(), created, updated, 3L, true);
        Product product = new Product("Headphones", "headphones", "Over ear", category);
        setAudit(product, UUID.randomUUID(), created, updated, 7L, false);

        ProductVariant active = new ProductVariant(
                product, "HEAD-BLK", "Black", new BigDecimal("10.0000"), "USD", "https://example.test/black.jpg"
        );
        ProductVariant inactive = new ProductVariant(
                product, "HEAD-WHT", "White", new BigDecimal("11.0000"), "USD", null
        );
        setAudit(active, UUID.randomUUID(), created, updated, 1L, true);
        setAudit(inactive, UUID.randomUUID(), created, updated, 2L, false);

        var publicProduct = mapper.toProductResponse(product, List.of(active));
        assertThat(publicProduct.version()).isEqualTo(7L);
        assertThat(publicProduct.createdAt()).isEqualTo(created);
        assertThat(publicProduct.updatedAt()).isEqualTo(updated);
        assertThat(publicProduct.active()).isFalse();
        assertThat(publicProduct.category().id()).isEqualTo(category.getId());
        assertThat(publicProduct.category().version()).isEqualTo(3L);
        assertThat(publicProduct.variants()).extracting(variant -> variant.sku()).containsExactly("HEAD-BLK");
        assertThat(publicProduct.variants().getFirst().productId()).isEqualTo(product.getId());
        assertThat(publicProduct.variants().getFirst().version()).isEqualTo(1L);

        var adminProduct = mapper.toProductResponse(product, List.of(active, inactive));
        assertThat(adminProduct.variants()).extracting(variant -> variant.sku())
                .containsExactly("HEAD-BLK", "HEAD-WHT");
        assertThat(adminProduct.variants().get(1).active()).isFalse();

        var summary = mapper.toProductSummaryResponse(product, List.of(active));
        assertThat(summary.version()).isEqualTo(7L);
        assertThat(summary.variants()).singleElement().satisfies(price -> {
            assertThat(price.sku()).isEqualTo("HEAD-BLK");
            assertThat(price.currency()).isEqualTo("USD");
            assertThat(price.price()).isEqualByComparingTo("10.0000");
        });
    }

    private static void setAudit(
            Object target,
            UUID id,
            Instant createdAt,
            Instant updatedAt,
            long version,
            boolean active
    ) throws Exception {
        setField(target, "id", id);
        setField(target, "createdAt", createdAt);
        setField(target, "updatedAt", updatedAt);
        setField(target, "version", version);
        setField(target, "active", active);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
