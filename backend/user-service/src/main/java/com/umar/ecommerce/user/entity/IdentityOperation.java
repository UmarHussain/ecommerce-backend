package com.umar.ecommerce.user.entity;

import com.umar.ecommerce.user.domain.OperationStatus;
import com.umar.ecommerce.user.domain.OperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_operation")
public class IdentityOperation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "target_identity")
    private String targetIdentity;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private OperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OperationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "result_user_id")
    private UUID resultUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdentityOperation() {
    }

    public IdentityOperation(
            String idempotencyKey,
            String requestHash,
            OperationType operationType,
            String targetIdentity
    ) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.operationType = operationType;
        this.targetIdentity = targetIdentity;
        this.status = OperationStatus.PENDING;
        this.attemptCount = 0;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markInProgress() {
        status = OperationStatus.IN_PROGRESS;
        attemptCount += 1;
        lastError = null;
    }

    public void markSucceeded(UUID resultUserId) {
        status = OperationStatus.SUCCEEDED;
        this.resultUserId = resultUserId;
        nextAttemptAt = null;
        lastError = null;
    }

    public void markUncertain(String error) {
        status = OperationStatus.UNCERTAIN;
        lastError = truncate(error);
        nextAttemptAt = Instant.now().plusSeconds(15L * Math.max(1, attemptCount));
    }

    public void markFailed(String error) {
        status = OperationStatus.FAILED;
        lastError = truncate(error);
        nextAttemptAt = null;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getTargetIdentity() {
        return targetIdentity;
    }

    public void setTargetIdentity(String targetIdentity) {
        this.targetIdentity = targetIdentity;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public UUID getResultUserId() {
        return resultUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
