package com.umar.ecommerce.inventory.entity;

import com.umar.ecommerce.inventory.domain.CommandStatus;
import com.umar.ecommerce.inventory.domain.OperationScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_command")
public class InventoryCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_issuer", nullable = false, updatable = false, length = 300)
    private String actorIssuer;

    @Column(name = "actor_subject", nullable = false, updatable = false, length = 200)
    private String actorSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_scope", nullable = false, updatable = false, length = 32)
    private OperationScope operationScope;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_status", nullable = false, length = 20)
    private CommandStatus commandStatus;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "content_type", length = 80)
    private String contentType;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "location", length = 300)
    private String location;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected InventoryCommand() {
    }

    public static InventoryCommand start(
            String actorIssuer,
            String actorSubject,
            OperationScope operationScope,
            String idempotencyKey,
            String requestFingerprint
    ) {
        InventoryCommand command = new InventoryCommand();
        command.actorIssuer = actorIssuer;
        command.actorSubject = actorSubject;
        command.operationScope = operationScope;
        command.idempotencyKey = idempotencyKey;
        command.requestFingerprint = requestFingerprint;
        command.commandStatus = CommandStatus.IN_PROGRESS;
        return command;
    }

    public void complete(int status, String contentType, String body, String location) {
        this.commandStatus = CommandStatus.COMPLETED;
        this.httpStatus = status;
        this.contentType = contentType;
        this.responseBody = body;
        this.location = location;
        this.completedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isCompleted() {
        return commandStatus == CommandStatus.COMPLETED;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getContentType() {
        return contentType;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getLocation() {
        return location;
    }
}
