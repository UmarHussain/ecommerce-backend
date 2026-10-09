package com.umar.ecommerce.catalog.cache;

import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.dto.response.PageResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.dto.response.ProductSummaryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogCacheSerializationTest {

    @Test
    void publicDtosRoundTripAsTheirDeclaredTypes() {
        var product = sampleProduct();
        ProductResponse restored = roundTrip(ProductResponse.class, product);
        assertThat(restored).isEqualTo(product);
        assertThat(restored.variants().getFirst().price()).isEqualByComparingTo("79.9900");
        assertThat(restored.createdAt()).isEqualTo(product.createdAt());

        PageResponse<ProductSummaryResponse> page = new PageResponse<>(
                List.of(new ProductSummaryResponse(
                        product.id(), product.name(), product.slug(), product.description(), product.category(),
                        List.of(new com.umar.ecommerce.catalog.dto.response.VariantPriceResponse(
                                product.variants().getFirst().id(),
                                product.variants().getFirst().sku(),
                                product.variants().getFirst().name(),
                                product.variants().getFirst().price(),
                                product.variants().getFirst().currency(),
                                product.variants().getFirst().imageUrl()
                        )),
                        true, product.createdAt(), product.updatedAt(), 0
                )),
                0, 20, 1, 1, true, true, "name,asc"
        );
        assertThat(roundTrip(pageType(), page).items().getFirst().slug()).isEqualTo("wireless-headphones");

        List<CategoryResponse> categories = List.of(product.category());
        assertThat(roundTrip(listType(CategoryResponse.class), categories)).containsExactly(product.category());
    }

    private static ProductResponse sampleProduct() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        CategoryResponse category = new CategoryResponse(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "Electronics", "electronics", true, now, now, 0
        );
        ProductVariantResponse variant = new ProductVariantResponse(
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "HEADPHONES-BLK", "Black", new BigDecimal("79.9900"), "USD",
                "https://example.com/black.jpg", true, now, now, 0
        );
        return new ProductResponse(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "Headphones", "wireless-headphones", "Over ear", category,
                List.of(variant), true, now, now, 0
        );
    }

    private static <T> T roundTrip(Class<T> type, T value) {
        var mapper = CatalogCacheConfig.cacheMapper();
        var serializer = new Jackson2JsonRedisSerializer<>(mapper, mapper.constructType(type));
        return type.cast(serializer.deserialize(serializer.serialize(value)));
    }

    private static com.fasterxml.jackson.databind.JavaType pageType() {
        var mapper = CatalogCacheConfig.cacheMapper();
        return mapper.getTypeFactory().constructParametricType(PageResponse.class, ProductSummaryResponse.class);
    }

    private static com.fasterxml.jackson.databind.JavaType listType(Class<?> element) {
        var mapper = CatalogCacheConfig.cacheMapper();
        return mapper.getTypeFactory().constructCollectionType(List.class, element);
    }

    private static <T> T roundTrip(com.fasterxml.jackson.databind.JavaType type, T value) {
        var mapper = CatalogCacheConfig.cacheMapper();
        var serializer = new Jackson2JsonRedisSerializer<T>(mapper, type);
        return serializer.deserialize(serializer.serialize(value));
    }
}
