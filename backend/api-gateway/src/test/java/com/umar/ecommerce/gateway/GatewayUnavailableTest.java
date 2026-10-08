package com.umar.ecommerce.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayUnavailableTest {

    @Autowired
    WebTestClient client;

    @DynamicPropertySource
    static void closedPorts(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", () -> "http://127.0.0.1:1");
        registry.add("CATALOG_SERVICE_URL", () -> "http://127.0.0.1:1");
        registry.add("platform.gateway.rate-limit.enabled", () -> "false");
        registry.add("platform.security.issuer-uri", () -> "http://localhost:8180/realms/ecommerce-local");
        registry.add("platform.security.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
        registry.add("spring.cloud.gateway.server.webflux.httpclient.connect-timeout", () -> "500");
        registry.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "1s");
    }

    @BeforeEach
    void longerClientTimeout() {
        client = client.mutate().responseTimeout(Duration.ofSeconds(10)).build();
    }

    @Test
    void catalogDownIsServiceUnavailableWithCorrelation() {
        client.get().uri("/api/v1/store/catalog/products")
                .header("X-Correlation-ID", "corr-down")
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectHeader().valueEquals("X-Correlation-ID", "corr-down")
                .expectHeader().contentType("application/problem+json")
                .expectBody()
                .jsonPath("$.code").isEqualTo("GATEWAY_DOWNSTREAM_TIMEOUT")
                .jsonPath("$.correlationId").isEqualTo("corr-down");
    }
}
