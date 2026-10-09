package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.exception.InvalidRequestException;
import com.umar.ecommerce.catalog.repository.CategoryRepository;
import com.umar.ecommerce.catalog.repository.ProductRepository;
import com.umar.ecommerce.catalog.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(
                productRepository,
                variantRepository,
                categoryRepository,
                new CatalogMapperImpl(),
                event -> { }
        );
    }

    @Test
    void rejectsInvalidPaginationBeforeQuerying() {
        assertThatThrownBy(() -> service.searchPublicProducts(
                null, null, null, null, null, -1, 20, "name,asc"
        )).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.searchPublicProducts(
                null, null, null, null, null, 0, 101, "name,asc"
        )).isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(productRepository, variantRepository, categoryRepository);
    }

    @Test
    void rejectsInvalidPriceRangeAndSortContract() {
        assertThatThrownBy(() -> service.searchPublicProducts(
                null,
                null,
                new BigDecimal("20"),
                new BigDecimal("10"),
                "USD",
                0,
                20,
                "name,asc"
        )).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.searchPublicProducts(
                null, null, new BigDecimal("10"), null, null, 0, 20, "name,asc"
        )).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.searchPublicProducts(
                null, null, null, null, null, 0, 20, "price,asc"
        )).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.searchPublicProducts(
                null, null, null, null, null, 0, 20, "name,sideways"
        )).isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(productRepository, variantRepository, categoryRepository);
    }
}
