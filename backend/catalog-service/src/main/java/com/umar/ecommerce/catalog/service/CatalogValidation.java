package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.exception.InvalidRequestException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

final class CatalogValidation {

    private static final Pattern SLUG_PATTERN =
            Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern SKU_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{2,63}$");

    private CatalogValidation() {
    }

    static String normalizeSlug(String value, int maxLength) {
        String slug = requireText(value, "slug").toLowerCase(Locale.ROOT);
        if (slug.length() > maxLength || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new InvalidRequestException(
                    "slug must contain only letters, numbers, and single hyphens"
            );
        }
        return slug;
    }

    static String normalizeSku(String value) {
        String sku = requireText(value, "sku").toUpperCase(Locale.ROOT);
        if (!SKU_PATTERN.matcher(sku).matches()) {
            throw new InvalidRequestException(
                    "sku must be 3 to 64 characters and contain only letters, numbers, "
                            + "dots, underscores, and hyphens"
            );
        }
        return sku;
    }

    static String normalizeCurrency(String value) {
        String currencyCode = requireText(value, "currency").toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(
                    "currency must be a valid ISO 4217 code",
                    exception
            );
        }
        return currencyCode;
    }

    static BigDecimal validatePrice(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new InvalidRequestException(fieldName + " must not be null");
        }
        if (value.signum() < 0) {
            throw new InvalidRequestException(fieldName + " must not be negative");
        }

        int integerDigits = value.precision() - value.scale();
        if (value.scale() > 4 || value.precision() > 19 || integerDigits > 15) {
            throw new InvalidRequestException(
                    fieldName + " must have at most 15 integer digits and 4 fractional digits"
            );
        }
        return value;
    }

    static String normalizeImageUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String imageUrl = value.trim();
        if (imageUrl.length() > 2048) {
            throw new InvalidRequestException("imageUrl must be at most 2048 characters");
        }

        try {
            URI uri = URI.create(imageUrl);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw new InvalidRequestException(
                        "imageUrl must be an absolute HTTP or HTTPS URL"
                );
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(
                    "imageUrl must be an absolute HTTP or HTTPS URL",
                    exception
            );
        }
        return imageUrl;
    }

    static String normalizeOptionalSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String normalizeOptionalCategorySlug(String value) {
        return value == null ? null : normalizeSlug(value, 120);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
