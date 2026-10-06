package com.umar.ecommerce.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "access_audit")
public class AccessAudit {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_issuer", nullable = false)
    private String actorIssuer;

    @Column(name = "actor_subject", nullable = false)
    private String actorSubject;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "action", nullable = false)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state")
    private Map<String, Object> afterState;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccessAudit() {
    }

    public AccessAudit(
            String actorIssuer,
            String actorSubject,
            UUID targetUserId,
            String action,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            String outcome,
            String correlationId
    ) {
        this.id = UUID.randomUUID();
        this.actorIssuer = actorIssuer;
        this.actorSubject = actorSubject;
        this.targetUserId = targetUserId;
        this.action = action;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.outcome = outcome;
        this.correlationId = correlationId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getActorIssuer() {
        return actorIssuer;
    }

    public String getActorSubject() {
        return actorSubject;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public String getAction() {
        return action;
    }

    public Map<String, Object> getBeforeState() {
        return beforeState;
    }

    public Map<String, Object> getAfterState() {
        return afterState;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
