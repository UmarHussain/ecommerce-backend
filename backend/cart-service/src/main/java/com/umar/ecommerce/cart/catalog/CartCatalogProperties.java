package com.umar.ecommerce.cart.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.catalog")
public record CartCatalogProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration poolAcquireTimeout
) {
}
