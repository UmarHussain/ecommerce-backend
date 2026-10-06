package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.dto.request.ProductVariantRequest;
import com.umar.ecommerce.catalog.entity.Category;
import com.umar.ecommerce.catalog.entity.Product;
import com.umar.ecommerce.catalog.exception.ResourceConflictException;
import com.umar.ecommerce.catalog.repository.ProductRepository;
import com.umar.ecommerce.catalog.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
                new CatalogMapper()
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
}
