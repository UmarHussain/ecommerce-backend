package com.umar.ecommerce.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRouteTest {

    private static final AtomicReference<Recorded> LAST = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    @Autowired WebTestClient client;
    @MockitoBean ReactiveJwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
        registry.add("USER_SERVICE_URL", () -> base);
        registry.add("CATALOG_SERVICE_URL", () -> base);
        registry.add("INVENTORY_SERVICE_URL", () -> base);
        registry.add("platform.gateway.rate-limit.enabled", () -> "false");
        registry.add("platform.security.issuer-uri", () -> "http://localhost:8180/realms/ecommerce-local");
        registry.add("platform.security.jwk-set-uri", () -> base + "/jwks");
    }

    @BeforeEach
    void decoder() {
        LAST.set(null);
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt(Map.of(
                "user-service", List.of("profile.read_own", "profile.update_own"),
                "api-gateway", List.of("admin.access"),
                "catalog-service", List.of("catalog.create")
        ))));
    }

    @AfterAll
    static void stop() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void publicCatalogIsRewrittenToCatalogService() {
        client.get().uri("/api/v1/store/catalog/products?page=1&size=5")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-ID")
                .expectBody().jsonPath("$.ok").isEqualTo(true);
        Recorded recorded = LAST.get();
        org.junit.jupiter.api.Assertions.assertEquals("/api/v1/catalog/products", recorded.path());
        org.junit.jupiter.api.Assertions.assertEquals("page=1&size=5", recorded.query());
    }

    @Test
    void publicCategoriesAreRewritten() {
        client.get().uri("/api/v1/store/catalog/categories")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals("/api/v1/catalog/categories", LAST.get().path());
    }

    @Test
    void customerProfileAndAddressKeepMethodBodyAndHeaders() {
        client.patch().uri("/api/v1/store/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                .header("X-Correlation-ID", "corr-profile-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"displayName\":\"Ada\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-ID", "corr-profile-1");
        org.junit.jupiter.api.Assertions.assertEquals("/api/v1/users/me", LAST.get().path());
        org.junit.jupiter.api.Assertions.assertEquals("PATCH", LAST.get().method());
        org.junit.jupiter.api.Assertions.assertEquals("Bearer customer-token", LAST.get().authorization());
        org.junit.jupiter.api.Assertions.assertEquals("{\"displayName\":\"Ada\"}", LAST.get().body());

        client.put().uri("/api/v1/store/me/addresses/11111111-1111-1111-1111-111111111111")
                .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"label\":\"Home\"}")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals(
                "/api/v1/users/me/addresses/11111111-1111-1111-1111-111111111111",
                LAST.get().path()
        );
    }

    @Test
    void adminRoutesReachTheOwningService() {
        client.get().uri("/api/v1/admin/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals("/api/v1/admin/me", LAST.get().path());

        client.post().uri("/api/v1/admin/users/22222222-2222-2222-2222-222222222222/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .header("Idempotency-Key", "idem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"roles\":[\"CATALOG_EDITOR\"]}")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals(
                "/api/v1/admin/users/22222222-2222-2222-2222-222222222222/roles",
                LAST.get().path()
        );
        org.junit.jupiter.api.Assertions.assertEquals("idem-1", LAST.get().idempotencyKey());

        client.post().uri("/api/v1/admin/catalog/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Lamp\"}")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals("/api/v1/admin/catalog/products", LAST.get().path());
    }

    @Test
    void missingInvalidAndInsufficientTokensAreRejected() {
        client.get().uri("/api/v1/store/me")
                .exchange()
                .expectStatus().isUnauthorized();

        when(jwtDecoder.decode(anyString())).thenReturn(Mono.error(
                new org.springframework.security.oauth2.jwt.BadJwtException("rejected")));
        client.get().uri("/api/v1/store/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad")
                .exchange()
                .expectStatus().isUnauthorized();

        when(jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt(Map.of(
                "user-service", List.of("profile.read_own")
        ))));
        client.get().uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer customer")
                .exchange()
                .expectStatus().isForbidden();
        org.junit.jupiter.api.Assertions.assertNull(LAST.get());
    }

    @Test
    void publicCatalogDetailSlugVariantsAndBatchAreRewritten() {
        client.get().uri("/api/v1/store/catalog/products/slug/wireless-headphones")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals(
                "/api/v1/catalog/products/slug/wireless-headphones", LAST.get().path());

        client.get().uri("/api/v1/store/catalog/products/20000000-0000-0000-0000-000000000001/variants")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals(
                "/api/v1/catalog/products/20000000-0000-0000-0000-000000000001/variants",
                LAST.get().path());

        client.get().uri("/api/v1/store/catalog/products/20000000-0000-0000-0000-000000000001")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals(
                "/api/v1/catalog/products/20000000-0000-0000-0000-000000000001", LAST.get().path());

        client.post().uri("/api/v1/store/catalog/variants/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"skus\":[\"HEADPHONES-BLK\"]}")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals("POST", LAST.get().method());
        org.junit.jupiter.api.Assertions.assertEquals("/api/v1/catalog/variants/batch", LAST.get().path());
        org.junit.jupiter.api.Assertions.assertEquals("{\"skus\":[\"HEADPHONES-BLK\"]}", LAST.get().body());
    }

    @Test
    void adminCatalogReadsAreExplicitAndProblemsPassThrough() {
        client.get().uri("/api/v1/admin/catalog/categories?active=false")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .header("X-Correlation-ID", "corr-catalog-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-ID", "corr-catalog-1");
        org.junit.jupiter.api.Assertions.assertEquals("/api/v1/admin/catalog/categories", LAST.get().path());
        org.junit.jupiter.api.Assertions.assertEquals("active=false", LAST.get().query());
        org.junit.jupiter.api.Assertions.assertEquals("Bearer admin-token", LAST.get().authorization());

        client.get().uri("/api/v1/admin/catalog/products/20000000-0000-0000-0000-000000000001/variants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals(
                "/api/v1/admin/catalog/products/20000000-0000-0000-0000-000000000001/variants",
                LAST.get().path());

        client.get().uri("/api/v1/admin/catalog/variants/30000000-0000-0000-0000-000000000001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .header("X-Downstream-Status", "409")
                .header("X-Correlation-ID", "corr-conflict")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().valueEquals("X-Correlation-ID", "corr-conflict")
                .expectHeader().contentType("application/problem+json")
                .expectBody()
                .jsonPath("$.code").isEqualTo("CATALOG_STALE_VERSION");
    }

    @Test
    void adminInventoryRoutesAreExplicit() {
        client.get().uri("/api/v1/admin/inventory/stock-items?search=SKU&page=0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .header("X-Correlation-ID", "corr-inventory-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-ID", "corr-inventory-1");
        org.junit.jupiter.api.Assertions.assertEquals("/api/v1/admin/inventory/stock-items", LAST.get().path());
        org.junit.jupiter.api.Assertions.assertEquals("search=SKU&page=0", LAST.get().query());

        client.post().uri("/api/v1/admin/inventory/stock-items/00000000-0000-0000-0000-000000000010/adjustments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .header("Idempotency-Key", "idem-stock")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"delta\":1}")
                .exchange()
                .expectStatus().isOk();
        org.junit.jupiter.api.Assertions.assertEquals("POST", LAST.get().method());
        org.junit.jupiter.api.Assertions.assertEquals(
                "/api/v1/admin/inventory/stock-items/00000000-0000-0000-0000-000000000010/adjustments",
                LAST.get().path()
        );
        org.junit.jupiter.api.Assertions.assertEquals("idem-stock", LAST.get().idempotencyKey());

        client.put().uri("/api/v1/admin/inventory/stock-items/00000000-0000-0000-0000-000000000010")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .exchange()
                .expectStatus().isNotFound();
        org.junit.jupiter.api.Assertions.assertEquals("POST", LAST.get().method());
    }

    @Test
    void browserPreflightFromTheAdminOriginIsAllowed() {
        client.options().uri("/api/v1/admin/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:5174")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174");
    }

    private static Jwt jwt(Map<String, List<String>> rolesByClient) {
        Map<String, Map<String, List<String>>> access = new java.util.LinkedHashMap<>();
        rolesByClient.forEach((client, roles) -> access.put(client, Map.of("roles", roles)));
        return Jwt.withTokenValue("fixture")
                .header("alg", "none")
                .subject("user")
                .issuer("http://localhost:8180/realms/ecommerce-local")
                .audience(List.of("api-gateway"))
                .claim("typ", "Bearer")
                .claim("resource_access", access)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                LAST.set(new Recorded(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().getRawQuery(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                        exchange.getRequestHeaders().getFirst("X-Correlation-ID"),
                        new String(body, StandardCharsets.UTF_8)
                ));
                String forced = exchange.getRequestHeaders().getFirst("X-Downstream-Status");
                int status = forced == null ? 200 : Integer.parseInt(forced);
                String payload = status == 200
                        ? "{\"ok\":true}"
                        : "{\"title\":\"Conflict\",\"status\":409,\"detail\":\"stale\",\"code\":\"CATALOG_STALE_VERSION\"}";
                byte[] response = payload.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add(
                        "Content-Type",
                        status == 200 ? "application/json" : "application/problem+json"
                );
                String correlation = exchange.getRequestHeaders().getFirst("X-Correlation-ID");
                if (correlation != null) {
                    exchange.getResponseHeaders().add("X-Correlation-ID", correlation);
                }
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record Recorded(
            String method,
            String path,
            String query,
            String authorization,
            String idempotencyKey,
            String correlationId,
            String body
    ) {
    }
}
