package com.umar.ecommerce.inventory.entity;

import com.umar.ecommerce.inventory.messaging.InventoryChannels;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    public static final String PENDING = "PENDING";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String SENT = "SENT";
    public static final String DEAD = "DEAD";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_version", nullable = false, updatable = false)
    private long aggregateVersion;

    @Column(name = "saga_id", updatable = false)
    private UUID sagaId;

    @Column(name = "command_id", updatable = false)
    private UUID commandId;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 80)
    private String correlationId;

    @Column(name = "causation_id", updatable = false)
    private UUID causationId;

    @Column(name = "topic", nullable = false, updatable = false, length = 120)
    private String topic;

    @Column(name = "message_key", nullable = false, updatable = false, length = 80)
    private String messageKey;

    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Column(name = "envelope", nullable = false, updatable = false)
    private String envelope;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(
            UUID id,
            UUID eventId,
            String eventType,
            UUID aggregateId,
            long aggregateVersion,
            UUID sagaId,
            UUID commandId,
            String correlationId,
            UUID causationId,
            String payload,
            String envelope,
            Instant now
    ) {
        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.eventId = eventId;
        event.eventType = eventType;
        event.eventVersion = InventoryChannels.VERSION;
        event.aggregateId = aggregateId;
        event.aggregateVersion = aggregateVersion;
        event.sagaId = sagaId;
        event.commandId = commandId;
        event.correlationId = correlationId;
        event.causationId = causationId;
        event.topic = InventoryChannels.OUTCOMES;
        event.messageKey = aggregateId.toString();
        event.payload = payload;
        event.envelope = envelope;
        event.status = PENDING;
        event.nextAttemptAt = now;
        event.attempts = 0;
        event.createdAt = now;
        return event;
    }
}
