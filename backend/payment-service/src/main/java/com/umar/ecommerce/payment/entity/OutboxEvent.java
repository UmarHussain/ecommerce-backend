package com.umar.ecommerce.payment.entity;

import com.umar.ecommerce.payment.domain.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "topic", nullable = false, updatable = false, length = 200)
    private String topic;

    @Column(name = "message_key", nullable = false, updatable = false, length = 200)
    private String messageKey;

    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 200)
    private String aggregateId;

    @Column(name = "envelope", nullable = false, updatable = false, columnDefinition = "text")
    private String envelope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(
            UUID id,
            UUID eventId,
            String topic,
            String messageKey,
            String aggregateId,
            String envelope,
            Instant now
    ) {
        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.eventId = eventId;
        event.topic = topic;
        event.messageKey = messageKey;
        event.aggregateId = aggregateId;
        event.envelope = envelope;
        event.status = OutboxStatus.PENDING;
        event.nextAttemptAt = now;
        event.attempts = 0;
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEnvelope() {
        return envelope;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
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
