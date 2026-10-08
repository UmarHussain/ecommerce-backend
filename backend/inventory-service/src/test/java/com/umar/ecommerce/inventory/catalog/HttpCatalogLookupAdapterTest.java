package com.umar.ecommerce.inventory.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.umar.ecommerce.inventory.exception.InventoryProblem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCatalogLookupAdapterTest {

    private static final AtomicReference<String> CORRELATION = new AtomicReference<>();
    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer SERVER = start();

    @AfterAll
    static void stop() {
        SERVER.stop(0);
    }

    @Test
    void loadsTheAuthoritativeVariantAndForwardsCorrelation() {
        UUID id = UUID.randomUUID();
        CatalogVariantSnapshot snapshot = adapter(Duration.ofSeconds(2)).load(id, "caller-token", "corr-catalog-9");
        assertThat(snapshot.variantId()).isEqualTo(id);
        assertThat(snapshot.sku()).isEqualTo("CANONICAL-SKU");
        assertThat(snapshot.sellableChain()).isTrue();
        assertThat(CORRELATION.get()).isEqualTo("corr-catalog-9");
        assertThat(AUTHORIZATION.get()).isEqualTo("Bearer caller-token");
    }

    @Test
    void mapsCatalogStatusAndTimeoutsWithoutTreatingFailureAsAValidSku() {
        HttpCatalogLookupAdapter client = adapter(Duration.ofMillis(300));
        assertThatThrownBy(() -> client.load(special("1"), "token", "c1"))
                .isInstanceOf(InventoryProblem.class)
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_VARIANT_NOT_FOUND);
        assertThatThrownBy(() -> client.load(special("5"), "token", "c5"))
                .extracting(error -> ((InventoryProblem) error).status().value())
                .isEqualTo(401);
        assertThatThrownBy(() -> client.load(special("6"), "token", "c6"))
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_FORBIDDEN);
        assertThatThrownBy(() -> client.load(special("3"), "token", "c3"))
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_UNAVAILABLE);
        assertThatThrownBy(() -> client.load(special("4"), "token", "c4"))
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_TIMEOUT);
    }

    private static HttpCatalogLookupAdapter adapter(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(readTimeout);
        factory.setReadTimeout(readTimeout);
        RestClient client = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + SERVER.getAddress().getPort())
                .requestFactory(factory)
                .build();
        return new HttpCatalogLookupAdapter(client, new ObjectMapper());
    }

    private static UUID special(String tail) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + tail);
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                CORRELATION.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
                AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String path = exchange.getRequestURI().getPath();
                String id = path.substring(path.lastIndexOf('/') + 1);
                int status = 200;
                String body;
                if (id.endsWith("000000000001")) {
                    status = 404;
                    body = "{\"title\":\"Not Found\"}";
                } else if (id.endsWith("000000000003")) {
                    status = 503;
                    body = "{\"title\":\"Unavailable\"}";
                } else if (id.endsWith("000000000005")) {
                    status = 401;
                    body = "{\"title\":\"Unauthorized\"}";
                } else if (id.endsWith("000000000006")) {
                    status = 403;
                    body = "{\"title\":\"Forbidden\"}";
                } else if (id.endsWith("000000000004")) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    body = "{}";
                } else if (path.contains("/variants/")) {
                    body = "{\"id\":\"" + id + "\",\"productId\":\"" + id + "\",\"sku\":\"CANONICAL-SKU\",\"name\":\"Black\",\"active\":true}";
                } else {
                    body = "{\"id\":\"" + id + "\",\"name\":\"Headphones\",\"active\":true,\"category\":{\"name\":\"Electronics\",\"active\":true}}";
                }
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, payload.length);
                exchange.getResponseBody().write(payload);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
