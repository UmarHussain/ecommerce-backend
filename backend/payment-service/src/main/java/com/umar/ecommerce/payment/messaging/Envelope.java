package com.umar.ecommerce.payment.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record Envelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateId,
        long aggregateVersion,
        UUID sagaId,
        UUID commandId,
        String correlationId,
        UUID causationId,
        Instant occurredAt,
        JsonNode payload
) {
}
