package com.umar.ecommerce.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umar.ecommerce.inventory.catalog.CatalogVariantSnapshot;
import com.umar.ecommerce.inventory.domain.ReasonCode;
import com.umar.ecommerce.inventory.domain.RequestFingerprint;
import com.umar.ecommerce.inventory.domain.ReservationDigest;
import com.umar.ecommerce.inventory.exception.InventoryProblem;
import com.umar.ecommerce.inventory.messaging.CheckoutCommand;
import com.umar.ecommerce.inventory.messaging.InventoryChannels;
import com.umar.ecommerce.inventory.messaging.OutboxClaimService;
import com.umar.ecommerce.inventory.service.Actor;
import com.umar.ecommerce.inventory.service.CommandOutcome;
import com.umar.ecommerce.inventory.service.ReservationExpiry;
import com.umar.ecommerce.inventory.service.ReservationTransactionService;
import com.umar.ecommerce.inventory.service.ReservationTransactionService.ReservationEffect;
import com.umar.ecommerce.inventory.service.StockTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "inventory.workers.enabled=false",
        "inventory.messaging.listener-enabled=false",
        "inventory.messaging.dispatch-enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Testcontainers
class InventoryReservationIT {

    private static final String INVENTORY_USER = "inventory_app";
    private static final String INVENTORY_PASSWORD = "test_" + UUID.randomUUID().toString().replace("-", "");
    private static final Actor ACTOR = new Actor("http://issuer.test", "subject-1");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.6-alpine")
    ).withDatabaseName("inventory_reservation_test");

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
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StockTransactionService stockTransactions;
    @Autowired
    private ReservationTransactionService reservations;
    @Autowired
    private ReservationExpiry expiry;
    @Autowired
    private OutboxClaimService outboxClaims;
    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void allLinesRollBackWhenOneSkuLacksStock() throws Exception {
        UUID inStock = UUID.randomUUID();
        UUID empty = UUID.randomUUID();
        setup(inStock, 5);
        setup(empty, 0);
        UUID orderId = UUID.randomUUID();

        ReservationEffect effect = reservations.apply(reserve(orderId, UUID.randomUUID(), UUID.randomUUID(), List.of(
                line(inStock, 1),
                line(empty, 1)
        )));

        assertThat(effect.eventType()).isEqualTo(InventoryChannels.STOCK_REJECTED);
        assertThat(reserved(inStock)).isZero();
        assertThat(reserved(empty)).isZero();
        assertThat(onHand(inStock)).isEqualTo(5);
        assertThat(count("select count(*) from reservation where order_id = ?", orderId)).isZero();
        assertThat(count("select count(*) from outbox_event where aggregate_id = ?", orderId)).isEqualTo(1);
    }

    @Test
    void competingReservesOnlyOneWins() throws Exception {
        UUID variant = UUID.randomUUID();
        setup(variant, 1);
        UUID firstOrder = UUID.randomUUID();
        UUID secondOrder = UUID.randomUUID();
        List<ReservationEffect> effects = race(
                () -> reservations.apply(reserve(firstOrder, UUID.randomUUID(), UUID.randomUUID(), List.of(line(variant, 1)))),
                () -> reservations.apply(reserve(secondOrder, UUID.randomUUID(), UUID.randomUUID(), List.of(line(variant, 1))))
        );

        assertThat(effects).extracting(ReservationEffect::eventType)
                .containsExactlyInAnyOrder(InventoryChannels.STOCK_RESERVED, InventoryChannels.STOCK_REJECTED);
        assertThat(reserved(variant)).isEqualTo(1);
        assertThat(onHand(variant)).isEqualTo(1);
        assertThat(count("select count(*) from reservation where state = 'ACTIVE' and order_id in (?, ?)", firstOrder, secondOrder))
                .isEqualTo(1);
    }

    @Test
    void reservedCeilingBlocksAdminAdjustment() throws Exception {
        UUID variant = UUID.randomUUID();
        UUID stockId = setup(variant, 5);
        UUID orderId = UUID.randomUUID();
        assertThat(reservations.apply(reserve(orderId, UUID.randomUUID(), UUID.randomUUID(), List.of(line(variant, 4))))
                .eventType()).isEqualTo(InventoryChannels.STOCK_RESERVED);
        long version = jdbc.queryForObject("select version from stock_item where id = ?", Long.class, stockId);

        CommandOutcome rejected = stockTransactions.writeAdjustment(new StockTransactionService.AdjustmentWrite(
                ACTOR,
                stockId,
                "below-reserved-" + variant,
                RequestFingerprint.adjustment(stockId, -2, ReasonCode.DAMAGE_LOSS, "note", "ref", version),
                "corr",
                -2,
                ReasonCode.DAMAGE_LOSS,
                version,
                "note",
                "ref"
        ));

        assertThat(rejected.status()).isEqualTo(409);
        assertThat(objectMapper.readTree(rejected.body()).path("code").asText())
                .isEqualTo(InventoryProblem.STOCK_INVARIANT);
        assertThat(onHand(variant)).isEqualTo(5);
        assertThat(reserved(variant)).isEqualTo(4);
    }

    @Test
    void duplicateCommandAndDifferentEventDoNotDoubleReserve() {
        UUID variant = UUID.randomUUID();
        setup(variant, 5);
        UUID orderId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        CheckoutCommand first = reserve(orderId, commandId, UUID.randomUUID(), List.of(line(variant, 2)));

        assertThat(reservations.apply(first).eventType()).isEqualTo(InventoryChannels.STOCK_RESERVED);
        UUID replayEvent = UUID.randomUUID();
        UUID conflictEvent = UUID.randomUUID();
        assertThat(reservations.apply(reserve(orderId, commandId, replayEvent, List.of(line(variant, 2)))).eventType())
                .isEqualTo(InventoryChannels.STOCK_RESERVED);
        assertThat(reservations.apply(reserve(orderId, commandId, conflictEvent, List.of(line(variant, 3)))).eventType())
                .isNull();

        assertThat(reserved(variant)).isEqualTo(2);
        assertThat(count("select count(*) from reservation_command where command_id = ?", commandId)).isEqualTo(1);
        assertThat(count("select count(*) from outbox_event where command_id = ?", commandId)).isEqualTo(1);
        assertThat(count(
                "select count(*) from inbox_event where event_id in (?, ?, ?)",
                first.eventId(),
                replayEvent,
                conflictEvent
        )).isEqualTo(3);
    }

    @Test
    void releaseBeforeReserveTombstoneThenLateReserveDoesNotIncreaseReserved() {
        UUID variant = UUID.randomUUID();
        setup(variant, 5);
        UUID earlyOrder = UUID.randomUUID();
        UUID releaseCommand = UUID.randomUUID();
        UUID reserveCommand = UUID.randomUUID();

        assertThat(reservations.apply(command(InventoryChannels.RELEASE_RESERVATION, earlyOrder, releaseCommand, UUID.randomUUID()))
                .eventType()).isEqualTo(InventoryChannels.STOCK_RELEASED);
        assertThat(reservations.apply(reserve(earlyOrder, reserveCommand, UUID.randomUUID(), List.of(line(variant, 2))))
                .eventType()).isEqualTo(InventoryChannels.STOCK_REJECTED);
        reservations.apply(command(InventoryChannels.RELEASE_RESERVATION, earlyOrder, releaseCommand, UUID.randomUUID()));
        reservations.apply(reserve(earlyOrder, reserveCommand, UUID.randomUUID(), List.of(line(variant, 2))));

        assertThat(reserved(variant)).isZero();
        assertThat(count("select count(*) from reservation_tombstone where order_id = ?", earlyOrder)).isEqualTo(1);
        assertThat(count("select count(*) from reservation where order_id = ?", earlyOrder)).isZero();
        assertThat(count("select count(*) from outbox_event where command_id = ?", releaseCommand)).isEqualTo(1);
        assertThat(count("select count(*) from outbox_event where command_id = ?", reserveCommand)).isEqualTo(1);

        UUID laterOrder = UUID.randomUUID();
        assertThat(reservations.apply(reserve(laterOrder, UUID.randomUUID(), UUID.randomUUID(), List.of(line(variant, 2))))
                .eventType()).isEqualTo(InventoryChannels.STOCK_RESERVED);
        assertThat(reservations.apply(command(InventoryChannels.RELEASE_RESERVATION, laterOrder, UUID.randomUUID(), UUID.randomUUID()))
                .eventType()).isEqualTo(InventoryChannels.STOCK_RELEASED);
        assertThat(reservations.apply(command(InventoryChannels.RELEASE_RESERVATION, laterOrder, UUID.randomUUID(), UUID.randomUUID()))
                .eventType()).isEqualTo(InventoryChannels.STOCK_RELEASED);
        assertThat(reserved(variant)).isZero();
        assertThat(state(laterOrder)).isEqualTo("RELEASED");
    }

    @Test
    void expiryAndHoldOnlyOneWins() throws Exception {
        UUID variant = UUID.randomUUID();
        setup(variant, 5);
        UUID orderId = UUID.randomUUID();
        UUID reserveCommand = UUID.randomUUID();
        reservations.apply(reserve(orderId, reserveCommand, UUID.randomUUID(), List.of(line(variant, 2))));
        jdbc.update("update reservation set expires_at = now() - interval '1 minute' where order_id = ?", orderId);
        UUID reservationId = jdbc.queryForObject("select id from reservation where order_id = ?", UUID.class, orderId);

        List<String> outcomes = race(
                () -> reservations.apply(command(InventoryChannels.HOLD_RESERVATION, orderId, UUID.randomUUID(), UUID.randomUUID())).eventType(),
                () -> reservations.expireIfDue(reservationId).map(ReservationEffect::eventType).orElse("IGNORED")
        );

        if ("CHECKOUT_HELD".equals(state(orderId))) {
            assertThat(reserved(variant)).isEqualTo(2);
            assertThat(outcomes).contains(InventoryChannels.RESERVATION_HELD, "IGNORED");
        } else {
            assertThat(state(orderId)).isEqualTo("RELEASED");
            assertThat(reserved(variant)).isZero();
            assertThat(outcomes).contains(InventoryChannels.RESERVATION_EXPIRED, InventoryChannels.HOLD_REJECTED);
            assertThat(jdbc.queryForObject(
                    "select command_id from outbox_event where event_type = ? and aggregate_id = ?",
                    UUID.class,
                    InventoryChannels.RESERVATION_EXPIRED,
                    orderId
            )).isEqualTo(reserveCommand);
        }
        boolean held = outcomes.contains(InventoryChannels.RESERVATION_HELD);
        boolean expired = outcomes.contains(InventoryChannels.RESERVATION_EXPIRED);
        assertThat(held ^ expired).isTrue();
    }

    @Test
    void checkoutHeldIsNotExpired() {
        UUID variant = UUID.randomUUID();
        setup(variant, 5);
        UUID orderId = UUID.randomUUID();
        reservations.apply(reserve(orderId, UUID.randomUUID(), UUID.randomUUID(), List.of(line(variant, 2))));
        assertThat(reservations.apply(command(InventoryChannels.HOLD_RESERVATION, orderId, UUID.randomUUID(), UUID.randomUUID()))
                .eventType()).isEqualTo(InventoryChannels.RESERVATION_HELD);
        UUID reservationId = jdbc.queryForObject("select id from reservation where order_id = ?", UUID.class, orderId);
        jdbc.update("update reservation set expires_at = now() - interval '5 minutes' where order_id = ?", orderId);

        expiry.expireDue();
        assertThat(reservations.expireIfDue(reservationId)).isEmpty();

        assertThat(state(orderId)).isEqualTo("CHECKOUT_HELD");
        assertThat(reserved(variant)).isEqualTo(2);
        assertThat(count(
                "select count(*) from outbox_event where aggregate_id = ? and event_type = ?",
                orderId,
                InventoryChannels.RESERVATION_EXPIRED
        )).isZero();
    }

    @Test
    void consumeDecreasesOnHandAndReservedOnce() {
        UUID variant = UUID.randomUUID();
        setup(variant, 5);
        UUID orderId = UUID.randomUUID();
        reservations.apply(reserve(orderId, UUID.randomUUID(), UUID.randomUUID(), List.of(line(variant, 2))));
        reservations.apply(command(InventoryChannels.HOLD_RESERVATION, orderId, UUID.randomUUID(), UUID.randomUUID()));
        UUID consumeCommand = UUID.randomUUID();

        assertThat(reservations.apply(command(InventoryChannels.CONSUME_RESERVATION, orderId, consumeCommand, UUID.randomUUID()))
                .eventType()).isEqualTo(InventoryChannels.STOCK_CONSUMED);
        assertThat(reservations.apply(command(InventoryChannels.CONSUME_RESERVATION, orderId, UUID.randomUUID(), UUID.randomUUID()))
                .eventType()).isEqualTo(InventoryChannels.CONSUME_REJECTED);
        reservations.apply(command(InventoryChannels.CONSUME_RESERVATION, orderId, consumeCommand, UUID.randomUUID()));

        assertThat(onHand(variant)).isEqualTo(3);
        assertThat(reserved(variant)).isZero();
        assertThat(state(orderId)).isEqualTo("CONSUMED");
        assertThat(count("select count(*) from stock_movement where command_id = ? and movement_type = 'CONSUME'", consumeCommand))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("update stock_movement set quantity = 1 where command_id = ?", consumeCommand))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("stock_movement is immutable");
    }

    @Test
    void restockIncreasesOnHandOnce() {
        UUID variant = UUID.randomUUID();
        setup(variant, 5);
        UUID orderId = UUID.randomUUID();
        reservations.apply(reserve(orderId, UUID.randomUUID(), UUID.randomUUID(), List.of(line(variant, 2))));
        reservations.apply(command(InventoryChannels.HOLD_RESERVATION, orderId, UUID.randomUUID(), UUID.randomUUID()));
        reservations.apply(command(InventoryChannels.CONSUME_RESERVATION, orderId, UUID.randomUUID(), UUID.randomUUID()));
        UUID restockCommand = UUID.randomUUID();

        assertThat(reservations.apply(command(InventoryChannels.RESTOCK_RESERVATION, orderId, restockCommand, UUID.randomUUID()))
                .eventType()).isEqualTo(InventoryChannels.STOCK_RESTOCKED);
        assertThat(reservations.apply(command(InventoryChannels.RESTOCK_RESERVATION, orderId, UUID.randomUUID(), UUID.randomUUID()))
                .eventType()).isEqualTo(InventoryChannels.RESTOCK_REJECTED);
        reservations.apply(command(InventoryChannels.RESTOCK_RESERVATION, orderId, restockCommand, UUID.randomUUID()));

        assertThat(onHand(variant)).isEqualTo(5);
        assertThat(reserved(variant)).isZero();
        assertThat(state(orderId)).isEqualTo("RESTOCKED");
        assertThat(count("select count(*) from stock_movement where command_id = ? and movement_type = 'RESTOCK'", restockCommand))
                .isEqualTo(1);
    }

    @Test
    void sentOutboxRowIsNotReclaimed() {
        UUID sentId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        insertOutbox(sentId, "SENT");
        insertOutbox(pendingId, "PENDING");

        List<OutboxClaimService.ClaimedOutbox> claimed = outboxClaims.claimDue(Instant.now(), Duration.ofSeconds(30), 20);

        assertThat(claimed).extracting(OutboxClaimService.ClaimedOutbox::id).contains(pendingId).doesNotContain(sentId);
        assertThat(status(sentId)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select claim_token from outbox_event where id = ?", UUID.class, sentId)).isNull();
        assertThat(outboxClaims.claimDue(Instant.now(), Duration.ofSeconds(30), 20))
                .extracting(OutboxClaimService.ClaimedOutbox::id)
                .doesNotContain(sentId);
        assertThat(status(sentId)).isEqualTo("SENT");
    }

    private UUID setup(UUID variant, int onHand) {
        CommandOutcome created = stockTransactions.writeSetup(new StockTransactionService.SetupWrite(
                ACTOR,
                "setup-" + variant + "-" + onHand,
                RequestFingerprint.setup(variant, onHand, ReasonCode.OPENING_BALANCE, "opening", "ref"),
                "corr-" + variant,
                new CatalogVariantSnapshot(variant, variant, "SKU-" + variant, "Variant", "Product", true, true, true),
                onHand,
                "opening",
                "ref"
        ));
        assertThat(created.status()).isEqualTo(201);
        try {
            return UUID.fromString(objectMapper.readTree(created.body()).path("stockItem").path("id").asText());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ReservationDigest.Line line(UUID variant, int quantity) {
        return new ReservationDigest.Line(variant, "SKU-" + variant, quantity);
    }

    private static CheckoutCommand reserve(UUID orderId, UUID commandId, UUID eventId, List<ReservationDigest.Line> lines) {
        return new CheckoutCommand(
                eventId,
                InventoryChannels.RESERVE_STOCK,
                orderId,
                1,
                UUID.randomUUID(),
                commandId,
                "corr-" + orderId,
                null,
                Instant.now(),
                lines
        );
    }

    private static CheckoutCommand command(String eventType, UUID orderId, UUID commandId, UUID eventId) {
        return new CheckoutCommand(
                eventId,
                eventType,
                orderId,
                1,
                UUID.randomUUID(),
                commandId,
                "corr-" + orderId,
                null,
                Instant.now(),
                List.of()
        );
    }

    private int reserved(UUID variant) {
        return jdbc.queryForObject(
                "select reserved from stock_item where catalog_variant_id = ?",
                Integer.class,
                variant
        );
    }

    private int onHand(UUID variant) {
        return jdbc.queryForObject(
                "select on_hand from stock_item where catalog_variant_id = ?",
                Integer.class,
                variant
        );
    }

    private String state(UUID orderId) {
        return jdbc.queryForObject("select state from reservation where order_id = ?", String.class, orderId);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String status(UUID outboxId) {
        return jdbc.queryForObject("select status from outbox_event where id = ?", String.class, outboxId);
    }

    private void insertOutbox(UUID id, String status) {
        jdbc.update("""
                insert into outbox_event (
                    id, event_id, event_type, event_version, aggregate_id, aggregate_version,
                    correlation_id, topic, message_key, payload, envelope, status,
                    next_attempt_at, attempts, created_at, sent_at
                ) values (
                    ?, ?, 'StockReserved', 1, ?, 0,
                    'corr', 'checkout.inventory.outcomes', ?, '{}', '{}', ?,
                    now() - interval '1 minute', 1, now(), case when ? = 'SENT' then now() else null end
                )
                """, id, UUID.randomUUID(), UUID.randomUUID(), id.toString(), status, status);
    }

    private <T> List<T> race(ThrowingSupplier<T> left, ThrowingSupplier<T> right) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> first = pool.submit(() -> {
                ready.countDown();
                start.await();
                return left.get();
            });
            Future<T> second = pool.submit(() -> {
                ready.countDown();
                start.await();
                return right.get();
            });
            ready.await();
            start.countDown();
            List<T> results = new ArrayList<>();
            results.add(first.get());
            results.add(second.get());
            return results;
        } finally {
            pool.shutdownNow();
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
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
