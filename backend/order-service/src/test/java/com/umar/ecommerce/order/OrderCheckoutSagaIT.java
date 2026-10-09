package com.umar.ecommerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.umar.ecommerce.order.domain.MoneyPolicy;
import com.umar.ecommerce.order.entity.CheckoutRequest;
import com.umar.ecommerce.order.entity.CustomerQuote;
import com.umar.ecommerce.order.entity.QuoteLine;
import com.umar.ecommerce.order.exception.OrderProblem;
import com.umar.ecommerce.order.messaging.CheckoutEvents;
import com.umar.ecommerce.order.messaging.CheckoutTopics;
import com.umar.ecommerce.order.messaging.OutboxClaim;
import com.umar.ecommerce.order.messaging.OutboxClaimService;
import com.umar.ecommerce.order.messaging.OutboxDispatcher;
import com.umar.ecommerce.order.messaging.OutboxMarkService;
import com.umar.ecommerce.order.messaging.OutboxPublisher;
import com.umar.ecommerce.order.messaging.OutcomeApplyService;
import com.umar.ecommerce.order.remote.CheckoutRemote;
import com.umar.ecommerce.order.remote.CheckoutSnapshot;
import com.umar.ecommerce.order.repository.OutboxRepository;
import com.umar.ecommerce.order.repository.QuoteRepository;
import com.umar.ecommerce.order.service.CheckoutResult;
import com.umar.ecommerce.order.service.CheckoutTransactionService;
import com.umar.ecommerce.order.service.QuoteCheckoutService;
import com.umar.ecommerce.order.service.SagaAdvanceService;
import com.umar.ecommerce.order.service.SagaRecovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "checkout.outbox.poll-delay=1h",
        "checkout.saga.poll-delay=1h",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.bootstrap-servers=127.0.0.1:1",
        "management.health.kafka.enabled=false"
})
@Testcontainers
class OrderCheckoutSagaIT {

    private static final String ORDER_USER = "order_app";
    private static final String ORDER_PASSWORD = "test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String ISSUER = "http://issuer.test";
    private static final String SUBJECT = "ada";
    private static final UUID VARIANT = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ADDRESS = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final BigDecimal PRICE = new BigDecimal("25.00");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.6-alpine")
    ).withDatabaseName("order_test");

    static {
        POSTGRES.start();
        bootstrap();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> ORDER_USER);
        registry.add("spring.datasource.password", () -> ORDER_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> "order");
        registry.add("spring.flyway.default-schema", () -> "order");
        registry.add("spring.flyway.schemas", () -> "order");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "\"order\"");
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private QuoteRepository quotes;
    @Autowired
    private QuoteCheckoutService checkout;
    @Autowired
    private CheckoutTransactionService transactions;
    @Autowired
    private SagaAdvanceService saga;
    @Autowired
    private SagaRecovery recovery;
    @Autowired
    private OutcomeApplyService outcomes;
    @Autowired
    private OutboxClaimService claims;
    @Autowired
    private OutboxMarkService marks;
    @Autowired
    private OutboxPublisher publisher;
    @MockitoSpyBean
    private OutboxRepository outboxRows;
    @MockitoBean
    private CheckoutRemote remote;
    @MockitoBean
    private KafkaTemplate<String, String> kafka;
    @MockitoBean
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void clean() {
        org.mockito.Mockito.reset(outboxRows, remote, kafka);
        jdbc.update("DELETE FROM inbox_event");
        jdbc.update("DELETE FROM dead_letter");
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM order_history");
        jdbc.update("DELETE FROM order_line");
        jdbc.update("DELETE FROM order_address");
        jdbc.update("DELETE FROM checkout_request");
        jdbc.update("DELETE FROM customer_order");
        jdbc.update("DELETE FROM quote_line");
        jdbc.update("DELETE FROM customer_quote");
        when(remote.cart(anyString(), anyString())).thenReturn(cart());
        when(remote.catalog(any(), anyString())).thenReturn(List.of(catalogLine()));
        when(remote.address(anyString(), any(), anyString())).thenReturn(address());
    }

    @Test
    void sameCompletedKeyReplaysStoredAcceptanceAfterQuoteExpiry() {
        CustomerQuote quote = saveQuote();
        CheckoutResult first = accept(quote, "replay-key-1");

        jdbc.update("UPDATE customer_quote SET expires_at = now() - interval '1 hour' WHERE id = ?", quote.getId());
        CheckoutResult replay = accept(quote, "replay-key-1");

        assertThat(replay.status()).isEqualTo(202);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(replay.location()).isEqualTo(first.location());
        assertThat(count("SELECT COUNT(*) FROM customer_order")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE event_type = ?", CheckoutEvents.RESERVE_STOCK)).isEqualTo(1);
        verify(remote, times(1)).cart(anyString(), anyString());
        verify(remote, times(1)).catalog(any(), anyString());
        verify(remote, times(1)).address(anyString(), any(), anyString());
    }

    @Test
    void changedQuoteForTheSameKeyConflictsAndOwnerKeyStaysUnique() {
        CustomerQuote quote = saveQuote();
        CheckoutResult accepted = accept(quote, "same-owner-key");
        jdbc.update("UPDATE customer_quote SET expires_at = now() - interval '1 hour' WHERE id = ?", quote.getId());

        assertThatThrownBy(() -> accept(saveQuote(), "same-owner-key"))
                .isInstanceOfSatisfying(OrderProblem.class, problem -> {
                    assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(problem.code()).isEqualTo(OrderProblem.IDEMPOTENCY_CONFLICT);
                });
        assertThatThrownBy(() -> transactions.insertClaim(ISSUER, SUBJECT, "same-owner-key", fingerprint(quote.getId())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(error -> assertThat(((DataIntegrityViolationException) error).getMostSpecificCause().getMessage())
                        .contains("uq_checkout_request_owner_key"));

        assertThat(count("SELECT COUNT(*) FROM customer_order")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM checkout_request")).isEqualTo(1);
        assertThat(accept(quote, "same-owner-key").body()).isEqualTo(accepted.body());
        verify(remote, times(1)).cart(anyString(), anyString());
    }

    @Test
    void inProgressClaimCompletesOnceAndThenReplays() {
        CustomerQuote quote = saveQuote();
        CheckoutRequest claim = transactions.insertClaim(ISSUER, SUBJECT, "inflight-key", fingerprint(quote.getId()));

        assertThatThrownBy(() -> accept(quote, "inflight-key"))
                .isInstanceOfSatisfying(OrderProblem.class, problem -> {
                    assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(problem.code()).isEqualTo(OrderProblem.IDEMPOTENCY_IN_PROGRESS);
                });
        assertThat(count("SELECT COUNT(*) FROM customer_order")).isZero();

        CheckoutResult completed = transactions.accept(claim.getId(), quote.getId(), ISSUER, SUBJECT, quote.getCartVersion());
        CheckoutResult replay = accept(quote, "inflight-key");

        assertThat(completed.status()).isEqualTo(202);
        assertThat(replay.status()).isEqualTo(202);
        assertThat(replay.body()).isEqualTo(completed.body());
        assertThat(count("SELECT COUNT(*) FROM customer_order")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE event_type = ?", CheckoutEvents.RESERVE_STOCK)).isEqualTo(1);
        verify(remote, times(1)).cart(anyString(), anyString());
    }

    @Test
    void concurrentSameKeyCommitsOneOrder() throws Exception {
        CustomerQuote quote = saveQuote();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(remote.cart(anyString(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("checkout barrier timed out");
            }
            return cart();
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<CheckoutResult> first = pool.submit(() -> accept(quote, "concurrent-key"));
            Future<CheckoutResult> second = pool.submit(() -> accept(quote, "concurrent-key"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<CheckoutResult> future : List.of(first, second)) {
                try {
                    outcomes.add(future.get(10, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    outcomes.add(exception.getCause());
                }
            }
            List<CheckoutResult> accepted = outcomes.stream()
                    .filter(CheckoutResult.class::isInstance)
                    .map(CheckoutResult.class::cast)
                    .toList();
            List<OrderProblem> problems = outcomes.stream()
                    .filter(OrderProblem.class::isInstance)
                    .map(OrderProblem.class::cast)
                    .toList();
            assertThat(problems).allSatisfy(problem -> assertThat(problem.code()).isEqualTo(OrderProblem.IDEMPOTENCY_IN_PROGRESS));
            assertThat(accepted).isNotEmpty().allSatisfy(result -> {
                assertThat(result.status()).isEqualTo(202);
                assertThat(result.location()).isEqualTo(accepted.get(0).location());
                assertThat(result.body()).isEqualTo(accepted.get(0).body());
            });
            assertThat(count("SELECT COUNT(*) FROM customer_order")).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE event_type = ?", CheckoutEvents.RESERVE_STOCK)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void acceptedOrderAndReserveStockCommitOrRollBackTogether() {
        CustomerQuote quote = saveQuote();
        CheckoutResult accepted = accept(quote, "atomic-key-1");
        UUID orderId = orderId(accepted);

        OrderRow order = order(orderId);
        assertThat(order.orderStatus()).isEqualTo("PENDING_STOCK");
        assertThat(order.paymentStatus()).isEqualTo("NOT_STARTED");
        assertThat(order.fulfilmentStatus()).isEqualTo("NOT_STARTED");
        assertThat(order.sagaStep()).isEqualTo("AWAIT_RESERVATION");
        assertThat(order.stockConsumed()).isFalse();
        assertThat(jdbc.queryForObject("SELECT consumed_order_id FROM customer_quote WHERE id = ?", UUID.class, quote.getId()))
                .isEqualTo(orderId);
        assertThat(count("SELECT COUNT(*) FROM order_line WHERE order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_address WHERE order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_history WHERE order_id = ?", orderId)).isEqualTo(1);
        UUID reserveCommandId = command(orderId, "reserve_command_id");
        assertThat(count("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = ? AND status = 'PENDING' AND command_id = ? AND topic = ? AND message_key = ?
                """, CheckoutEvents.RESERVE_STOCK, reserveCommandId, CheckoutTopics.INVENTORY_COMMANDS, orderId.toString()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload FROM outbox_event WHERE command_id = ?", String.class, reserveCommandId))
                .contains("HEADPHONES-BLK");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE event_type <> ?", CheckoutEvents.RESERVE_STOCK)).isZero();

        doThrow(new IllegalStateException("outbox down")).when(outboxRows).save(any());
        CustomerQuote rolledBack = saveQuote();
        assertThatThrownBy(() -> accept(rolledBack, "atomic-key-2")).isInstanceOf(IllegalStateException.class);

        assertThat(count("SELECT COUNT(*) FROM customer_order")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM checkout_request")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT consumed_order_id FROM customer_quote WHERE id = ?", UUID.class, rolledBack.getId()))
                .containsOnlyNulls();
    }

    @Test
    void oneQuoteCannotCreateASecondOrder() {
        CustomerQuote quote = saveQuote();
        accept(quote, "quote-once-key");

        assertThatThrownBy(() -> accept(quote, "quote-other-key"))
                .isInstanceOfSatisfying(OrderProblem.class, problem -> {
                    assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(problem.code()).isEqualTo(OrderProblem.REVIEW_REQUIRED);
                    assertThat(problem.reason()).isEqualTo("QUOTE_CONSUMED");
                });
        assertThat(count("SELECT COUNT(*) FROM checkout_request WHERE idempotency_key = ?", "quote-other-key")).isZero();

        CheckoutRequest second = transactions.insertClaim(ISSUER, SUBJECT, "quote-stored-key", fingerprint(quote.getId()));
        CheckoutResult stored = transactions.accept(second.getId(), quote.getId(), ISSUER, SUBJECT, quote.getCartVersion());
        CheckoutResult replay = accept(quote, "quote-stored-key");

        assertThat(stored.status()).isEqualTo(409);
        assertThat(stored.body()).contains("QUOTE_CONSUMED");
        assertThat(replay.status()).isEqualTo(stored.status());
        assertThat(replay.body()).isEqualTo(stored.body());
        assertThat(count("SELECT COUNT(*) FROM customer_order")).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customer_order (
                    id, owner_issuer, owner_subject, quote_id, cart_version, order_status, payment_status,
                    fulfilment_status, saga_step, terminal_plan, cancellation_requested, stock_consumed,
                    cleanup_status, obligation, currency, merchandise_total, shipping_total, tax_total,
                    grand_total, attempts, reserve_command_id, version, created_at, updated_at
                )
                SELECT ?, owner_issuer, owner_subject, quote_id, cart_version, order_status, payment_status,
                    fulfilment_status, saga_step, terminal_plan, cancellation_requested, stock_consumed,
                    cleanup_status, obligation, currency, merchandise_total, shipping_total, tax_total,
                    grand_total, attempts, ?, 0, created_at, updated_at
                FROM customer_order
                """, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(error -> assertThat(((DataIntegrityViolationException) error).getMostSpecificCause().getMessage())
                        .contains("uq_customer_order_quote"));
    }

    @Test
    void ownerReadsAndCancelMapLinesAndAddressOutsideTheSagaTransaction() {
        UUID orderId = acceptedOrder("read-map-key");

        var order = checkout.get(ISSUER, SUBJECT, orderId);
        assertThat(order.lines()).hasSize(1);
        assertThat(order.address()).isNotNull();

        var page = checkout.list(ISSUER, SUBJECT, 0, 20);
        assertThat(page.items()).extracting(item -> item.id()).contains(orderId);
        assertThat(page.items()).allSatisfy(item -> assertThat(item.lines()).isNotEmpty());

        var cancelled = checkout.cancel(ISSUER, SUBJECT, orderId);
        assertThat(cancelled.cancellationRequested()).isTrue();
        assertThat(cancelled.lines()).hasSize(1);
        assertThat(cancelled.address()).isNotNull();

        assertThatThrownBy(() -> checkout.get(ISSUER, "grace", orderId))
                .isInstanceOfSatisfying(OrderProblem.class,
                        problem -> assertThat(problem.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void confirmsOnlyAfterPaymentSuccessAndStockConsumed() {
        UUID rejected = acceptedOrder("reject-stock-key");
        apply(rejected, command(rejected, "reserve_command_id"), CheckoutEvents.STOCK_REJECTED);
        OrderRow rejection = order(rejected);
        assertThat(rejection.orderStatus()).isEqualTo("REJECTED");
        assertThat(rejection.paymentStatus()).isEqualTo("NOT_STARTED");
        assertThat(rejection.sagaStep()).isEqualTo("COMPLETED");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", rejected, CheckoutEvents.REQUEST_PAYMENT))
                .isZero();

        UUID orderId = payReady("confirm-key");
        assertThat(order(orderId).orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(order(orderId).paymentStatus()).isEqualTo("REQUESTED");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", orderId, CheckoutEvents.REQUEST_PAYMENT))
                .isEqualTo(1);

        apply(orderId, command(orderId, "payment_command_id"), CheckoutEvents.PAYMENT_SUCCEEDED);
        OrderRow paid = order(orderId);
        assertThat(paid.orderStatus()).isEqualTo("PENDING_CONSUMPTION");
        assertThat(paid.paymentStatus()).isEqualTo("SUCCEEDED");
        assertThat(paid.stockConsumed()).isFalse();
        assertThat(paid.sagaStep()).isEqualTo("AWAIT_CONSUMPTION");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", orderId, CheckoutEvents.CONSUME_RESERVATION))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", orderId, CheckoutEvents.CLEAR_CART))
                .isZero();

        apply(orderId, command(orderId, "consume_command_id"), CheckoutEvents.STOCK_CONSUMED);
        OrderRow confirmed = order(orderId);
        assertThat(confirmed.orderStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.stockConsumed()).isTrue();
        assertThat(confirmed.cleanupStatus()).isEqualTo("REQUESTED");
        assertThat(confirmed.sagaStep()).isEqualTo("AWAIT_CLEANUP");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", orderId, CheckoutEvents.CLEAR_CART))
                .isEqualTo(1);

        apply(orderId, command(orderId, "cleanup_command_id"), CheckoutEvents.CART_CLEANUP_SKIPPED);
        OrderRow finished = order(orderId);
        assertThat(finished.orderStatus()).isEqualTo("CONFIRMED");
        assertThat(finished.sagaStep()).isEqualTo("COMPLETED");
        assertThat(finished.cleanupStatus()).isEqualTo("SKIPPED");
    }

    @Test
    void duplicateAndOutOfOrderOutcomesDoNotRepeatTransitions() {
        UUID early = acceptedOrder("early-outcome-key");
        UUID prematurePayment = UUID.randomUUID();
        apply(early, UUID.randomUUID(), CheckoutEvents.PAYMENT_SUCCEEDED, prematurePayment);
        apply(early, null, CheckoutEvents.STOCK_CONSUMED, UUID.randomUUID());
        assertThat(order(early).orderStatus()).isEqualTo("PENDING_STOCK");
        assertThat(order(early).paymentStatus()).isEqualTo("NOT_STARTED");
        assertThat(count("SELECT COUNT(*) FROM inbox_event")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM order_history WHERE order_id = ?", early)).isEqualTo(1);

        UUID reserved = UUID.randomUUID();
        int outboxBefore = count("SELECT COUNT(*) FROM outbox_event");
        apply(early, command(early, "reserve_command_id"), CheckoutEvents.STOCK_RESERVED, reserved);
        apply(early, command(early, "reserve_command_id"), CheckoutEvents.STOCK_RESERVED, reserved);
        apply(early, command(early, "reserve_command_id"), CheckoutEvents.STOCK_RESERVED, UUID.randomUUID());
        assertThat(order(early).sagaStep()).isEqualTo("AWAIT_HOLD");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", early, CheckoutEvents.HOLD_RESERVATION))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event")).isEqualTo(outboxBefore + 1);
        assertThat(count("SELECT COUNT(*) FROM inbox_event WHERE event_id = ?", reserved)).isEqualTo(1);

        apply(early, command(early, "hold_command_id"), CheckoutEvents.RESERVATION_HELD);
        apply(early, command(early, "reserve_command_id"), CheckoutEvents.RESERVATION_HELD, UUID.randomUUID());
        apply(early, prematurePayment, CheckoutEvents.PAYMENT_SUCCEEDED, prematurePayment);
        apply(early, command(early, "payment_command_id"), CheckoutEvents.PAYMENT_SUCCEEDED);
        assertThat(order(early).orderStatus()).isEqualTo("PENDING_CONSUMPTION");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", early, CheckoutEvents.REQUEST_PAYMENT))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", early, CheckoutEvents.CONSUME_RESERVATION))
                .isEqualTo(1);

        UUID consumed = UUID.randomUUID();
        apply(early, command(early, "consume_command_id"), CheckoutEvents.STOCK_CONSUMED, consumed);
        apply(early, command(early, "consume_command_id"), CheckoutEvents.STOCK_CONSUMED, consumed);
        apply(early, command(early, "consume_command_id"), CheckoutEvents.STOCK_CONSUMED, UUID.randomUUID());
        assertThat(order(early).orderStatus()).isEqualTo("CONFIRMED");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", early, CheckoutEvents.CLEAR_CART))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_history WHERE order_id = ? AND order_status = 'CONFIRMED'", early)).isEqualTo(1);
    }

    @Test
    void paymentDeadlineBecomesUnknownUntilReconcile() {
        UUID orderId = payReady("deadline-key");
        jdbc.update("UPDATE customer_order SET deadline_at = now() - interval '1 minute' WHERE id = ?", orderId);

        recovery.recover();
        OrderRow unknown = order(orderId);
        assertThat(unknown.orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(unknown.paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(unknown.sagaStep()).isEqualTo("AWAIT_RECONCILE");
        assertThat(unknown.attempts()).isZero();
        assertThat(unknown.obligation()).contains("payment-protected");
        UUID paymentCommandId = command(orderId, "payment_command_id");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ? AND command_id = ?",
                orderId, CheckoutEvents.QUERY_PAYMENT, paymentCommandId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?",
                orderId, CheckoutEvents.REQUEST_PAYMENT)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?",
                orderId, CheckoutEvents.RELEASE_RESERVATION)).isZero();

        recovery.recover();
        assertThat(order(orderId).attempts()).isZero();
        assertThat(order(orderId).paymentStatus()).isEqualTo("UNKNOWN");

        jdbc.update("UPDATE customer_order SET deadline_at = now() - interval '1 minute' WHERE id = ?", orderId);
        recovery.recover();
        assertThat(order(orderId).attempts()).isEqualTo(1);
        assertThat(order(orderId).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(order(orderId).sagaStep()).isEqualTo("AWAIT_RECONCILE");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ? AND command_id = ?",
                orderId, CheckoutEvents.QUERY_PAYMENT, paymentCommandId)).isEqualTo(2);

        apply(orderId, paymentCommandId, CheckoutEvents.PAYMENT_SUCCEEDED);
        assertThat(order(orderId).orderStatus()).isEqualTo("PENDING_CONSUMPTION");
        assertThat(order(orderId).paymentStatus()).isEqualTo("SUCCEEDED");
        apply(orderId, command(orderId, "consume_command_id"), CheckoutEvents.STOCK_CONSUMED);
        assertThat(order(orderId).orderStatus()).isEqualTo("CONFIRMED");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?",
                orderId, CheckoutEvents.REQUEST_PAYMENT)).isEqualTo(1);
    }

    @Test
    void cancellationAndLateSuccessCompensateBeforeTerminal() {
        UUID paying = payReady("cancel-paying-key");
        assertThatThrownBy(() -> saga.cancel(ISSUER, "grace", paying))
                .isInstanceOfSatisfying(OrderProblem.class, problem -> {
                    assertThat(problem.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(problem.code()).isEqualTo(OrderProblem.NOT_FOUND);
                });
        assertThat(order(paying).cancellationRequested()).isFalse();

        saga.cancel(ISSUER, SUBJECT, paying);
        OrderRow cancelling = order(paying);
        assertThat(cancelling.orderStatus()).isEqualTo("CANCEL_PENDING");
        assertThat(cancelling.sagaStep()).isEqualTo("AWAIT_PAYMENT");
        assertThat(cancelling.cancellationRequested()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", paying, CheckoutEvents.REFUND_PAYMENT))
                .isZero();

        UUID paymentCommandId = command(paying, "payment_command_id");
        UUID latePayment = UUID.randomUUID();
        apply(paying, paymentCommandId, CheckoutEvents.PAYMENT_SUCCEEDED, latePayment);
        apply(paying, paymentCommandId, CheckoutEvents.PAYMENT_SUCCEEDED, latePayment);
        apply(paying, paymentCommandId, CheckoutEvents.PAYMENT_SUCCEEDED, UUID.randomUUID());
        OrderRow refunding = order(paying);
        assertThat(refunding.orderStatus()).isEqualTo("CANCEL_PENDING");
        assertThat(refunding.paymentStatus()).isEqualTo("REFUND_REQUESTED");
        assertThat(refunding.sagaStep()).isEqualTo("AWAIT_REFUND");
        assertThat(refunding.orderStatus()).isNotEqualTo("CONFIRMED");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ? AND command_id = ?",
                paying, CheckoutEvents.REFUND_PAYMENT, command(paying, "refund_command_id"))).isEqualTo(1);

        apply(paying, command(paying, "refund_command_id"), CheckoutEvents.PAYMENT_REFUNDED);
        assertThat(order(paying).sagaStep()).isEqualTo("AWAIT_RELEASE");
        assertThat(order(paying).paymentStatus()).isEqualTo("REFUNDED");
        assertThat(order(paying).orderStatus()).isEqualTo("CANCEL_PENDING");
        apply(paying, command(paying, "release_command_id"), CheckoutEvents.STOCK_RELEASED);
        apply(paying, command(paying, "release_command_id"), CheckoutEvents.STOCK_RELEASED, UUID.randomUUID());
        assertThat(order(paying).orderStatus()).isEqualTo("CANCELLED");
        assertThat(order(paying).sagaStep()).isEqualTo("COMPLETED");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", paying, CheckoutEvents.RELEASE_RESERVATION))
                .isEqualTo(1);

        UUID declined = payReady("late-after-decline-key");
        apply(declined, command(declined, "payment_command_id"), CheckoutEvents.PAYMENT_DECLINED);
        assertThat(order(declined).paymentStatus()).isEqualTo("DECLINED");
        assertThat(order(declined).sagaStep()).isEqualTo("AWAIT_RELEASE");
        apply(declined, command(declined, "release_command_id"), CheckoutEvents.STOCK_RELEASED);
        assertThat(order(declined).orderStatus()).isEqualTo("REJECTED");
        apply(declined, command(declined, "payment_command_id"), CheckoutEvents.PAYMENT_SUCCEEDED);
        apply(declined, command(declined, "payment_command_id"), CheckoutEvents.PAYMENT_SUCCEEDED, UUID.randomUUID());
        assertThat(order(declined).orderStatus()).isEqualTo("REJECTED");
        assertThat(order(declined).paymentStatus()).isEqualTo("REFUND_REQUESTED");
        assertThat(order(declined).sagaStep()).isEqualTo("AWAIT_REFUND");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", declined, CheckoutEvents.REFUND_PAYMENT))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", declined, CheckoutEvents.REQUEST_PAYMENT))
                .isEqualTo(1);

        UUID confirmed = confirmedOrder("cancel-confirmed-key");
        saga.cancel(ISSUER, SUBJECT, confirmed);
        assertThat(order(confirmed).orderStatus()).isEqualTo("CANCEL_PENDING");
        assertThat(order(confirmed).sagaStep()).isEqualTo("AWAIT_REFUND");
        apply(confirmed, command(confirmed, "refund_command_id"), CheckoutEvents.PAYMENT_REFUNDED);
        assertThat(order(confirmed).orderStatus()).isEqualTo("CANCEL_PENDING");
        assertThat(order(confirmed).sagaStep()).isEqualTo("AWAIT_RESTOCK");
        apply(confirmed, command(confirmed, "restock_command_id"), CheckoutEvents.STOCK_RESTOCKED);
        apply(confirmed, command(confirmed, "restock_command_id"), CheckoutEvents.STOCK_RESTOCKED, UUID.randomUUID());
        assertThat(order(confirmed).orderStatus()).isEqualTo("CANCELLED");
        assertThat(order(confirmed).paymentStatus()).isEqualTo("REFUNDED");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", confirmed, CheckoutEvents.RESTOCK_RESERVATION))
                .isEqualTo(1);
    }

    @Test
    void sentOutboxIsExcludedAndExpiredLeaseIsFenced() {
        UUID orderId = acceptedOrder("lease-key");
        UUID olderId = jdbc.queryForObject(
                "SELECT id FROM outbox_event WHERE aggregate_id = ? AND event_type = ?",
                UUID.class, orderId, CheckoutEvents.RESERVE_STOCK);
        UUID newerId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_event (
                    id, event_id, event_type, event_version, aggregate_id, aggregate_version,
                    saga_id, command_id, correlation_id, topic, message_key, payload, envelope,
                    status, next_attempt_at, attempts, created_at
                ) VALUES (
                    ?, ?, 'HoldReservation', 1, ?, 2, ?, ?, ?, ?, ?, '{}', '{}',
                    'PENDING', now() - interval '1 minute', 0, now() + interval '1 second'
                )
                """, newerId, UUID.randomUUID(), orderId, orderId, UUID.randomUUID(), orderId.toString(),
                CheckoutTopics.INVENTORY_COMMANDS, orderId.toString());

        jdbc.update("UPDATE outbox_event SET next_attempt_at = now() + interval '1 hour' WHERE id = ?", olderId);
        assertThat(claims.claimNext(Instant.now(), Duration.ofSeconds(15))).isEmpty();
        jdbc.update("UPDATE outbox_event SET next_attempt_at = now() - interval '1 second' WHERE id = ?", olderId);

        OutboxClaim first = claims.claimNext(Instant.now(), Duration.ofSeconds(15)).orElseThrow();
        assertThat(first.id()).isEqualTo(olderId);
        assertThat(first.messageKey()).isEqualTo(orderId.toString());
        assertThat(claims.claimNext(Instant.now(), Duration.ofSeconds(15))).isEmpty();
        assertThat(marks.markSent(olderId, UUID.randomUUID(), Instant.now())).isFalse();
        assertThat(status(olderId)).isEqualTo("IN_PROGRESS");

        jdbc.update("UPDATE outbox_event SET lease_until = now() - interval '1 minute' WHERE id = ?", olderId);
        OutboxClaim reclaimed = claims.claimNext(Instant.now(), Duration.ofSeconds(30)).orElseThrow();
        assertThat(reclaimed.id()).isEqualTo(olderId);
        assertThat(reclaimed.claimToken()).isNotEqualTo(first.claimToken());
        assertThat(marks.markSent(olderId, first.claimToken(), Instant.now())).isFalse();
        assertThat(marks.markRetry(olderId, first.claimToken(), "stale", Instant.now())).isFalse();
        assertThat(status(olderId)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT claim_token FROM outbox_event WHERE id = ?", UUID.class, olderId))
                .isEqualTo(reclaimed.claimToken());
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_event WHERE id = ?", Integer.class, olderId)).isZero();

        assertThat(marks.markSent(olderId, reclaimed.claimToken(), Instant.now())).isTrue();
        assertThat(status(olderId)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT claim_token FROM outbox_event WHERE id = ?", UUID.class, olderId)).isNull();
        jdbc.update("""
                UPDATE outbox_event
                SET next_attempt_at = now() - interval '1 hour', lease_until = now() - interval '1 hour'
                WHERE id = ?
                """, olderId);
        OutboxClaim newer = claims.claimNext(Instant.now(), Duration.ofSeconds(15)).orElseThrow();
        assertThat(newer.id()).isEqualTo(newerId);
        assertThat(status(olderId)).isEqualTo("SENT");
        assertThat(marks.markSent(newerId, newer.claimToken(), Instant.now())).isTrue();
        jdbc.update("""
                UPDATE outbox_event
                SET next_attempt_at = now() - interval '1 hour', lease_until = now() - interval '1 hour'
                """);
        assertThat(claims.claimNext(Instant.now(), Duration.ofSeconds(15))).isEmpty();
    }

    @Test
    void publisherAckMarksSentAndALostLeaseCannotFinishTheOldClaim() {
        acceptedOrder("publish-success-key");
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(completedSend());
        publisher.drain();
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE status = 'SENT'")).isEqualTo(1);
        publisher.drain();
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());

        acceptedOrder("publish-retry-key");
        org.mockito.Mockito.clearInvocations(kafka);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(failedSend())
                .thenReturn(completedSend());
        publisher.drain();
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE status = 'PENDING' AND attempts = 1 AND event_type = ?",
                CheckoutEvents.RESERVE_STOCK)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE status = 'PENDING' AND next_attempt_at > now() AND attempts = 1"))
                .isEqualTo(1);
        publisher.drain();
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
        jdbc.update("UPDATE outbox_event SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'");
        publisher.drain();
        verify(kafka, times(2)).send(anyString(), anyString(), anyString());
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE status = 'SENT' AND event_type = ?", CheckoutEvents.RESERVE_STOCK))
                .isEqualTo(2);

        UUID fenced = acceptedOrder("publish-fence-key");
        UUID outboxId = jdbc.queryForObject(
                "SELECT id FROM outbox_event WHERE aggregate_id = ? AND status = 'PENDING'",
                UUID.class, fenced);
        AtomicReference<UUID> stolen = new AtomicReference<>();
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            jdbc.update("UPDATE outbox_event SET lease_until = now() - interval '1 minute' WHERE id = ?", outboxId);
            stolen.set(claims.claimNext(Instant.now().plusSeconds(5), Duration.ofSeconds(30)).orElseThrow().claimToken());
            return completedSend();
        });
        publisher.drain();
        assertThat(status(outboxId)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT claim_token FROM outbox_event WHERE id = ?", UUID.class, outboxId))
                .isEqualTo(stolen.get());
        assertThat(marks.markSent(outboxId, stolen.get(), Instant.now())).isTrue();
        assertThat(status(outboxId)).isEqualTo("SENT");
        publisher.drain();
        assertThat(status(outboxId)).isEqualTo("SENT");
    }

    private CheckoutResult accept(CustomerQuote quote, String key) {
        return checkout.accept(ISSUER, SUBJECT, "Bearer test", quote.getId(), key, "corr-" + key);
    }

    private UUID acceptedOrder(String key) {
        return orderId(accept(saveQuote(), key));
    }

    private UUID payReady(String key) {
        UUID orderId = acceptedOrder(key);
        apply(orderId, command(orderId, "reserve_command_id"), CheckoutEvents.STOCK_RESERVED);
        apply(orderId, command(orderId, "hold_command_id"), CheckoutEvents.RESERVATION_HELD);
        return orderId;
    }

    private UUID confirmedOrder(String key) {
        UUID orderId = payReady(key);
        apply(orderId, command(orderId, "payment_command_id"), CheckoutEvents.PAYMENT_SUCCEEDED);
        apply(orderId, command(orderId, "consume_command_id"), CheckoutEvents.STOCK_CONSUMED);
        apply(orderId, command(orderId, "cleanup_command_id"), CheckoutEvents.CART_CLEANUP_SKIPPED);
        return orderId;
    }

    private void apply(UUID orderId, UUID commandId, String eventType) {
        apply(orderId, commandId, eventType, UUID.randomUUID());
    }

    private void apply(UUID orderId, UUID commandId, String eventType, UUID eventId) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", orderId.toString());
        envelope.put("aggregateVersion", 1);
        envelope.put("sagaId", orderId.toString());
        if (commandId == null) {
            envelope.putNull("commandId");
        } else {
            envelope.put("commandId", commandId.toString());
        }
        envelope.put("correlationId", orderId.toString());
        envelope.putNull("causationId");
        envelope.put("occurredAt", "2026-10-09T00:00:00Z");
        envelope.putObject("payload");
        outcomes.apply(envelope);
    }

    private CustomerQuote saveQuote() {
        Instant now = Instant.now();
        CustomerQuote quote = new CustomerQuote(
                UUID.randomUUID(),
                ISSUER,
                SUBJECT,
                3,
                "USD",
                PRICE,
                MoneyPolicy.ZERO,
                MoneyPolicy.ZERO,
                PRICE,
                ADDRESS,
                "Home",
                "1 Main",
                null,
                "Austin",
                "TX",
                "78701",
                "US",
                now.plus(Duration.ofMinutes(10)),
                now
        );
        quote.addLine(new QuoteLine(UUID.randomUUID(), VARIANT, "HEADPHONES-BLK", "Black", 1, PRICE, PRICE));
        return quotes.save(quote);
    }

    private static CheckoutSnapshot.CartSnapshotView cart() {
        return new CheckoutSnapshot.CartSnapshotView(3, List.of(new CheckoutSnapshot.CartLine(
                VARIANT, "HEADPHONES-BLK", 1, "Black", PRICE, "USD", "CONFIRMED")));
    }

    private static CheckoutSnapshot.CatalogLine catalogLine() {
        return new CheckoutSnapshot.CatalogLine(VARIANT, "HEADPHONES-BLK", "Black", PRICE, "USD");
    }

    private static CheckoutSnapshot.OwnedAddress address() {
        return new CheckoutSnapshot.OwnedAddress(ADDRESS, "Home", "1 Main", null, "Austin", "TX", "78701", "US");
    }

    private static UUID orderId(CheckoutResult result) {
        return UUID.fromString(result.location().substring("/api/v1/orders/".length()));
    }

    private static String fingerprint(UUID quoteId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(quoteId.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static CompletableFuture<SendResult<String, String>> completedSend() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<SendResult<String, String>> failedSend() {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new TimeoutException("ack timeout"));
        return future;
    }

    private UUID command(UUID orderId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM customer_order WHERE id = ?", UUID.class, orderId);
    }

    private OrderRow order(UUID orderId) {
        return jdbc.queryForObject("""
                SELECT order_status, payment_status, fulfilment_status, saga_step, cancellation_requested,
                       stock_consumed, cleanup_status, attempts, obligation
                FROM customer_order WHERE id = ?
                """, (rs, row) -> new OrderRow(
                rs.getString("order_status"),
                rs.getString("payment_status"),
                rs.getString("fulfilment_status"),
                rs.getString("saga_step"),
                rs.getBoolean("cancellation_requested"),
                rs.getBoolean("stock_consumed"),
                rs.getString("cleanup_status"),
                rs.getInt("attempts"),
                rs.getString("obligation")
        ), orderId);
    }

    private String status(UUID outboxId) {
        return jdbc.queryForObject("SELECT status FROM outbox_event WHERE id = ?", String.class, outboxId);
    }

    private int count(String sql, Object... args) {
        Number value = jdbc.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private static void bootstrap() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + ORDER_USER + " LOGIN PASSWORD '" + ORDER_PASSWORD + "'");
            statement.execute("CREATE SCHEMA \"order\" AUTHORIZATION " + ORDER_USER);
            statement.execute("GRANT ALL ON SCHEMA \"order\" TO " + ORDER_USER);
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record OrderRow(
            String orderStatus,
            String paymentStatus,
            String fulfilmentStatus,
            String sagaStep,
            boolean cancellationRequested,
            boolean stockConsumed,
            String cleanupStatus,
            int attempts,
            String obligation
    ) {
    }
}
