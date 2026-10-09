package com.umar.ecommerce.catalog.cache;

import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.dto.response.PageResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.dto.response.ProductSummaryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import com.umar.ecommerce.catalog.service.CategoryService;
import com.umar.ecommerce.catalog.service.ProductService;
import com.umar.ecommerce.catalog.service.ProductVariantService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Spring cache proxy for public browse reads.
 *
 * <p>{@code @Cacheable} runs only when this bean is called through its Spring
 * proxy. The public controller calls this facade. The facade then calls the
 * domain services. A service method that called another method on the same
 * class would skip the cache. Admin reads and {@code POST /variants/batch}
 * do not use this facade.
 *
 * <p>{@code sync = true} calls {@code Cache.get(key, loader)}. The cache manager
 * wraps Redis with 32 in-process stripes and waits at most two seconds. That
 * coalesces a cold key on this JVM only. It is not a Redis lock. Another
 * instance can load the same key at the same time. A waiter that exceeds two
 * seconds loads the key itself.
 */
@Service
public class PublicBrowseFacade {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductVariantService variantService;

    public PublicBrowseFacade(
            ProductService productService,
            CategoryService categoryService,
            ProductVariantService variantService
    ) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.variantService = variantService;
    }

    @Cacheable(cacheNames = PublicCacheNames.CATEGORIES, keyGenerator = "publicCatalogKeyGenerator", sync = true)
    public List<CategoryResponse> listCategories() {
        return categoryService.listActiveCategories();
    }

    @Cacheable(cacheNames = PublicCacheNames.PRODUCTS, keyGenerator = "publicCatalogKeyGenerator", sync = true)
    public PageResponse<ProductSummaryResponse> searchProducts(
            String search,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency,
            int page,
            int size,
            String sort
    ) {
        return productService.searchPublicProducts(
                search, categorySlug, minPrice, maxPrice, currency, page, size, sort
        );
    }

    @Cacheable(cacheNames = PublicCacheNames.PRODUCT_ID, keyGenerator = "publicCatalogKeyGenerator", sync = true)
    public ProductResponse getProduct(UUID id) {
        return productService.getPublicProduct(id);
    }

    @Cacheable(cacheNames = PublicCacheNames.PRODUCT_SLUG, keyGenerator = "publicCatalogKeyGenerator", sync = true)
    public ProductResponse getProductBySlug(String slug) {
        return productService.getPublicProductBySlug(slug);
    }

    @Cacheable(cacheNames = PublicCacheNames.VARIANTS, keyGenerator = "publicCatalogKeyGenerator", sync = true)
    public List<ProductVariantResponse> listVariants(UUID productId) {
        return variantService.listPublicVariants(productId);
    }
}
