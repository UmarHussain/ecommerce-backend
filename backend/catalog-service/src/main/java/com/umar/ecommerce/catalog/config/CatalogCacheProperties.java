package com.umar.ecommerce.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.catalog-cache")
public record CatalogCacheProperties(
        Duration categoriesTtl,
        Duration productsTtl,
        Duration productTtl,
        Duration variantsTtl,
        Duration ttlJitter
) {
}
