package com.umar.ecommerce.catalog;

import com.umar.ecommerce.catalog.dto.request.BatchVariantRequest;
import com.umar.ecommerce.catalog.dto.request.CategoryRequest;
import com.umar.ecommerce.catalog.dto.request.CategoryUpdateRequest;
import com.umar.ecommerce.catalog.dto.request.ProductRequest;
import com.umar.ecommerce.catalog.dto.request.ProductVariantRequest;
import com.umar.ecommerce.catalog.dto.request.ProductVariantUpdateRequest;
import com.umar.ecommerce.catalog.dto.response.BatchVariantResponse;
import com.umar.ecommerce.catalog.dto.response.PageResponse;
import com.umar.ecommerce.catalog.dto.response.ProductSummaryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import com.umar.ecommerce.catalog.entity.Category;
import com.umar.ecommerce.catalog.entity.Product;
import com.umar.ecommerce.catalog.entity.ProductVariant;
import com.umar.ecommerce.catalog.repository.CategoryRepository;
import com.umar.ecommerce.catalog.repository.ProductRepository;
import com.umar.ecommerce.catalog.repository.ProductVariantRepository;
import com.umar.ecommerce.catalog.exception.ResourceConflictException;
import com.umar.ecommerce.catalog.exception.ResourceNotFoundException;
import com.umar.ecommerce.catalog.service.CategoryService;
import com.umar.ecommerce.catalog.service.ProductService;
import com.umar.ecommerce.catalog.service.ProductVariantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
class CatalogPostgresIT {

    private static final String CATALOG_USER = "catalog_app";
    private static final String CATALOG_PASSWORD =
            "test_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.6-alpine")
    ).withDatabaseName("catalog_test");

    static {
        POSTGRES.start();
        bootstrapSchemas();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> CATALOG_USER);
        registry.add("spring.datasource.password", () -> CATALOG_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> "catalog");
        registry.add("spring.flyway.default-schema", () -> "catalog");
        registry.add("spring.flyway.schemas", () -> "catalog");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "catalog");
        registry.add("spring.cache.type", () -> "none");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductVariantRepository variantRepository;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductVariantService variantService;
    @Autowired
    private CategoryService categoryService;

    @Test
    void migratesAsCatalogRoleAndLoadsRepeatableLocalSeed() {
        assertThat(jdbcTemplate.queryForObject("select current_user", String.class))
                .isEqualTo(CATALOG_USER);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success",
                Integer.class
        )).isGreaterThanOrEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from category",
                Integer.class
        )).isGreaterThanOrEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes "
                        + "where schemaname = 'catalog' and tablename = 'product_variant'",
                Integer.class
        )).isGreaterThanOrEqualTo(4);
    }

    @Test
    void catalogRoleCannotAccessForeignServiceSchema() {
        assertThatThrownBy(() -> jdbcTemplate.queryForObject(
                "select count(*) from inventory.private_inventory",
                Integer.class
        )).hasMessageContaining("inventory");
    }

    @Test
    void searchesActiveProductsByCategoryPriceAndValidatedSort() {
        PageResponse<ProductSummaryResponse> page = productService.searchPublicProducts(
                "keyboard",
                "electronics",
                new BigDecimal("100.0000"),
                new BigDecimal("120.0000"),
                "USD",
                0,
                10,
                "name,asc"
        );

        assertThat(page.items()).extracting(ProductSummaryResponse::slug)
                .containsExactly("mechanical-keyboard");
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.sort()).isEqualTo("name,asc");
        assertThat(page.items().getFirst().variants())
                .allSatisfy(variant -> {
                    assertThat(variant.sku()).startsWith("KEYBOARD-");
                    assertThat(variant.currency()).isEqualTo("USD");
                });
    }

    @Test
    void batchLookupReturnsSeededSkuAndReportsMissingSku() {
        BatchVariantResponse result = variantService.findPublicVariantsBySku(
                new BatchVariantRequest(List.of("KEYBOARD-US", "MISSING-SKU"))
        );

        assertThat(result.variants()).extracting(ProductVariantResponse::sku)
                .containsExactly("KEYBOARD-US");
        assertThat(result.missingSkus()).containsExactly("MISSING-SKU");
    }

    @Test
    @Transactional
    void repositoryPersistsAndReadsOwnedVariantFields() {
        Category category = categoryRepository.findBySlug("electronics").orElseThrow();
        Product product = productRepository.saveAndFlush(new Product(
                "Temporary Speaker",
                "temporary-speaker-" + UUID.randomUUID().toString().substring(0, 8),
                "Portable speaker",
                category
        ));
        String sku = "SPEAKER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ProductVariant variant = variantRepository.saveAndFlush(new ProductVariant(
                product,
                sku.toLowerCase(),
                "Black",
                new BigDecimal("49.5000"),
                "usd",
                "https://example.test/speaker.jpg"
        ));

        ProductVariant loaded = variantRepository.findBySku(sku).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(variant.getId());
        assertThat(loaded.getSku()).isEqualTo(sku);
        assertThat(loaded.getPrice()).isEqualByComparingTo("49.5000");
        assertThat(loaded.getCurrency()).isEqualTo("USD");
        assertThat(loaded.getProduct().getId()).isEqualTo(product.getId());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getSlug())
                .doesNotContain("sku")
                .isEqualTo(product.getSlug());
    }

    @Test
    void searchesByVariantSkuAndPaginatesWithValidatedSort() {
        PageResponse<ProductSummaryResponse> skuMatch = productService.searchPublicProducts(
                "KEYBOARD-UK",
                null,
                null,
                null,
                null,
                0,
                10,
                "name,asc"
        );
        assertThat(skuMatch.items()).extracting(ProductSummaryResponse::slug)
                .containsExactly("mechanical-keyboard");

        PageResponse<ProductSummaryResponse> firstPage = productService.searchPublicProducts(
                null, null, null, null, null, 0, 1, "name,asc"
        );
        PageResponse<ProductSummaryResponse> secondPage = productService.searchPublicProducts(
                null, null, null, null, null, 1, 1, "name,asc"
        );
        assertThat(firstPage.size()).isEqualTo(1);
        assertThat(firstPage.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(secondPage.items()).isNotEmpty();
        assertThat(secondPage.items().getFirst().slug())
                .isNotEqualTo(firstPage.items().getFirst().slug());
    }

    @Test
    @Transactional
    void databaseRejectsDuplicateSku() {
        Product product = productRepository.findBySlug("wireless-headphones").orElseThrow();
        String sku = "DUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        variantRepository.saveAndFlush(new ProductVariant(
                product,
                sku,
                "First",
                new BigDecimal("10.0000"),
                "USD",
                null
        ));

        assertThatThrownBy(() -> variantRepository.saveAndFlush(new ProductVariant(
                product,
                sku,
                "Second",
                new BigDecimal("11.0000"),
                "USD",
                null
        ))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void databaseEnforcesCatalogConstraintsAndCaseNormalizedSku() {
        UUID productId = jdbcTemplate.queryForObject(
                "select id from product where slug = 'wireless-headphones'",
                UUID.class
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into category (id, name, slug) values (?, 'Duplicate', 'electronics')",
                UUID.randomUUID()
        )).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into product_variant (id, product_id, sku, name, price, currency) "
                        + "values (?, ?, 'HEADPHONES-BLK', 'Dup', 1.0000, 'USD')",
                UUID.randomUUID(),
                productId
        )).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into product_variant (id, product_id, sku, name, price, currency) "
                        + "values (?, ?, 'headphones-case', 'Mixed', 1.0000, 'USD')",
                UUID.randomUUID(),
                productId
        )).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into product_variant (id, product_id, sku, name, price, currency) "
                        + "values (?, ?, 'NEG-PRICE', 'Negative', -1.0000, 'USD')",
                UUID.randomUUID(),
                productId
        )).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into product_variant (id, product_id, sku, name, price, currency) "
                        + "values (?, ?, 'BAD-CUR', 'Currency', 1.0000, 'US')",
                UUID.randomUUID(),
                productId
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void adminReadsIncludeInactiveWhilePublicReadsExcludeTheChain() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var category = categoryService.create(new CategoryRequest("Hidden " + suffix, "hidden-" + suffix));
        categoryService.changeStatus(category.id(), false, category.version());
        var hiddenProduct = productService.create(new ProductRequest(
                "Hidden product " + suffix,
                "hidden-product-" + suffix,
                "not public",
                category.id()
        ));
        var variant = variantService.create(hiddenProduct.id(), new ProductVariantRequest(
                "HIDE-" + suffix.toUpperCase(),
                "Hidden",
                new BigDecimal("12.0000"),
                "USD",
                "https://example.test/hidden.jpg"
        ));

        assertThat(categoryService.getAdmin(category.id()).active()).isFalse();
        assertThat(productService.getAdminProduct(hiddenProduct.id()).variants())
                .extracting(ProductVariantResponse::sku)
                .contains(variant.sku());
        assertThatThrownBy(() -> productService.getPublicProduct(hiddenProduct.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> productService.getPublicProductBySlug(hiddenProduct.slug()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> variantService.listPublicVariants(hiddenProduct.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(variantService.findPublicVariantsBySku(
                new BatchVariantRequest(List.of(variant.sku()))
        ).missingSkus()).containsExactly(variant.sku());
        assertThat(productService.searchPublicProducts(suffix, null, null, null, null, 0, 20, "name,asc").items())
                .isEmpty();
        assertThat(categoryService.searchAdmin(suffix, false, 0, 20, "name,asc").items()).isNotEmpty();
    }

    @Test
    void staleSequentialWritesAndOverlappingUpdatesConflict() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var created = categoryService.create(new CategoryRequest("Versioned " + suffix, "versioned-" + suffix));
        var updated = categoryService.update(
                created.id(),
                new CategoryUpdateRequest(created.name() + " updated", created.slug(), created.version())
        );
        assertThat(updated.version()).isGreaterThan(created.version());
        assertThatThrownBy(() -> categoryService.update(
                created.id(),
                new CategoryUpdateRequest("Stale " + suffix, created.slug(), created.version())
        )).isInstanceOf(ResourceConflictException.class)
                .extracting(error -> ((ResourceConflictException) error).getCode())
                .isEqualTo(ResourceConflictException.STALE_VERSION);
        assertThatThrownBy(() -> categoryService.changeStatus(created.id(), false, created.version()))
                .isInstanceOf(ResourceConflictException.class)
                .extracting(error -> ((ResourceConflictException) error).getCode())
                .isEqualTo(ResourceConflictException.STALE_VERSION);

        long current = categoryService.getAdmin(created.id()).version();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    pool.submit(() -> writeCategory(created.id(), created.slug(), current, "A " + suffix, successes, conflicts)),
                    pool.submit(() -> writeCategory(created.id(), created.slug(), current, "B " + suffix, successes, conflicts))
            );
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
    }

    @Test
    void skuChangeIsRejectedAndConcurrentDuplicatesCannotBothInsert() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var category = categoryService.create(new CategoryRequest("Sku " + suffix, "sku-cat-" + suffix));
        var product = productService.create(new ProductRequest(
                "Sku product " + suffix,
                "sku-product-" + suffix,
                null,
                category.id()
        ));
        var created = variantService.create(product.id(), new ProductVariantRequest(
                "KEEP-" + suffix.toUpperCase(),
                "Keep",
                new BigDecimal("8.0000"),
                "USD",
                null
        ));
        assertThatThrownBy(() -> variantService.update(created.id(), new ProductVariantUpdateRequest(
                "MOVE-" + suffix.toUpperCase(),
                "Keep",
                new BigDecimal("8.0000"),
                "USD",
                null,
                created.version()
        ))).isInstanceOf(ResourceConflictException.class)
                .extracting(error -> ((ResourceConflictException) error).getCode())
                .isEqualTo(ResourceConflictException.SKU_IMMUTABLE);

        var kept = variantService.update(created.id(), new ProductVariantUpdateRequest(
                "keep-" + suffix,
                "Renamed",
                new BigDecimal("9.0000"),
                "USD",
                null,
                created.version()
        ));
        assertThat(kept.sku()).isEqualTo(created.sku());
        assertThat(kept.version()).isGreaterThan(created.version());

        String shared = "RACE-" + suffix.toUpperCase();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    pool.submit(() -> createVariant(product.id(), shared, successes, conflicts)),
                    pool.submit(() -> createVariant(product.id(), shared, successes, conflicts))
            );
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(variantRepository.existsBySku(shared)).isTrue();
    }

    @Test
    void priceFilterRequiresCurrencyAndDoesNotMixCurrencies() {
        assertThatThrownBy(() -> productService.searchPublicProducts(
                null, null, new BigDecimal("1"), null, null, 0, 10, "name,asc"
        )).isInstanceOf(com.umar.ecommerce.catalog.exception.InvalidRequestException.class);

        PageResponse<ProductSummaryResponse> usd = productService.searchAdminProducts(
                "keyboard",
                null,
                true,
                new BigDecimal("100"),
                new BigDecimal("120"),
                "EUR",
                0,
                10,
                "name,asc"
        );
        assertThat(usd.items()).isEmpty();
    }

    private void writeCategory(
            UUID id,
            String slug,
            long version,
            String name,
            AtomicInteger successes,
            AtomicInteger conflicts
    ) {
        try {
            categoryService.update(id, new CategoryUpdateRequest(name, slug, version));
            successes.incrementAndGet();
        } catch (RuntimeException exception) {
            if (isWriteConflict(exception)) {
                conflicts.incrementAndGet();
                return;
            }
            throw exception;
        }
    }

    private void createVariant(UUID productId, String sku, AtomicInteger successes, AtomicInteger conflicts) {
        try {
            variantService.create(productId, new ProductVariantRequest(
                    sku, "Race", new BigDecimal("3.0000"), "USD", null
            ));
            successes.incrementAndGet();
        } catch (RuntimeException exception) {
            if (exception instanceof ResourceConflictException || exception instanceof DataAccessException || isWriteConflict(exception)) {
                conflicts.incrementAndGet();
                return;
            }
            throw exception;
        }
    }

    private static boolean isWriteConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ResourceConflictException conflict
                    && ResourceConflictException.STALE_VERSION.equals(conflict.getCode())) {
                return true;
            }
            if (current instanceof ObjectOptimisticLockingFailureException
                    || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void bootstrapSchemas() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + CATALOG_USER
                    + " LOGIN PASSWORD '" + CATALOG_PASSWORD + "'");
            statement.execute("CREATE SCHEMA catalog AUTHORIZATION " + CATALOG_USER);
            statement.execute("CREATE SCHEMA inventory AUTHORIZATION " + POSTGRES.getUsername());
            statement.execute("REVOKE ALL ON SCHEMA inventory FROM PUBLIC");
            statement.execute("CREATE TABLE inventory.private_inventory (id integer primary key)");
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
