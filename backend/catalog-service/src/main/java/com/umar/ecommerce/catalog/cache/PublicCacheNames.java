package com.umar.ecommerce.catalog.cache;

/**
 * Public browse regions. Names are the cache key prefix, so they include the
 * service and contract version. Admin reads and the SKU batch lookup are not here.
 */
public final class PublicCacheNames {

    public static final String CATEGORIES = "catalog:v1:categories";
    public static final String PRODUCTS = "catalog:v1:products";
    public static final String PRODUCT_ID = "catalog:v1:product-id";
    public static final String PRODUCT_SLUG = "catalog:v1:product-slug";
    public static final String VARIANTS = "catalog:v1:variants";

    private PublicCacheNames() {
    }
}
