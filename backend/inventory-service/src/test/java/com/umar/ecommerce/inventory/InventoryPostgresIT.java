package com.umar.ecommerce.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.umar.ecommerce.inventory.catalog.CatalogVariantSnapshot;
import com.umar.ecommerce.inventory.catalog.HttpCatalogLookupAdapter;
import com.umar.ecommerce.inventory.domain.ReasonCode;
import com.umar.ecommerce.inventory.domain.RequestFingerprint;
import com.umar.ecommerce.inventory.dto.request.AdjustStockRequest;
import com.umar.ecommerce.inventory.dto.request.SetupStockRequest;
import com.umar.ecommerce.inventory.dto.response.PageResponse;
import com.umar.ecommerce.inventory.dto.response.StockAdjustmentResponse;
import com.umar.ecommerce.inventory.dto.response.StockItemResponse;
import com.umar.ecommerce.inventory.entity.InventoryCommand;
import com.umar.ecommerce.inventory.entity.StockItem;
import com.umar.ecommerce.inventory.exception.InventoryProblem;
import com.umar.ecommerce.inventory.service.Actor;
import com.umar.ecommerce.inventory.service.CommandOutcome;
import com.umar.ecommerce.inventory.service.StockCommandService;
import com.umar.ecommerce.inventory.service.StockQueryService;
import com.umar.ecommerce.inventory.service.StockTransactionService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Testcontainers
class InventoryPostgresIT {

    private static final String INVENTORY_USER = "inventory_app";
    private static final String INVENTORY_PASSWORD = "test_" + UUID.randomUUID().toString().replace("-", "");
    private static final Actor ACTOR = new Actor("http://issuer.test", "subject-1");
    private static final AtomicInteger CATALOG_HITS = new AtomicInteger();
    private static final AtomicReference<String> CORRELATION = new AtomicReference<>();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.6-alpine")
    ).withDatabaseName("inventory_test");

    private static final HttpServer CATALOG = startCatalog();

    static {
        POSTGRES.start();
        bootstrap();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> INVENTORY_USER);
        registry.add("spring.datasource.password", () -> INVENTORY_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> "inventory");
        registry.add("spring.flyway.default-schema", () -> "inventory");
        registry.add("spring.flyway.schemas", () -> "inventory");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "inventory");
        registry.add("platform.catalog.base-url", () -> "http://127.0.0.1:" + CATALOG.getAddress().getPort());
        registry.add("platform.catalog.connect-timeout", () -> "500ms");
        registry.add("platform.catalog.read-timeout", () -> "500ms");
        registry.add("platform.catalog.pool-acquire-timeout", () -> "500ms");
        registry.add("resilience4j.retry.instances.inventoryCatalog.waitDuration", () -> "1ms");
        registry.add("resilience4j.retry.instances.inventoryCatalog.enableExponentialBackoff", () -> "false");
        registry.add("resilience4j.retry.instances.inventoryCatalog.enableRandomizedWait", () -> "false");
    }

    private static final AtomicBoolean INSERT_LISTENER = new AtomicBoolean();
    private static volatile boolean watchInserts;
    private static volatile boolean sawInsertInsideTransaction;
    private static volatile boolean sawInsertOutsideTransaction;
    private static volatile WinnerPlant winnerPlant;
    private static volatile StockPlant stockPlant;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private StockCommandService commands;
    @Autowired
    private StockTransactionService stockTransactions;
    @Autowired
    private StockQueryService queries;
    @MockitoSpyBean
    private HttpCatalogLookupAdapter catalog;

    @BeforeEach
    void catalogLookupStaysOutsideATransaction() {
        ensureInsertListener();
        watchInserts = false;
        winnerPlant = null;
        stockPlant = null;
        resetInsertWatch();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(catalog).load(any(), any(), any());
    }

    @Test
    void migratesAsTheInventoryRole() {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo(INVENTORY_USER);
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void setupStoresTheCatalogSkuAndReplaysWithoutASecondHistoryRow() throws Exception {
        UUID variant = UUID.randomUUID();
        CommandOutcome created = setup(variant, 4, "setup-key-" + variant);
        JsonNode body = objectMapper.readTree(created.body());
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.location()).contains(body.path("stockItem").path("id").asText());
        assertThat(body.path("stockItem").path("sku").asText()).isEqualTo("SKU-" + variant);
        assertThat(body.path("stockItem").path("available").asInt()).isEqualTo(4);
        assertThat(body.path("adjustment").path("delta").asInt()).isEqualTo(4);
        assertThat(body.path("adjustment").path("operationType").asText()).isEqualTo("SETUP");
        assertThat(CORRELATION.get()).isEqualTo("corr-" + variant);
        assertThat(commandCount("setup-key-" + variant)).isEqualTo(1);
        assertThat(commandStatus("setup-key-" + variant)).isEqualTo("COMPLETED");

        CommandOutcome replay = setup(variant, 4, "setup-key-" + variant);
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(created.body());
        UUID stockId = UUID.fromString(body.path("stockItem").path("id").asText());
        assertThat(historyCount(stockId)).isEqualTo(1);
        assertThat(commandCount("setup-key-" + variant)).isEqualTo(1);

        assertThatThrownBy(() -> setup(variant, 9, "setup-key-" + variant))
                .isInstanceOf(InventoryProblem.class)
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.IDEMPOTENCY_CONFLICT);
        assertThat(jdbc.queryForObject(
                "select on_hand from stock_item where id = ?",
                Integer.class,
                stockId
        )).isEqualTo(4);
    }

    @Test
    void zeroOpeningBalanceIsHistoryAndZeroAdjustmentsAreRejected() throws Exception {
        UUID variant = UUID.randomUUID();
        CommandOutcome created = setup(variant, 0, "zero-" + variant);
        assertThat(created.status()).isEqualTo(201);
        UUID stockId = stockId(created);
        CommandOutcome zero = commands.adjust(
                ACTOR,
                "corr-zero",
                stockId,
                "zero-adjust-" + variant,
                new AdjustStockRequest(0, ReasonCode.CORRECTION, 0L, null, null)
        );
        assertThat(zero.status()).isEqualTo(400);
        assertThat(objectMapper.readTree(zero.body()).path("code").asText())
                .isEqualTo(InventoryProblem.VALIDATION_FAILED);
        CommandOutcome replay = commands.adjust(
                ACTOR,
                "corr-zero-replay",
                stockId,
                "zero-adjust-" + variant,
                new AdjustStockRequest(0, ReasonCode.CORRECTION, 0L, null, null)
        );
        assertThat(replay.body()).isEqualTo(zero.body());
        assertThat(historyCount(stockId)).isEqualTo(1);
        assertThat(onHand(stockId)).isEqualTo(0);
    }

    @Test
    void catalogFailuresDoNotMutateInventory() {
        int before = stockCount();
        int hits = CATALOG_HITS.get();
        assertThatThrownBy(() -> setup(special("1"), 1, "missing"))
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_VARIANT_NOT_FOUND);
        assertThatThrownBy(() -> setup(special("2"), 1, "inactive-variant"))
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_INACTIVE);
        assertThatThrownBy(() -> setup(special("7"), 1, "inactive-product"))
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_INACTIVE);
        assertThatThrownBy(() -> setup(special("8"), 1, "inactive-category"))
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_INACTIVE);
        assertThatThrownBy(() -> setup(special("5"), 1, "catalog-401"))
                .extracting(error -> ((InventoryProblem) error).status().value())
                .isEqualTo(401);
        assertThatThrownBy(() -> setup(special("6"), 1, "catalog-403"))
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_FORBIDDEN);
        assertThatThrownBy(() -> setup(special("3"), 1, "catalog-503"))
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_UNAVAILABLE);
        assertThatThrownBy(() -> setup(special("4"), 1, "catalog-timeout"))
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_TIMEOUT);
        Jwt noCatalogRead = jwt(false);
        assertThatThrownBy(() -> commands.setup(
                ACTOR,
                noCatalogRead,
                "token",
                "corr-no-read",
                "no-read",
                new SetupStockRequest(UUID.randomUUID(), 1, ReasonCode.OPENING_BALANCE, null, null)
        )).extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.CATALOG_READ_REQUIRED);
        assertThat(CATALOG_HITS.get()).isEqualTo(hits + 13);
        assertThat(stockCount()).isEqualTo(before);
    }

    @Test
    void adjustmentsHonorReasonSignsOverflowAndDoNotRecheckCatalog() throws Exception {
        UUID variant = UUID.randomUUID();
        UUID stockId = stockId(setup(variant, 5, "signs-" + variant));
        int hits = CATALOG_HITS.get();
        CommandOutcome receipt = adjust(stockId, 2, ReasonCode.INBOUND_RECEIPT, 0, "receipt-" + variant);
        assertThat(receipt.status()).isEqualTo(200);
        assertThat(objectMapper.readTree(receipt.body()).path("stockItem").path("onHand").asInt()).isEqualTo(7);
        CommandOutcome damage = adjust(stockId, -1, ReasonCode.DAMAGE_LOSS, 1, "damage-" + variant);
        assertThat(damage.status()).isEqualTo(200);
        CommandOutcome correction = adjust(stockId, -2, ReasonCode.CORRECTION, 2, "correction-" + variant);
        assertThat(correction.status()).isEqualTo(200);
        CommandOutcome returned = adjust(stockId, 1, ReasonCode.RETURN, 3, "return-" + variant);
        assertThat(returned.status()).isEqualTo(200);
        assertThat(onHand(stockId)).isEqualTo(5);
        CommandOutcome wrongSign = adjust(stockId, -1, ReasonCode.INBOUND_RECEIPT, 4, "wrong-sign-" + variant);
        assertThat(wrongSign.status()).isEqualTo(400);
        CommandOutcome overflow = adjust(stockId, Integer.MAX_VALUE, ReasonCode.CORRECTION, 4, "overflow-" + variant);
        assertThat(objectMapper.readTree(overflow.body()).path("code").asText())
                .isEqualTo(InventoryProblem.QUANTITY_OVERFLOW);
        assertThat(onHand(stockId)).isEqualTo(5);
        assertThat(CATALOG_HITS.get()).isEqualTo(hits);
        assertThat(historyCount(stockId)).isEqualTo(5);
    }

    @Test
    void reservedCeilingRejectsTheAdjustmentAndTheDatabaseRejectsIllegalFixtures() throws Exception {
        UUID variant = UUID.randomUUID();
        UUID stockId = stockId(setup(variant, 5, "reserved-" + variant));
        jdbc.update("update stock_item set reserved = 4 where id = ?", stockId);
        CommandOutcome rejected = adjust(stockId, -2, ReasonCode.DAMAGE_LOSS, 0, "too-low-" + variant);
        assertThat(rejected.status()).isEqualTo(409);
        assertThat(objectMapper.readTree(rejected.body()).path("code").asText())
                .isEqualTo(InventoryProblem.STOCK_INVARIANT);
        assertThat(onHand(stockId)).isEqualTo(5);
        assertThat(historyCount(stockId)).isEqualTo(1);
        CommandOutcome allowed = adjust(stockId, -1, ReasonCode.DAMAGE_LOSS, 0, "to-reserved-" + variant);
        assertThat(allowed.status()).isEqualTo(200);
        assertThat(onHand(stockId)).isEqualTo(4);
        assertThatThrownBy(() -> jdbc.update("update stock_item set on_hand = -1 where id = ?", stockId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update stock_item set reserved = on_hand + 1 where id = ?", stockId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update stock_adjustment set note = 'changed' where stock_item_id = ?", stockId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void sequentialStaleWritesDoNotOverwrite() throws Exception {
        UUID variant = UUID.randomUUID();
        UUID stockId = stockId(setup(variant, 3, "stale-" + variant));
        CommandOutcome first = adjust(stockId, 1, ReasonCode.INBOUND_RECEIPT, 0, "stale-a-" + variant);
        assertThat(first.status()).isEqualTo(200);
        CommandOutcome stale = adjust(stockId, 1, ReasonCode.INBOUND_RECEIPT, 0, "stale-b-" + variant);
        assertThat(objectMapper.readTree(stale.body()).path("code").asText())
                .isEqualTo(InventoryProblem.STALE_VERSION);
        assertThat(commandStatus("stale-b-" + variant)).isEqualTo("COMPLETED");
        CommandOutcome staleReplay = adjust(stockId, 1, ReasonCode.INBOUND_RECEIPT, 0, "stale-b-" + variant);
        assertThat(staleReplay.body()).isEqualTo(stale.body());
        assertThat(onHand(stockId)).isEqualTo(4);
        assertThat(historyCount(stockId)).isEqualTo(2);
        assertThat(commandCount("stale-b-" + variant)).isEqualTo(1);
    }

    @Test
    void overlappingDecrementsCannotMakeStockNegative() throws Exception {
        UUID variant = UUID.randomUUID();
        UUID stockId = stockId(setup(variant, 1, "overlap-" + variant));
        List<CommandOutcome> results = race(() -> adjust(stockId, -1, ReasonCode.DAMAGE_LOSS, 0, "overlap-a-" + variant),
                () -> adjust(stockId, -1, ReasonCode.DAMAGE_LOSS, 0, "overlap-b-" + variant));
        assertThat(results).filteredOn(result -> result.status() == 200).hasSize(1);
        assertThat(results).filteredOn(result -> result.status() == 409).hasSize(1);
        assertThat(onHand(stockId)).isZero();
        assertThat(historyCount(stockId)).isEqualTo(2);
    }

    @Test
    void concurrentSetupCreatesOneStockRow() throws Exception {
        UUID variant = UUID.randomUUID();
        String keyA = "setup-a-" + variant;
        String keyB = "setup-b-" + variant;
        List<CommandOutcome> results = race(
                () -> setup(variant, 8, keyA),
                () -> setup(variant, 8, keyB)
        );
        assertThat(results).filteredOn(result -> result.status() == 201).hasSize(1);
        assertThat(results).filteredOn(result -> result.status() == 409).hasSize(1);
        CommandOutcome replayA = setup(variant, 8, keyA);
        CommandOutcome replayB = setup(variant, 8, keyB);
        assertThat(List.of(replayA.body(), replayB.body()))
                .containsExactlyInAnyOrder(results.get(0).body(), results.get(1).body());
        assertThat(commandCount(keyA)).isEqualTo(1);
        assertThat(commandCount(keyB)).isEqualTo(1);
        assertThat(commandStatus(keyA)).isEqualTo("COMPLETED");
        assertThat(commandStatus(keyB)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "select count(*) from stock_item where catalog_variant_id = ?",
                Integer.class,
                variant
        )).isEqualTo(1);
        UUID stockId = jdbc.queryForObject(
                "select id from stock_item where catalog_variant_id = ?",
                UUID.class,
                variant
        );
        assertThat(historyCount(stockId)).isEqualTo(1);
        assertThat(onHand(stockId)).isEqualTo(8);
    }

    @Test
    void concurrentIdenticalCommandsReplayOneEffect() throws Exception {
        UUID variant = UUID.randomUUID();
        String key = "same-" + variant;
        List<CommandOutcome> results = race(() -> setup(variant, 2, key), () -> setup(variant, 2, key));
        assertThat(results).allSatisfy(result -> assertThat(result.status()).isEqualTo(201));
        assertThat(results.get(0).body()).isEqualTo(results.get(1).body());
        assertThat(jdbc.queryForObject(
                "select count(*) from stock_item where catalog_variant_id = ?",
                Integer.class,
                variant
        )).isEqualTo(1);
        UUID stockId = jdbc.queryForObject(
                "select id from stock_item where catalog_variant_id = ?",
                UUID.class,
                variant
        );
        assertThat(historyCount(stockId)).isEqualTo(1);
        assertThat(commandCount(key)).isEqualTo(1);
        assertThat(commandStatus(key)).isEqualTo("COMPLETED");
    }

    @Test
    void listsStockAndHistoryInAllowlistedOrder() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        registerSku(first, "AAA-" + first.toString().substring(0, 8));
        registerSku(second, "MMM-" + second.toString().substring(0, 8));
        registerSku(third, "ZZZ-" + third.toString().substring(0, 8));
        UUID low = stockId(setup(first, 1, "page-a-" + first));
        UUID mid = stockId(setup(second, 1, "page-b-" + second));
        stockId(setup(third, 1, "page-c-" + third));
        adjust(low, 1, ReasonCode.INBOUND_RECEIPT, 0, "hist-a-" + first);
        adjust(low, -1, ReasonCode.CORRECTION, 1, "hist-b-" + first);
        PageResponse<StockItemResponse> page = queries.list("AAA-", 0, 1, "sku,asc");
        assertThat(page.items()).hasSize(1);
        assertThat(page.sort()).isEqualTo("sku,asc");
        assertThat(page.items().getFirst().sku()).startsWith("AAA-");
        PageResponse<StockAdjustmentResponse> history = queries.history(low, 0, 10, "createdAt,desc");
        assertThat(history.items()).hasSize(3);
        assertThat(history.sort()).isEqualTo("createdAt,desc");
        assertThat(history.items().getFirst().createdAt())
                .isAfterOrEqualTo(history.items().get(2).createdAt());
        assertThat(queries.get(mid).available()).isEqualTo(1);
        assertThatThrownBy(() -> queries.list(null, 0, 20, "reserved,asc"))
                .isInstanceOf(InventoryProblem.class);
    }

    @Test
    void catalogLookupRunsWithNoActiveTransaction() throws Exception {
        UUID variant = UUID.randomUUID();
        AtomicInteger lookups = new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            lookups.incrementAndGet();
            return invocation.callRealMethod();
        }).when(catalog).load(any(), any(), any());
        resetInsertWatch();
        watchInserts = true;
        int hits = CATALOG_HITS.get();
        try {
            String key = "catalog-tx-" + variant;
            CommandOutcome created = setup(variant, 3, key);
            assertThat(created.status()).isEqualTo(201);
            assertThat(lookups.get()).isEqualTo(1);
            assertThat(CATALOG_HITS.get()).isEqualTo(hits + 2);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(sawInsertInsideTransaction).isTrue();
            assertThat(sawInsertOutsideTransaction).isFalse();
            UUID stockId = stockId(created);
            assertThat(onHand(stockId)).isEqualTo(3);
            assertThat(historyCount(stockId)).isEqualTo(1);
            assertThat(commandStatus(key)).isEqualTo("COMPLETED");
        } finally {
            watchInserts = false;
        }
    }

    @Test
    void writerEntryMethodsRunInsideANewTransactionAndCommit() throws Exception {
        UUID variant = UUID.randomUUID();
        String setupKey = "writer-setup-" + variant;
        resetInsertWatch();
        watchInserts = true;
        try {
            CommandOutcome created = stockTransactions.writeSetup(new StockTransactionService.SetupWrite(
                    ACTOR,
                    setupKey,
                    RequestFingerprint.setup(variant, 3, ReasonCode.OPENING_BALANCE, "note", "ref"),
                    "corr-writer",
                    snapshot(variant),
                    3,
                    "note",
                    "ref"
            ));
            assertThat(created.status()).isEqualTo(201);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(sawInsertInsideTransaction).isTrue();
            assertThat(sawInsertOutsideTransaction).isFalse();
            UUID stockId = stockId(created);
            assertThat(countVisibleToOtherConnection(
                    "select count(*) from stock_item where id = ?",
                    stockId
            )).isEqualTo(1);
            assertThat(historyCount(stockId)).isEqualTo(1);
            assertThat(commandStatus(setupKey)).isEqualTo("COMPLETED");

            resetInsertWatch();
            watchInserts = true;
            String adjustKey = "writer-adjust-" + variant;
            CommandOutcome adjusted = stockTransactions.writeAdjustment(new StockTransactionService.AdjustmentWrite(
                    ACTOR,
                    stockId,
                    adjustKey,
                    RequestFingerprint.adjustment(stockId, 1, ReasonCode.INBOUND_RECEIPT, "note", "ref", 0L),
                    "corr-writer",
                    1,
                    ReasonCode.INBOUND_RECEIPT,
                    0L,
                    "note",
                    "ref"
            ));
            assertThat(adjusted.status()).isEqualTo(200);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(sawInsertInsideTransaction).isTrue();
            assertThat(sawInsertOutsideTransaction).isFalse();
            assertThat(onHand(stockId)).isEqualTo(4);
            assertThat(historyCount(stockId)).isEqualTo(2);
            assertThat(commandStatus(adjustKey)).isEqualTo("COMPLETED");
        } finally {
            watchInserts = false;
        }
    }

    @Test
    @Transactional
    void writerJoinsAnExistingTransactionInsteadOfCommitting() throws Exception {
        UUID variant = UUID.randomUUID();
        resetInsertWatch();
        watchInserts = true;
        try {
            CommandOutcome outcome = stockTransactions.writeSetup(new StockTransactionService.SetupWrite(
                    ACTOR,
                    "join-" + variant,
                    RequestFingerprint.setup(variant, 3, ReasonCode.OPENING_BALANCE, "opening", "ref"),
                    "corr-join",
                    snapshot(variant),
                    3,
                    "opening",
                    "ref"
            ));
            assertThat(outcome.status()).isEqualTo(201);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(sawInsertInsideTransaction).isTrue();
            assertThat(sawInsertOutsideTransaction).isFalse();
            assertThat(countVisibleToOtherConnection(
                    "select count(*) from stock_item where catalog_variant_id = ?",
                    variant
            )).isZero();
        } finally {
            watchInserts = false;
        }
    }

    @Test
    @Transactional
    void coordinatorRejectsAnExistingTransaction() {
        UUID variant = UUID.randomUUID();
        assertThatThrownBy(() -> setup(variant, 1, "never-setup-" + variant))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> commands.adjust(
                ACTOR,
                "corr-never",
                UUID.randomUUID(),
                "never-adjust-" + variant,
                new AdjustStockRequest(1, ReasonCode.INBOUND_RECEIPT, 0L, null, null)
        )).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    }

    @Test
    void injectedPersistenceFailureRollsTheWriteBack() {
        UUID variant = UUID.randomUUID();
        String key = "rollback-" + variant;
        jdbc.execute("""
                create table if not exists test_fault (
                    name varchar(80) primary key
                )
                """);
        jdbc.execute("""
                create or replace function inventory.reject_injected_adjustment()
                returns trigger
                language plpgsql
                as $$
                begin
                    if exists (select 1 from inventory.test_fault where name = 'adjustment-insert') then
                        raise exception 'injected adjustment insert failure';
                    end if;
                    return new;
                end;
                $$
                """);
        jdbc.execute("drop trigger if exists reject_injected_adjustment on stock_adjustment");
        jdbc.execute("""
                create trigger reject_injected_adjustment
                before insert on stock_adjustment
                for each row
                execute function inventory.reject_injected_adjustment()
                """);
        int stocks = stockCount();
        int adjustments = jdbc.queryForObject("select count(*) from stock_adjustment", Integer.class);
        int commands = jdbc.queryForObject("select count(*) from inventory_command", Integer.class);
        try {
            jdbc.update("insert into test_fault(name) values ('adjustment-insert')");
            assertThatThrownBy(() -> setup(variant, 3, key)).isInstanceOf(DataAccessException.class);
            assertThat(stockCount()).isEqualTo(stocks);
            assertThat(jdbc.queryForObject("select count(*) from stock_adjustment", Integer.class)).isEqualTo(adjustments);
            assertThat(jdbc.queryForObject("select count(*) from inventory_command", Integer.class)).isEqualTo(commands);
            assertThat(commandCount(key)).isZero();
        } finally {
            jdbc.update("delete from test_fault where name = 'adjustment-insert'");
            jdbc.execute("drop trigger if exists reject_injected_adjustment on stock_adjustment");
            jdbc.execute("drop function if exists inventory.reject_injected_adjustment()");
            jdbc.execute("drop table if exists test_fault");
        }
    }

    @Test
    void sameKeyDuplicateRecoveryReplaysTheWinnerAfterRollback() throws Exception {
        UUID variant = UUID.randomUUID();
        String key = "dup-" + variant;
        String fingerprint = RequestFingerprint.setup(variant, 4, ReasonCode.OPENING_BALANCE, "opening", "ref");
        String stored = "{\"replayed\":\"" + key + "\"}";
        int hits = CATALOG_HITS.get();
        int stocks = stockCount();
        winnerPlant = new WinnerPlant(
                fingerprint,
                "SETUP",
                key,
                stored,
                "/api/v1/admin/inventory/stock-items/replayed",
                201,
                new AtomicBoolean()
        );
        try {
            CommandOutcome outcome = setup(variant, 4, key);
            assertThat(winnerPlant.planted()).isTrue();
            assertThat(outcome.status()).isEqualTo(201);
            assertThat(outcome.body()).isEqualTo(stored);
            assertThat(outcome.location()).isEqualTo("/api/v1/admin/inventory/stock-items/replayed");
            assertThat(CATALOG_HITS.get()).isEqualTo(hits + 2);
            assertThat(stockCount()).isEqualTo(stocks);
            assertThat(commandCount(key)).isEqualTo(1);
            assertThat(commandStatus(key)).isEqualTo("COMPLETED");
            CommandOutcome replay = setup(variant, 4, key);
            assertThat(replay.body()).isEqualTo(stored);
            assertThat(CATALOG_HITS.get()).isEqualTo(hits + 2);
            assertThat(stockCount()).isEqualTo(stocks);
            assertThat(commandCount(key)).isEqualTo(1);
        } finally {
            winnerPlant = null;
        }
    }

    @Test
    void sameKeyAdjustmentRecoveryReplaysTheWinnerAfterRollback() {
        UUID stockId = UUID.randomUUID();
        String key = "dup-adj-" + stockId;
        String fingerprint = RequestFingerprint.adjustment(
                stockId,
                1,
                ReasonCode.INBOUND_RECEIPT,
                "note",
                "ref",
                0L
        );
        String stored = "{\"replayed\":\"adjustment\"}";
        winnerPlant = new WinnerPlant(fingerprint, "ADJUSTMENT", key, stored, null, 200, new AtomicBoolean());
        try {
            CommandOutcome outcome = adjust(stockId, 1, ReasonCode.INBOUND_RECEIPT, 0, key);
            assertThat(winnerPlant.planted()).isTrue();
            assertThat(outcome.status()).isEqualTo(200);
            assertThat(outcome.body()).isEqualTo(stored);
            assertThat(commandCount(key)).isEqualTo(1);
            assertThat(commandStatus(key)).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject(
                    "select count(*) from stock_item where id = ?",
                    Integer.class,
                    stockId
            )).isZero();
            CommandOutcome replay = adjust(stockId, 1, ReasonCode.INBOUND_RECEIPT, 0, key);
            assertThat(replay.body()).isEqualTo(stored);
            assertThat(commandCount(key)).isEqualTo(1);
        } finally {
            winnerPlant = null;
        }
    }

    @Test
    void duplicateSkuRecordsTheSetupConflictInANewTransaction() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String sku = "SAME-" + first.toString().substring(0, 8);
        registerSku(first, sku);
        registerSku(second, sku);
        CommandOutcome created = setup(first, 2, "sku-a-" + first);
        assertThat(created.status()).isEqualTo(201);
        String loserKey = "sku-b-" + second;
        resetInsertWatch();
        watchInserts = true;
        try {
            CommandOutcome conflict = setup(second, 2, loserKey);
            assertThat(conflict.status()).isEqualTo(409);
            assertThat(objectMapper.readTree(conflict.body()).path("code").asText())
                    .isEqualTo(InventoryProblem.VARIANT_ALREADY_STOCKED);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(sawInsertInsideTransaction).isTrue();
            assertThat(sawInsertOutsideTransaction).isFalse();
            assertThat(jdbc.queryForObject(
                    "select count(*) from stock_item where sku = ?",
                    Integer.class,
                    sku
            )).isEqualTo(1);
            assertThat(commandCount(loserKey)).isEqualTo(1);
            assertThat(commandStatus(loserKey)).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject(
                    "select http_status from inventory_command where idempotency_key = ?",
                    Integer.class,
                    loserKey
            )).isEqualTo(409);
            CommandOutcome replay = setup(second, 2, loserKey);
            assertThat(replay.body()).isEqualTo(conflict.body());
            assertThat(jdbc.queryForObject(
                    "select count(*) from stock_item where sku = ?",
                    Integer.class,
                    sku
            )).isEqualTo(1);
        } finally {
            watchInserts = false;
        }
    }

    @Test
    void variantUniquenessRecoveryRecordsTheConflictInANewTransaction() throws Exception {
        UUID variant = UUID.randomUUID();
        String key = "variant-race-" + variant;
        stockPlant = new StockPlant(variant, new AtomicBoolean());
        try {
            CommandOutcome outcome = setup(variant, 6, key);
            assertThat(stockPlant.planted()).isTrue();
            assertThat(outcome.status()).isEqualTo(409);
            assertThat(objectMapper.readTree(outcome.body()).path("code").asText())
                    .isEqualTo(InventoryProblem.VARIANT_ALREADY_STOCKED);
            assertThat(jdbc.queryForObject(
                    "select count(*) from stock_item where catalog_variant_id = ?",
                    Integer.class,
                    variant
            )).isEqualTo(1);
            assertThat(commandCount(key)).isEqualTo(1);
            assertThat(commandStatus(key)).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject(
                    """
                    select count(*) from stock_adjustment adjustment
                    join stock_item item on item.id = adjustment.stock_item_id
                    where item.catalog_variant_id = ?
                    """,
                    Integer.class,
                    variant
            )).isZero();
            int hits = CATALOG_HITS.get();
            CommandOutcome replay = setup(variant, 6, key);
            assertThat(replay.body()).isEqualTo(outcome.body());
            assertThat(CATALOG_HITS.get()).isEqualTo(hits);
        } finally {
            stockPlant = null;
        }
    }

    @Test
    void missingStockIsStoredAndReplayable() throws Exception {
        UUID missing = UUID.randomUUID();
        String key = "missing-" + missing;
        CommandOutcome missingStock = adjust(missing, 1, ReasonCode.INBOUND_RECEIPT, 0, key);
        assertThat(missingStock.status()).isEqualTo(404);
        assertThat(objectMapper.readTree(missingStock.body()).path("code").asText())
                .isEqualTo(InventoryProblem.NOT_FOUND);
        assertThat(commandStatus(key)).isEqualTo("COMPLETED");
        CommandOutcome replay = adjust(missing, 1, ReasonCode.INBOUND_RECEIPT, 0, key);
        assertThat(replay.body()).isEqualTo(missingStock.body());
        assertThat(commandCount(key)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from stock_item where id = ?",
                Integer.class,
                missing
        )).isZero();
    }

    @Test
    void lockTimeoutStaysInProgressAndRollsBackTheCommand() throws Exception {
        UUID variant = UUID.randomUUID();
        UUID stockId = stockId(setup(variant, 5, "lock-setup-" + variant));
        String key = "lock-" + variant;
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                INVENTORY_USER,
                INVENTORY_PASSWORD
        )) {
            connection.setAutoCommit(false);
            connection.setSchema("inventory");
            try (PreparedStatement statement = connection.prepareStatement(
                    "select id from stock_item where id = ? for update")) {
                statement.setObject(1, stockId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }
            long started = System.nanoTime();
            assertThatThrownBy(() -> adjust(stockId, 1, ReasonCode.INBOUND_RECEIPT, 0, key))
                    .isInstanceOf(InventoryProblem.class)
                    .extracting(error -> ((InventoryProblem) error).code())
                    .isEqualTo(InventoryProblem.COMMAND_IN_PROGRESS);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertThat(elapsedMs).isGreaterThanOrEqualTo(1_000L);
            assertThat(commandCount(key)).isZero();
            assertThat(onHand(stockId)).isEqualTo(5);
            assertThat(historyCount(stockId)).isEqualTo(1);
        }
    }

    private CommandOutcome setup(UUID variant, int initialOnHand, String key) {
        return commands.setup(
                ACTOR,
                jwt(true),
                "caller-token",
                "corr-" + variant,
                key,
                new SetupStockRequest(variant, initialOnHand, ReasonCode.OPENING_BALANCE, "opening", "ref")
        );
    }

    private CommandOutcome adjust(UUID stockId, int delta, ReasonCode reason, long version, String key) {
        return commands.adjust(
                ACTOR,
                "corr-adjust",
                stockId,
                key,
                new AdjustStockRequest(delta, reason, version, "note", "ref")
        );
    }

    private List<CommandOutcome> race(ThrowingSupplier left, ThrowingSupplier right) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<CommandOutcome> first = pool.submit(() -> {
                ready.countDown();
                start.await();
                return left.get();
            });
            Future<CommandOutcome> second = pool.submit(() -> {
                ready.countDown();
                start.await();
                return right.get();
            });
            ready.await();
            start.countDown();
            List<CommandOutcome> results = new ArrayList<>();
            results.add(first.get());
            results.add(second.get());
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID stockId(CommandOutcome outcome) throws Exception {
        return UUID.fromString(objectMapper.readTree(outcome.body()).path("stockItem").path("id").asText());
    }

    private int onHand(UUID stockId) {
        return jdbc.queryForObject("select on_hand from stock_item where id = ?", Integer.class, stockId);
    }

    private int historyCount(UUID stockId) {
        return jdbc.queryForObject(
                "select count(*) from stock_adjustment where stock_item_id = ?",
                Integer.class,
                stockId
        );
    }

    private int stockCount() {
        return jdbc.queryForObject("select count(*) from stock_item", Integer.class);
    }

    private int commandCount(String key) {
        return jdbc.queryForObject(
                "select count(*) from inventory_command where idempotency_key = ?",
                Integer.class,
                key
        );
    }

    private String commandStatus(String key) {
        return jdbc.queryForObject(
                "select command_status from inventory_command where idempotency_key = ?",
                String.class,
                key
        );
    }

    private int countVisibleToOtherConnection(String sql, Object argument) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                INVENTORY_USER,
                INVENTORY_PASSWORD
        )) {
            connection.setAutoCommit(true);
            connection.setSchema("inventory");
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, argument);
                try (ResultSet rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getInt(1);
                }
            }
        }
    }

    private static CatalogVariantSnapshot snapshot(UUID variant) {
        return new CatalogVariantSnapshot(
                variant,
                variant,
                "SKU-" + variant,
                "Variant",
                "Product",
                true,
                true,
                true
        );
    }

    private void ensureInsertListener() {
        if (!INSERT_LISTENER.compareAndSet(false, true)) {
            return;
        }
        SessionFactoryImplementor factory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        EventListenerRegistry registry = factory.getServiceRegistry().getService(EventListenerRegistry.class);
        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(event -> {
            if (watchInserts) {
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    sawInsertInsideTransaction = true;
                } else {
                    sawInsertOutsideTransaction = true;
                }
            }
            plantWinner(event.getEntity());
            plantStock(event.getEntity());
            return false;
        });
    }

    private static void resetInsertWatch() {
        sawInsertInsideTransaction = false;
        sawInsertOutsideTransaction = false;
    }

    private static void plantWinner(Object entity) {
        WinnerPlant plant = winnerPlant;
        if (plant == null || !(entity instanceof InventoryCommand command)) {
            return;
        }
        if (!plant.fingerprint().equals(command.getRequestFingerprint()) || !plant.planted().compareAndSet(false, true)) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                INVENTORY_USER,
                INVENTORY_PASSWORD
        )) {
            connection.setAutoCommit(true);
            connection.setSchema("inventory");
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into inventory_command (
                        id, actor_issuer, actor_subject, operation_scope, idempotency_key,
                        request_fingerprint, command_status, http_status, content_type,
                        response_body, location, created_at, completed_at
                    ) values (?, ?, ?, ?, ?, ?, 'COMPLETED', ?, 'application/json', ?, ?, now(), now())
                    """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, ACTOR.issuer());
                statement.setString(3, ACTOR.subject());
                statement.setString(4, plant.scope());
                statement.setString(5, plant.key());
                statement.setString(6, plant.fingerprint());
                statement.setInt(7, plant.httpStatus());
                statement.setString(8, plant.body());
                statement.setString(9, plant.location());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not plant the winning command", exception);
        }
    }

    private static void plantStock(Object entity) {
        StockPlant plant = stockPlant;
        if (plant == null || !(entity instanceof StockItem item)) {
            return;
        }
        if (!plant.variantId().equals(item.getCatalogVariantId()) || !plant.planted().compareAndSet(false, true)) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                INVENTORY_USER,
                INVENTORY_PASSWORD
        )) {
            connection.setAutoCommit(true);
            connection.setSchema("inventory");
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into stock_item (
                        id, catalog_variant_id, sku, on_hand, reserved,
                        product_name_snapshot, variant_name_snapshot,
                        created_at, updated_at, version
                    ) values (?, ?, ?, 1, 0, 'Planted', 'Planted', now(), now(), 0)
                    """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, plant.variantId());
                statement.setString(3, "P-" + plant.variantId());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not plant the conflicting stock row", exception);
        }
    }

    private record WinnerPlant(
            String fingerprint,
            String scope,
            String key,
            String body,
            String location,
            int httpStatus,
            AtomicBoolean planted
    ) {
    }

    private record StockPlant(UUID variantId, AtomicBoolean planted) {
    }

    private static void registerSku(UUID variant, String sku) {
        CatalogStub.SKUS.put(variant, sku);
    }

    private static UUID special(String tail) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + tail);
    }

    private static Jwt jwt(boolean catalogRead) {
        Map<String, Object> catalogRoles = catalogRead
                ? Map.of("roles", List.of("catalog.read"))
                : Map.of("roles", List.of("catalog.update"));
        return Jwt.withTokenValue("caller-token")
                .header("alg", "none")
                .subject(ACTOR.subject())
                .issuer(ACTOR.issuer())
                .audience(List.of("inventory-service", "catalog-service"))
                .claim("typ", "Bearer")
                .claim("resource_access", Map.of(
                        "catalog-service", catalogRoles,
                        "other-client", Map.of("roles", List.of("catalog.read"))
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static HttpServer startCatalog() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.createContext("/", exchange -> {
                CATALOG_HITS.incrementAndGet();
                CORRELATION.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
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
                        Thread.sleep(1200);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    body = "{}";
                } else if (path.contains("/variants/")) {
                    boolean active = !id.endsWith("000000000002");
                    String sku = CatalogStub.SKUS.getOrDefault(UUID.fromString(id), "SKU-" + id);
                    body = "{\"id\":\"" + id + "\",\"productId\":\"" + id
                            + "\",\"sku\":\"" + sku + "\",\"name\":\"Variant\",\"active\":" + active + "}";
                } else {
                    boolean productActive = !id.endsWith("000000000007");
                    boolean categoryActive = !id.endsWith("000000000008");
                    body = "{\"id\":\"" + id + "\",\"name\":\"Product\",\"active\":" + productActive
                            + ",\"category\":{\"name\":\"Category\",\"active\":" + categoryActive + "}}";
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

    private static void bootstrap() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + INVENTORY_USER + " LOGIN PASSWORD '" + INVENTORY_PASSWORD + "'");
            statement.execute("CREATE SCHEMA inventory AUTHORIZATION " + INVENTORY_USER);
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        CommandOutcome get() throws Exception;
    }

    private static final class CatalogStub {
        private static final java.util.concurrent.ConcurrentHashMap<UUID, String> SKUS = new java.util.concurrent.ConcurrentHashMap<>();
    }
}
