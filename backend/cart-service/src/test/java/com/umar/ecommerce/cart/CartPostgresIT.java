package com.umar.ecommerce.cart;

import com.sun.net.httpserver.HttpServer;
import com.umar.ecommerce.cart.catalog.HttpPublicCatalogAdapter;
import com.umar.ecommerce.cart.dto.response.CartResponse;
import com.umar.ecommerce.cart.exception.CartProblem;
import com.umar.ecommerce.cart.repository.CartRepository;
import com.umar.ecommerce.cart.service.CartCommandService;
import com.umar.ecommerce.cart.service.CatalogRefresh;
import com.umar.ecommerce.cart.service.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Testcontainers
class CartPostgresIT {

    private static final String CART_USER = "cart_app";
    private static final String CART_PASSWORD = "test_" + UUID.randomUUID().toString().replace("-", "");
    private static final Owner ADA = new Owner("http://issuer.test", "ada");
    private static final Owner GRACE = new Owner("http://issuer.test", "grace");
    private static final AtomicInteger HITS = new AtomicInteger();
    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> CORRELATION = new AtomicReference<>();
    private static final AtomicReference<String> MODE = new AtomicReference<>("ok");
    private static final AtomicInteger HOLD_PERMITS = new AtomicInteger(0);
    private static final AtomicReference<CountDownLatch> HOLD_BATCH = new AtomicReference<>(new CountDownLatch(1));
    private static final AtomicReference<CountDownLatch> HOLD_ENTERED = new AtomicReference<>(new CountDownLatch(1));

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.6-alpine")
    ).withDatabaseName("cart_test");

    private static final HttpServer CATALOG = startCatalog();

    static {
        POSTGRES.start();
        bootstrap();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> CART_USER);
        registry.add("spring.datasource.password", () -> CART_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> "cart");
        registry.add("spring.flyway.default-schema", () -> "cart");
        registry.add("spring.flyway.schemas", () -> "cart");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "cart");
        registry.add("platform.catalog.base-url", () -> "http://127.0.0.1:" + CATALOG.getAddress().getPort());
        registry.add("resilience4j.retry.instances.cartCatalog.waitDuration", () -> "1ms");
        registry.add("resilience4j.retry.instances.cartCatalog.enableExponentialBackoff", () -> "false");
        registry.add("resilience4j.retry.instances.cartCatalog.enableRandomizedWait", () -> "false");
        registry.add("resilience4j.circuitbreaker.instances.cartCatalog.minimumNumberOfCalls", () -> "100");
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CartCommandService carts;
    @Autowired
    private PlatformTransactionManager transactions;
    @MockitoSpyBean
    private HttpPublicCatalogAdapter catalog;
    @MockitoSpyBean
    private CartRepository cartRepository;

    @BeforeEach
    void clean() {
        MODE.set("ok");
        HOLD_PERMITS.set(0);
        HOLD_BATCH.set(new CountDownLatch(1));
        HOLD_ENTERED.set(new CountDownLatch(1));
        org.mockito.Mockito.reset(cartRepository, catalog);
        jdbc.update("DELETE FROM cart_item");
        jdbc.update("DELETE FROM cart");
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(catalog).requireActive(anyString(), any());
    }

    @Test
    void ownCartStoresCanonicalSkuAndGroupsCurrencies() {
        CartResponse first = carts.setQuantity(ADA, "headphones-blk", 2, 0, "corr-ada");
        assertThat(first.version()).isEqualTo(1);
        assertThat(first.items()).singleElement().satisfies(line -> {
            assertThat(line.sku()).isEqualTo("HEADPHONES-BLK");
            assertThat(line.unitPrice()).isEqualByComparingTo("79.9900");
            assertThat(line.currency()).isEqualTo("USD");
            assertThat(line.lineTotal()).isEqualByComparingTo("159.9800");
            assertThat(line.catalogState().name()).isEqualTo("CONFIRMED");
        });
        assertThat(AUTHORIZATION.get()).isNull();
        assertThat(CORRELATION.get()).isEqualTo("corr-ada");

        CartResponse second = carts.setQuantity(ADA, "BOOK-EUR", 1, first.version(), "corr-ada-2");
        assertThat(second.subtotals()).extracting("currency").containsExactly("EUR", "USD");
        assertThat(second.checkoutNotice()).contains("checkout");
        assertThat(carts.get(GRACE, "corr-grace").items()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart", Integer.class)).isEqualTo(2);
    }

    @Test
    void sameQuantityIsANoOpAndAStaleVersionDoesNotChangeTheLine() {
        CartResponse added = carts.setQuantity(ADA, "HEADPHONES-BLK", 1, 0, "c");
        int hits = HITS.get();
        CartResponse again = carts.setQuantity(ADA, "HEADPHONES-BLK", 1, added.version(), "c2");
        assertThat(again.version()).isEqualTo(added.version());
        assertThat(HITS.get()).isEqualTo(hits + 1);

        assertThatThrownBy(() -> carts.setQuantity(ADA, "HEADPHONES-BLK", 3, 0, "stale"))
                .isInstanceOf(CartProblem.class)
                .extracting(error -> ((CartProblem) error).code())
                .isEqualTo(CartProblem.STALE_VERSION);
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_item", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT aggregate_version FROM cart", Long.class)).isEqualTo(1L);
    }

    @Test
    void removalAndReductionWorkWhenCatalogIsDownAndAddDoesNot() {
        CartResponse added = carts.setQuantity(ADA, "HEADPHONES-BLK", 2, 0, "c");
        MODE.set("down");
        CartResponse reduced = carts.setQuantity(ADA, "HEADPHONES-BLK", 1, added.version(), "reduce");
        assertThat(reduced.items().getFirst().quantity()).isEqualTo(1);
        assertThat(reduced.catalogRefresh()).isEqualTo(CatalogRefresh.UNKNOWN);
        CartResponse removed = carts.remove(ADA, "HEADPHONES-BLK", reduced.version(), "remove");
        assertThat(removed.items()).isEmpty();
        assertThat(removed.version()).isEqualTo(reduced.version() + 1);
        assertThatThrownBy(() -> carts.setQuantity(ADA, "HEADPHONES-BLK", 1, removed.version(), "add"))
                .extracting(error -> ((CartProblem) error).code())
                .isEqualTo(CartProblem.CATALOG_UNAVAILABLE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_item", Integer.class)).isZero();
    }

    @Test
    void clearBumpsVersionOnlyWhenSomethingWasRemoved() {
        CartResponse added = carts.setQuantity(ADA, "HEADPHONES-BLK", 1, 0, "c");
        CartResponse cleared = carts.clear(ADA, added.version(), "clear");
        assertThat(cleared.version()).isEqualTo(2);
        assertThat(cleared.items()).isEmpty();
        CartResponse again = carts.clear(ADA, cleared.version(), "clear-empty");
        assertThat(again.version()).isEqualTo(cleared.version());
    }

    @Test
    void quantityAndItemLimitsAreEnforced() {
        assertThatThrownBy(() -> carts.setQuantity(ADA, "HEADPHONES-BLK", 100, 0, "qty"))
                .extracting(error -> ((CartProblem) error).code())
                .isEqualTo(CartProblem.VALIDATION_FAILED);
        CartResponse cart = carts.get(ADA, "seed");
        UUID cartId = jdbc.queryForObject("SELECT id FROM cart WHERE owner_subject = 'ada'", UUID.class);
        for (int index = 0; index < 100; index++) {
            jdbc.update(
                    "INSERT INTO cart_item (id, cart_id, catalog_variant_id, sku, quantity, display_name, unit_price, currency, snapshot_at) "
                            + "VALUES (?, ?, ?, ?, 1, 'Item', 1.0000, 'USD', CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), cartId, UUID.randomUUID(), "SKU-" + index
            );
        }
        assertThatThrownBy(() -> carts.setQuantity(ADA, "HEADPHONES-BLK", 1, cart.version(), "limit"))
                .extracting(error -> ((CartProblem) error).code())
                .isEqualTo(CartProblem.ITEM_LIMIT);
    }

    @Test
    void failedItemSaveRollsTheAggregateBack() {
        carts.get(ADA, "empty");
        doAnswer(invocation -> {
            com.umar.ecommerce.cart.entity.Cart cart = invocation.getArgument(0);
            if (!cart.getItems().isEmpty()) {
                throw new DataAccessException("injected") {
                };
            }
            return invocation.callRealMethod();
        }).when(cartRepository).saveAndFlush(any());
        assertThatThrownBy(() -> carts.setQuantity(ADA, "HEADPHONES-BLK", 1, 0, "boom"))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_item", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT aggregate_version FROM cart", Long.class)).isZero();
    }

    @Test
    void concurrentLazyCreateAndStaleOverlap() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<CartResponse> first = pool.submit(() -> {
            ready.countDown();
            go.await(2, TimeUnit.SECONDS);
            return carts.get(ADA, "lazy-1");
        });
        Future<CartResponse> second = pool.submit(() -> {
            ready.countDown();
            go.await(2, TimeUnit.SECONDS);
            return carts.get(ADA, "lazy-2");
        });
        ready.await(2, TimeUnit.SECONDS);
        go.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).version()).isZero();
        assertThat(second.get(5, TimeUnit.SECONDS).version()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart WHERE owner_subject = 'ada'", Integer.class)).isEqualTo(1);

        HOLD_PERMITS.set(1);
        Future<CartResponse> waiting = pool.submit(() -> carts.setQuantity(ADA, "HEADPHONES-BLK", 1, 0, "overlap"));
        assertThat(HOLD_ENTERED.get().await(2, TimeUnit.SECONDS)).isTrue();
        CartResponse other = carts.setQuantity(ADA, "BOOK-EUR", 1, 0, "other");
        HOLD_BATCH.get().countDown();
        assertThatThrownBy(() -> waiting.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(CartProblem.class);
        assertThat(other.version()).isEqualTo(1);
        pool.shutdownNow();
    }

    @Test
    void coordinatorRejectsAnExistingTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status ->
                carts.get(ADA, "nested")
        )).isInstanceOf(IllegalTransactionStateException.class);
    }

    private static void bootstrap() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + CART_USER + " LOGIN PASSWORD '" + CART_PASSWORD + "'");
            statement.execute("CREATE SCHEMA cart AUTHORIZATION " + CART_USER);
            statement.execute("GRANT ALL ON SCHEMA cart TO " + CART_USER);
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static HttpServer startCatalog() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/api/v1/catalog/variants/batch", exchange -> {
                HITS.incrementAndGet();
                AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
                CORRELATION.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (HOLD_PERMITS.get() > 0) {
                    HOLD_PERMITS.decrementAndGet();
                    HOLD_ENTERED.get().countDown();
                    try {
                        HOLD_BATCH.get().await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
                int status = "down".equals(MODE.get()) ? 503 : 200;
                String payload = status == 503
                        ? "{\"title\":\"down\"}"
                        : batch(body);
                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String batch(String body) {
        boolean eur = body.contains("BOOK-EUR");
        boolean headphones = body.toUpperCase().contains("HEADPHONES-BLK");
        StringBuilder variants = new StringBuilder("[");
        if (headphones) {
            variants.append("""
                    {"id":"30000000-0000-0000-0000-000000000001","sku":"HEADPHONES-BLK","name":"Black","price":79.9900,"currency":"USD","imageUrl":"https://example.com/black.jpg"}""");
        }
        if (eur) {
            if (headphones) {
                variants.append(',');
            }
            variants.append("""
                    {"id":"30000000-0000-0000-0000-000000000009","sku":"BOOK-EUR","name":"Paperback","price":12.5000,"currency":"EUR","imageUrl":null}""");
        }
        variants.append(']');
        return "{\"variants\":" + variants + ",\"missingSkus\":[]}";
    }
}
