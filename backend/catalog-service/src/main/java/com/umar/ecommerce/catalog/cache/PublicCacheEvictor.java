package com.umar.ecommerce.catalog.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * Evicts every public browse region. Call this bean from another Spring bean.
 * A direct call from the same instance would skip {@code @CacheEvict}.
 * Clearing the small public regions avoids a partial parent/slug invalidation.
 */
@Component
public class PublicCacheEvictor {

    @CacheEvict(cacheNames = {
            PublicCacheNames.CATEGORIES,
            PublicCacheNames.PRODUCTS,
            PublicCacheNames.PRODUCT_ID,
            PublicCacheNames.PRODUCT_SLUG,
            PublicCacheNames.VARIANTS
    }, allEntries = true)
    public void evictPublicRegions() {
        // The annotation performs the eviction.
    }
}
