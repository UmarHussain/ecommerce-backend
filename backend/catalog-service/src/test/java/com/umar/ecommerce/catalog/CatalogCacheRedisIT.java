package com.umar.ecommerce.catalog;

import com.umar.ecommerce.catalog.cache.PublicBrowseFacade;
import com.umar.ecommerce.catalog.cache.PublicCacheEvictor;
import com.umar.ecommerce.catalog.cache.PublicCacheNames;
import com.umar.ecommerce.catalog.dto.request.CategoryRequest;
import com.umar.ecommerce.catalog.dto.request.ProductRequest;
import com.umar.ecommerce.catalog.dto.request.ProductVariantRequest;
import com.umar.ecommerce.catalog.dto.response.BatchVariantResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.service.CategoryService;
import com.umar.ecommerce.catalog.service.ProductService;
import com.umar.ecommerce.catalog.service.ProductVariantService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogCacheRedisIT {

    private static final String CATALOG_USER = "catalog_app";
    private static final String CATALOG_PASSWORD = "test_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.6-alpine")
    ).withDatabaseName("catalog_cache_test");

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.5"))
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
        bootstrap();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> CATALOG_USER);
        registry.add("spring.datasource.password", () -> CATALOG_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> "catalog");
        registry.add("spring.flyway.default-schema", () -> "catalog");
        registry.add("spring.flyway.schemas", () -> "catalog");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "catalog");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        registry.add("spring.cache.type", () -> "redis");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.timeout", () -> "5s");
        registry.add("spring.data.redis.connect-timeout", () -> "5s");
        registry.add("platform.catalog-cache.categories-ttl", () -> "60s");
        registry.add("platform.catalog-cache.products-ttl", () -> "45s");
        registry.add("platform.catalog-cache.product-ttl", () -> "45s");
        registry.add("platform.catalog-cache.variants-ttl", () -> "30s");
        registry.add("platform.catalog-cache.ttl-jitter", () -> "5s");
    }

    @Autowired
    private PublicBrowseFacade browse;
    @Autowired
    private CategoryService categories;
    @Autowired
    private ProductService products;
    @Autowired
    private ProductVariantService variants;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private PlatformTransactionManager transactions;
    @MockitoSpyBean
    private PublicCacheEvictor evictor;

    @Test
    @Order(1)
    void missThenHitUsesOneQueryAndNormalizesTheKey() {
        assertThat(cacheManager.getClass().getSimpleName()).isEqualTo("CoalescingCacheManager");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(browse.searchProducts(" Keyboard ", null, null, null, null, 0, 20, "name,asc").items())
                .isNotEmpty();
        long afterMiss = statistics.getPrepareStatementCount();
        assertThat(afterMiss).isPositive();
        browse.searchProducts("keyboard", null, null, null, null, 0, 20, " name,asc ");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(afterMiss);
        String key = redis.keys("catalog:v1:products::*").stream().findFirst().orElseThrow();
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(40L, 50L);
    }

    @Test
    @Order(2)
    void commitEvictsAndRollbackDoesNot() {
        browse.listCategories();
        assertThat(redis.keys("catalog:v1:categories::*")).isNotEmpty();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            categories.create(new CategoryRequest("Rollback Toys", "rollback-toys"));
            status.setRollbackOnly();
        });
        assertThat(redis.keys("catalog:v1:categories::*")).isNotEmpty();
        categories.create(new CategoryRequest("Committed Toys", "committed-toys"));
        assertThat(redis.keys("catalog:v1:categories::*")).isEmpty();
    }

    @Test
    @Order(3)
    void evictionFailureDoesNotFailTheCommittedWrite() {
        doThrow(new IllegalStateException("redis evict failed")).when(evictor).evictPublicRegions();
        var created = categories.create(new CategoryRequest("Kept Toys", "kept-toys"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE slug = 'kept-toys'", Integer.class))
                .isEqualTo(1);
        assertThat(created.slug()).isEqualTo("kept-toys");
        org.mockito.Mockito.reset(evictor);
    }

    @Test
    @Order(4)
    void corruptRedisEntryAndStaleBrowseDoNotChangeTheUncachedBatch() {
        ProductResponse cached = browse.getProductBySlug("wireless-headphones");
        assertThat(cached.variants()).extracting("sku").contains("HEADPHONES-BLK");
        Object stored = cacheManager.getCache(PublicCacheNames.PRODUCT_SLUG)
                .get(com.umar.ecommerce.catalog.service.PublicCacheKeys.productSlug("wireless-headphones"))
                .get();
        assertThat(stored).isInstanceOf(ProductResponse.class);
        browse.listCategories();
        String key = redis.keys("catalog:v1:categories::*").stream().findFirst().orElseThrow();
        redis.opsForValue().set(key, "{not-json");
        assertThat(browse.listCategories()).isNotEmpty();

        jdbc.update("UPDATE product_variant SET active = false WHERE sku = 'HEADPHONES-BLK'");
        ProductResponse stillCached = browse.getProductBySlug("wireless-headphones");
        assertThat(stillCached.variants()).extracting("sku").contains("HEADPHONES-BLK");
        BatchVariantResponse batch = variants.findPublicVariantsBySku(
                new com.umar.ecommerce.catalog.dto.request.BatchVariantRequest(java.util.List.of("headphones-blk"))
        );
        assertThat(batch.variants()).isEmpty();
        assertThat(batch.missingSkus()).contains("HEADPHONES-BLK");
    }

    @Test
    @Order(5)
    void coldLoadCoalescesOnThisInstanceOnly() throws Exception {
        Cache cache = cacheManager.getCache(PublicCacheNames.CATEGORIES);
        assertThat(cache).isNotNull();
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Object> first = pool.submit(() -> cache.get("coalesce-demo", () -> {
            loads.incrementAndGet();
            inside.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return java.util.List.of();
        }));
        assertThat(inside.await(2, TimeUnit.SECONDS)).isTrue();
        Future<Object> second = pool.submit(() -> cache.get("coalesce-demo", () -> {
            loads.incrementAndGet();
            return java.util.List.of();
        }));
        release.countDown();
        first.get(3, TimeUnit.SECONDS);
        second.get(3, TimeUnit.SECONDS);
        assertThat(loads).hasValue(1);
        pool.shutdownNow();
    }

    @Test
    @Order(6)
    void redisOutageFallsBackToPostgres() {
        REDIS.stop();
        try {
            assertThat(browse.listCategories()).isNotEmpty();
        } finally {
            REDIS.start();
        }
    }

    private static void bootstrap() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + CATALOG_USER + " LOGIN PASSWORD '" + CATALOG_PASSWORD + "'");
            statement.execute("CREATE SCHEMA catalog AUTHORIZATION " + CATALOG_USER);
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
