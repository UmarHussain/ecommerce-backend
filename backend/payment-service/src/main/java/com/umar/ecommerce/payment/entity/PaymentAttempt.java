package com.umar.ecommerce.payment.entity;

import com.umar.ecommerce.payment.domain.AttemptStatus;
import com.umar.ecommerce.payment.messaging.Envelope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt {

    @Id
    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AttemptStatus status;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "correlation_id", length = 200)
    private String correlationId;

    @Column(name = "request_event_id")
    private UUID requestEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentAttempt() {
    }

    public static PaymentAttempt create(
            UUID operationId,
            UUID orderId,
            BigDecimal amount,
            String currency,
            String payloadHash,
            AttemptStatus status,
            Instant now,
            Envelope command
    ) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.operationId = operationId;
        attempt.orderId = orderId;
        attempt.amount = amount;
        attempt.currency = currency;
        attempt.payloadHash = payloadHash;
        attempt.status = status;
        attempt.version = 1L;
        attempt.sagaId = command.sagaId();
        attempt.correlationId = command.correlationId();
        attempt.requestEventId = command.eventId();
        attempt.createdAt = now;
        attempt.updatedAt = now;
        return attempt;
    }

    public void succeed(Instant now) {
        this.status = AttemptStatus.SUCCEEDED;
        this.version = this.version + 1;
        this.updatedAt = now;
    }

    public boolean sameHash(String hash) {
        return getPayloadHash().equals(hash);
    }

    public UUID getOperationId() {
        return operationId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency == null ? null : currency.trim();
    }

    public String getPayloadHash() {
        return payloadHash == null ? null : payloadHash.trim();
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public UUID getRequestEventId() {
        return requestEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
