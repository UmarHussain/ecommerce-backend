package com.umar.ecommerce.payment.entity;

import com.umar.ecommerce.payment.domain.RefundStatus;
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
@Table(name = "payment_refund")
public class PaymentRefund {

    @Id
    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Column(name = "charge_operation_id", nullable = false, updatable = false)
    private UUID chargeOperationId;

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
    private RefundStatus status;

    @Column(name = "applied", nullable = false)
    private boolean applied;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentRefund() {
    }

    public static PaymentRefund applied(
            UUID operationId,
            UUID chargeOperationId,
            UUID orderId,
            BigDecimal amount,
            String currency,
            String payloadHash,
            Instant now
    ) {
        return create(operationId, chargeOperationId, orderId, amount, currency, payloadHash, RefundStatus.REFUNDED, true, now);
    }

    public static PaymentRefund failure(
            UUID operationId,
            UUID chargeOperationId,
            UUID orderId,
            BigDecimal amount,
            String currency,
            String payloadHash,
            Instant now
    ) {
        return create(operationId, chargeOperationId, orderId, amount, currency, payloadHash, RefundStatus.FAILED, false, now);
    }

    private static PaymentRefund create(
            UUID operationId,
            UUID chargeOperationId,
            UUID orderId,
            BigDecimal amount,
            String currency,
            String payloadHash,
            RefundStatus status,
            boolean applied,
            Instant now
    ) {
        PaymentRefund refund = new PaymentRefund();
        refund.operationId = operationId;
        refund.chargeOperationId = chargeOperationId;
        refund.orderId = orderId;
        refund.amount = amount;
        refund.currency = currency;
        refund.payloadHash = payloadHash;
        refund.status = status;
        refund.applied = applied;
        refund.attempts = 1;
        refund.createdAt = now;
        refund.updatedAt = now;
        return refund;
    }

    public void applySuccess(Instant now) {
        if (applied) {
            return;
        }
        this.status = RefundStatus.REFUNDED;
        this.applied = true;
        this.attempts = this.attempts + 1;
        this.updatedAt = now;
    }

    public boolean sameHash(String hash) {
        return getPayloadHash().equals(hash);
    }

    public UUID getOperationId() {
        return operationId;
    }

    public UUID getChargeOperationId() {
        return chargeOperationId;
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

    public RefundStatus getStatus() {
        return status;
    }

    public boolean isApplied() {
        return applied;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
