package com.umar.ecommerce.payment;

import com.umar.ecommerce.payment.domain.ChargeScenario;
import com.umar.ecommerce.payment.domain.RefundScenario;
import com.umar.ecommerce.payment.messaging.PaymentCommandProcessor;
import com.umar.ecommerce.payment.service.DueWorker;
import com.umar.ecommerce.payment.service.SimulatorControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.bootstrap-servers=127.0.0.1:1",
        "payment.messaging.listener-enabled=false",
        "payment.outbox.immediate-dispatch=false",
        "payment.outbox.poll-delay=1h",
        "payment.simulator.due-poll-delay=1h",
        "payment.simulator.delay=1h",
        "payment.simulator.charge-outcome=SUCCESS",
        "payment.simulator.refund-outcome=SUCCESS",
        "payment.simulator.control-enabled=false",
        "management.health.kafka.enabled=false"
})
@Testcontainers
class PaymentAttemptIT {

    private static final String PAYMENT_USER = "payment_app";
    private static final String PAYMENT_PASSWORD = "test_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID SAGA = UUID.fromString("51000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.6-alpine")
    ).withDatabaseName("payment_test");

    static {
        POSTGRES.start();
        bootstrap();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> PAYMENT_USER);
        registry.add("spring.datasource.password", () -> PAYMENT_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> "payment");
        registry.add("spring.flyway.default-schema", () -> "payment");
        registry.add("spring.flyway.schemas", () -> "payment");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "payment");
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PaymentCommandProcessor commands;
    @Autowired
    private DueWorker dueWorker;
    @Autowired
    private SimulatorControlService controls;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM inbox_event");
        jdbc.update("DELETE FROM dead_letter");
        jdbc.update("DELETE FROM simulator_due");
        jdbc.update("DELETE FROM payment_refund");
        jdbc.update("DELETE FROM payment_attempt");
        jdbc.update("DELETE FROM simulator_control");
    }

    @Test
    void sameOperationAndPayloadKeepsOneAttempt() {
        UUID orderId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        String first = request(UUID.randomUUID(), operationId, orderId, "25.00");
        commands.accept(first);
        commands.accept(first);
        commands.accept(request(UUID.randomUUID(), operationId, orderId, "25.00"));

        assertThat(count("SELECT COUNT(*) FROM payment_attempt")).isEqualTo(1);
        assertThat(status(operationId)).isEqualTo("SUCCEEDED");
        assertThat(amount(operationId)).isEqualByComparingTo("25.00");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE envelope LIKE '%PaymentSucceeded%'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM simulator_due")).isZero();
    }

    @Test
    void changedAmountDoesNotAddASecondEffect() {
        UUID orderId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        commands.accept(request(UUID.randomUUID(), operationId, orderId, "25.00"));
        commands.accept(request(UUID.randomUUID(), operationId, orderId, "40.00"));

        assertThat(count("SELECT COUNT(*) FROM payment_attempt")).isEqualTo(1);
        assertThat(amount(operationId)).isEqualByComparingTo("25.00");
        assertThat(status(operationId)).isEqualTo("SUCCEEDED");
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE envelope LIKE '%PaymentConflict%'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM payment_refund")).isZero();
    }

    @Test
    void refundAppliesOnce() {
        UUID orderId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        commands.accept(request(UUID.randomUUID(), chargeId, orderId, "25.00"));
        commands.accept(refund(UUID.randomUUID(), refundId, orderId, chargeId, "25.00"));
        commands.accept(refund(UUID.randomUUID(), refundId, orderId, chargeId, "25.00"));
        commands.accept(refund(UUID.randomUUID(), UUID.randomUUID(), orderId, chargeId, "25.00"));
        commands.accept(refund(UUID.randomUUID(), refundId, orderId, chargeId, "9.00"));

        assertThat(count("SELECT COUNT(*) FROM payment_refund")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM payment_refund WHERE applied")).isEqualTo(1);
        assertThat(refundAmount(refundId)).isEqualByComparingTo("25.00");
        assertThat(refundAttempts(refundId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE envelope LIKE '%PaymentRefunded%'")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE envelope LIKE '%RefundConflict%'")).isEqualTo(1);
    }

    @Test
    void failedRefundCanLaterSucceedOnce() {
        UUID orderId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        commands.accept(request(UUID.randomUUID(), chargeId, orderId, "25.00"));
        controls.upsert(ChargeScenario.SUCCESS, RefundScenario.REFUND_FAILURE);
        commands.accept(refund(UUID.randomUUID(), refundId, orderId, chargeId, "25.00"));
        assertThat(refundStatus(refundId)).isEqualTo("FAILED");
        assertThat(applied(refundId)).isFalse();
        assertThat(refundAttempts(refundId)).isEqualTo(1);

        controls.upsert(ChargeScenario.SUCCESS, RefundScenario.SUCCESS);
        commands.accept(refund(UUID.randomUUID(), refundId, orderId, chargeId, "25.00"));
        commands.accept(refund(UUID.randomUUID(), refundId, orderId, chargeId, "25.00"));

        assertThat(count("SELECT COUNT(*) FROM payment_refund WHERE applied")).isEqualTo(1);
        assertThat(refundStatus(refundId)).isEqualTo("REFUNDED");
        assertThat(applied(refundId)).isTrue();
        assertThat(refundAttempts(refundId)).isEqualTo(2);
        assertThat(refundAmount(refundId)).isEqualByComparingTo("25.00");
    }

    @Test
    void timeoutStaysUnknownUntilLateSuccessDueIsApplied() {
        UUID orderId = UUID.randomUUID();
        UUID timeoutId = UUID.randomUUID();
        controls.upsert(ChargeScenario.TIMEOUT, RefundScenario.SUCCESS);
        commands.accept(request(UUID.randomUUID(), timeoutId, orderId, "25.00"));
        dueWorker.poll();
        commands.accept(query(UUID.randomUUID(), timeoutId, orderId, timeoutId));

        assertThat(status(timeoutId)).isEqualTo("UNKNOWN");
        assertThat(count("SELECT COUNT(*) FROM payment_attempt")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM simulator_due")).isZero();
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE envelope LIKE '%PaymentUnknown%'")).isEqualTo(2);

        UUID lateId = UUID.randomUUID();
        controls.upsert(ChargeScenario.LATE_SUCCESS, RefundScenario.SUCCESS);
        commands.accept(request(UUID.randomUUID(), lateId, orderId, "18.00"));
        commands.accept(query(UUID.randomUUID(), UUID.randomUUID(), orderId, lateId));
        dueWorker.poll();

        assertThat(status(lateId)).isEqualTo("UNKNOWN");
        assertThat(count("SELECT COUNT(*) FROM payment_attempt WHERE operation_id = ?", lateId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM simulator_due WHERE operation_id = ? AND NOT completed", lateId)).isEqualTo(1);
        assertThat(status(timeoutId)).isEqualTo("UNKNOWN");

        jdbc.update("UPDATE simulator_due SET due_at = now() - interval '1 minute' WHERE operation_id = ?", lateId);
        dueWorker.poll();

        assertThat(status(lateId)).isEqualTo("SUCCEEDED");
        assertThat(count("SELECT COUNT(*) FROM simulator_due WHERE operation_id = ? AND completed", lateId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE envelope LIKE '%PaymentSucceeded%'")).isEqualTo(1);
        assertThat(status(timeoutId)).isEqualTo("UNKNOWN");
        assertThat(count("SELECT COUNT(*) FROM payment_attempt")).isEqualTo(2);
    }

    @Test
    void controlChangeDoesNotRewriteAnExistingAttempt() {
        UUID orderId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        commands.accept(request(UUID.randomUUID(), first, orderId, "25.00"));
        controls.upsert(ChargeScenario.DECLINE, RefundScenario.SUCCESS);
        commands.accept(request(UUID.randomUUID(), first, orderId, "25.00"));
        UUID second = UUID.randomUUID();
        commands.accept(request(UUID.randomUUID(), second, orderId, "25.00"));

        assertThat(status(first)).isEqualTo("SUCCEEDED");
        assertThat(amount(first)).isEqualByComparingTo("25.00");
        assertThat(status(second)).isEqualTo("DECLINED");
        assertThat(count("SELECT COUNT(*) FROM payment_attempt")).isEqualTo(2);
    }

    @Test
    void unsupportedVersionIsDeadLetteredWithoutAnAttempt() {
        commands.accept("{\"eventId\":\"72000000-0000-0000-0000-000000000001\",\"eventVersion\":2}");
        assertThat(count("SELECT COUNT(*) FROM dead_letter")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM payment_attempt")).isZero();
        assertThat(count("SELECT COUNT(*) FROM inbox_event")).isZero();
    }

    private static void bootstrap() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + PAYMENT_USER + " LOGIN PASSWORD '" + PAYMENT_PASSWORD + "'");
            statement.execute("CREATE SCHEMA payment AUTHORIZATION " + PAYMENT_USER);
            statement.execute("GRANT ALL ON SCHEMA payment TO " + PAYMENT_USER);
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private String request(UUID eventId, UUID commandId, UUID orderId, String amount) {
        return command(eventId, "RequestPayment", commandId, orderId,
                "{\"orderId\":\"" + orderId + "\",\"amount\":\"" + amount + "\",\"currency\":\"USD\",\"scenario\":\"DECLINE\"}");
    }

    private String query(UUID eventId, UUID commandId, UUID orderId, UUID paymentOperationId) {
        return command(eventId, "QueryPaymentStatus", commandId, orderId,
                "{\"orderId\":\"" + orderId + "\",\"paymentOperationId\":\"" + paymentOperationId + "\"}");
    }

    private String refund(UUID eventId, UUID commandId, UUID orderId, UUID paymentOperationId, String amount) {
        return command(eventId, "RefundPayment", commandId, orderId,
                "{\"orderId\":\"" + orderId + "\",\"paymentOperationId\":\"" + paymentOperationId
                        + "\",\"amount\":\"" + amount + "\",\"currency\":\"USD\"}");
    }

    private String command(UUID eventId, String eventType, UUID commandId, UUID orderId, String payload) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType
                + "\",\"eventVersion\":1,\"aggregateId\":\"" + orderId
                + "\",\"aggregateVersion\":1,\"sagaId\":\"" + SAGA + "\",\"commandId\":\"" + commandId
                + "\",\"correlationId\":\"corr\",\"causationId\":\"" + eventId
                + "\",\"occurredAt\":\"2026-10-09T00:00:00Z\",\"payload\":" + payload + "}";
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String status(UUID operationId) {
        return jdbc.queryForObject("SELECT status FROM payment_attempt WHERE operation_id = ?", String.class, operationId);
    }

    private BigDecimal amount(UUID operationId) {
        return jdbc.queryForObject("SELECT amount FROM payment_attempt WHERE operation_id = ?", BigDecimal.class, operationId);
    }

    private String refundStatus(UUID operationId) {
        return jdbc.queryForObject("SELECT status FROM payment_refund WHERE operation_id = ?", String.class, operationId);
    }

    private BigDecimal refundAmount(UUID operationId) {
        return jdbc.queryForObject("SELECT amount FROM payment_refund WHERE operation_id = ?", BigDecimal.class, operationId);
    }

    private int refundAttempts(UUID operationId) {
        Integer value = jdbc.queryForObject(
                "SELECT attempts FROM payment_refund WHERE operation_id = ?", Integer.class, operationId);
        return value == null ? 0 : value;
    }

    private boolean applied(UUID operationId) {
        Boolean value = jdbc.queryForObject(
                "SELECT applied FROM payment_refund WHERE operation_id = ?", Boolean.class, operationId);
        return Boolean.TRUE.equals(value);
    }
}
