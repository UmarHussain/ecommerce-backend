package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogValidationTest {

    @Test
    void normalizesOwnedVariantFields() {
        assertThat(CatalogValidation.normalizeSku(" sku-001 ")).isEqualTo("SKU-001");
        assertThat(CatalogValidation.normalizeCurrency(" usd ")).isEqualTo("USD");
        assertThat(CatalogValidation.validatePrice(new BigDecimal("19.9900"), "price"))
                .isEqualByComparingTo("19.9900");
        assertThat(CatalogValidation.normalizeImageUrl(" https://example.test/image.jpg "))
                .isEqualTo("https://example.test/image.jpg");
    }

    @Test
    void rejectsInvalidVariantMetadata() {
        assertThatThrownBy(() -> CatalogValidation.normalizeCurrency("ZZZ"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> CatalogValidation.validatePrice(
                new BigDecimal("-0.01"),
                "price"
        )).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> CatalogValidation.normalizeImageUrl("file:///tmp/image.jpg"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void normalizesSlugsAndEscapesNoValidationBoundaries() {
        assertThat(CatalogValidation.normalizeSlug(" Headphones ", 160))
                .isEqualTo("headphones");
        assertThatThrownBy(() -> CatalogValidation.normalizeSlug("not valid", 160))
                .isInstanceOf(InvalidRequestException.class);
    }
}
