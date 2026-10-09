package com.umar.ecommerce.catalog.cache;

import com.umar.ecommerce.catalog.service.PublicCacheKeys;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Builds the key before the cached method runs, using the same normalization
 * as the query. Invalid input throws and is not stored.
 */
@Component("publicCatalogKeyGenerator")
public class PublicCatalogKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        return switch (method.getName()) {
            case "listCategories" -> PublicCacheKeys.categories();
            case "searchProducts" -> PublicCacheKeys.products(
                    (String) params[0],
                    (String) params[1],
                    (BigDecimal) params[2],
                    (BigDecimal) params[3],
                    (String) params[4],
                    (Integer) params[5],
                    (Integer) params[6],
                    (String) params[7]
            );
            case "getProduct" -> PublicCacheKeys.productId((UUID) params[0]);
            case "getProductBySlug" -> PublicCacheKeys.productSlug((String) params[0]);
            case "listVariants" -> PublicCacheKeys.variants((UUID) params[0]);
            default -> throw new IllegalStateException("No public cache key for " + method.getName());
        };
    }
}
