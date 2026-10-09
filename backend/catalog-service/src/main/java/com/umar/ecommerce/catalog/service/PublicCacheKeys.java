package com.umar.ecommerce.catalog.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * Cache keys built from the same normalization the public queries use.
 * Search text is trimmed and lowercased because the SQL comparison is case-insensitive.
 * A currency without a price bound is omitted, matching {@code ProductService}.
 */
public final class PublicCacheKeys {

    private static final Set<String> SORT_FIELDS = Set.of("name", "slug", "createdAt", "updatedAt");

    private PublicCacheKeys() {
    }

    public static String categories() {
        return "all";
    }

    public static String products(
            String search,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency,
            int page,
            int size,
            String sort
    ) {
        String normalizedSearch = CatalogValidation.normalizeOptionalSearch(search);
        String searchKey = normalizedSearch == null
                ? ""
                : normalizedSearch.toLowerCase(Locale.ROOT);
        String categoryKey = categorySlug == null || categorySlug.isBlank()
                ? ""
                : CatalogValidation.normalizeOptionalCategorySlug(categorySlug);
        boolean bounded = minPrice != null || maxPrice != null;
        String currencyKey = "";
        if (bounded) {
            if (currency == null || currency.isBlank()) {
                throw new com.umar.ecommerce.catalog.exception.InvalidRequestException(
                        "currency is required when minPrice or maxPrice is set; "
                                + "price filters compare the stored amount in that currency and do not convert"
                );
            }
            currencyKey = CatalogValidation.normalizeCurrency(currency);
            if (minPrice != null) {
                CatalogValidation.validatePrice(minPrice, "minPrice");
            }
            if (maxPrice != null) {
                CatalogValidation.validatePrice(maxPrice, "maxPrice");
            }
            if (minPrice != null && maxPrice != null && maxPrice.compareTo(minPrice) < 0) {
                throw new com.umar.ecommerce.catalog.exception.InvalidRequestException(
                        "maxPrice must be greater than or equal to minPrice"
                );
            }
        }
        CatalogPaging.validatePage(page, size);
        String sortKey = CatalogPaging.parseSort(sort, SORT_FIELDS).contract();
        return String.join("|",
                searchKey,
                categoryKey,
                money(minPrice),
                money(maxPrice),
                currencyKey,
                Integer.toString(page),
                Integer.toString(size),
                sortKey
        );
    }

    public static String productId(java.util.UUID id) {
        return id.toString();
    }

    public static String productSlug(String slug) {
        return CatalogValidation.normalizeSlug(slug, 160);
    }

    public static String variants(java.util.UUID productId) {
        return productId.toString();
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
