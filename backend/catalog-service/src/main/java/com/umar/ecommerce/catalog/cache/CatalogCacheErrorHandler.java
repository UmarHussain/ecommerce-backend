package com.umar.ecommerce.catalog.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

/**
 * A cache miss, a bad payload, or a Redis outage falls through to PostgreSQL.
 * The handler does not rethrow, and it does not replace a database failure.
 */
@Component
public class CatalogCacheErrorHandler implements CacheErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log("get", cache, key, exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log("put", cache, key, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log("evict", cache, key, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log("clear", cache, "*", exception);
    }

    private static void log(String operation, Cache cache, Object key, RuntimeException exception) {
        LOGGER.warn(
                "catalog cache {} failed correlationId={} region={} key={} error={}",
                operation,
                MDC.get("correlationId"),
                cache == null ? "unknown" : cache.getName(),
                key,
                exception.toString()
        );
    }
}
