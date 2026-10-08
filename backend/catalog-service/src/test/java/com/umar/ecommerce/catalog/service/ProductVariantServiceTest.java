package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.dto.request.ProductVariantRequest;
import com.umar.ecommerce.catalog.dto.request.ProductVariantUpdateRequest;
import com.umar.ecommerce.catalog.entity.Category;
import com.umar.ecommerce.catalog.entity.Product;
import com.umar.ecommerce.catalog.entity.ProductVariant;
import com.umar.ecommerce.catalog.exception.ResourceConflictException;
import com.umar.ecommerce.catalog.repository.ProductRepository;
import com.umar.ecommerce.catalog.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private ProductRepository productRepository;

    private ProductVariantService service;

    @BeforeEach
    void setUp() {
        service = new ProductVariantService(
                variantRepository,
                productRepository,
                new CatalogMapperImpl()
        );
    }

    @Test
    void rejectsDuplicateNormalizedSkuBeforeInsert() {
        UUID productId = UUID.randomUUID();
        Product product = new Product(
                "Wireless Headphones",
                "wireless-headphones",
                "Everyday headphones",
                new Category("Electronics", "electronics")
        );
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(variantRepository.existsBySku("SKU-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                productId,
                new ProductVariantRequest(
                        " sku-001 ",
                        "Black",
                        new BigDecimal("19.9900"),
                        "USD",
                        "https://example.test/black.jpg"
                )
        )).isInstanceOf(ResourceConflictException.class);

        verify(variantRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSkuChangeAndStaleVersion() throws Exception {
        UUID variantId = UUID.randomUUID();
        Product product = new Product(
                "Wireless Headphones",
                "wireless-headphones",
                "Everyday headphones",
                new Category("Electronics", "electronics")
        );
        ProductVariant variant = new ProductVariant(
                product,
                "SKU-001",
                "Black",
                new BigDecimal("19.9900"),
                "USD",
                null
        );
        setField(variant, "version", 4L);
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        ProductVariantUpdateRequest changedSku = new ProductVariantUpdateRequest(
                "SKU-002",
                "Black",
                new BigDecimal("19.9900"),
                "USD",
                null,
                4L
        );
        assertThatThrownBy(() -> service.update(variantId, changedSku))
                .isInstanceOf(ResourceConflictException.class)
                .extracting(error -> ((ResourceConflictException) error).getCode())
                .isEqualTo(ResourceConflictException.SKU_IMMUTABLE);
        org.assertj.core.api.Assertions.assertThat(variant.getSku()).isEqualTo("SKU-001");

        ProductVariantUpdateRequest stale = new ProductVariantUpdateRequest(
                "SKU-001",
                "Graphite",
                new BigDecimal("21.0000"),
                "USD",
                null,
                3L
        );
        assertThatThrownBy(() -> service.update(variantId, stale))
                .isInstanceOf(ResourceConflictException.class)
                .extracting(error -> ((ResourceConflictException) error).getCode())
                .isEqualTo(ResourceConflictException.STALE_VERSION);

        verify(variantRepository, never()).flush();
    }

    @Test
    void updateKeepsTheNormalizedSku() throws Exception {
        UUID variantId = UUID.randomUUID();
        Product product = new Product(
                "Wireless Headphones",
                "wireless-headphones",
                "Everyday headphones",
                new Category("Electronics", "electronics")
        );
        ProductVariant variant = new ProductVariant(
                product,
                "sku-001",
                "Black",
                new BigDecimal("19.9900"),
                "USD",
                null
        );
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        var response = service.update(variantId, new ProductVariantUpdateRequest(
                " sku-001 ",
                "Graphite",
                new BigDecimal("21.0000"),
                "usd",
                "https://example.test/graphite.jpg",
                0L
        ));

        org.assertj.core.api.Assertions.assertThat(variant.getSku()).isEqualTo("SKU-001");
        org.assertj.core.api.Assertions.assertThat(response.sku()).isEqualTo("SKU-001");
        org.assertj.core.api.Assertions.assertThat(response.name()).isEqualTo("Graphite");
        verify(variantRepository).flush();
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
