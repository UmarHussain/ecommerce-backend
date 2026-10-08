package com.umar.ecommerce.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.catalog")
public record InventoryCatalogProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration poolAcquireTimeout
) {
}
