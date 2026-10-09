package com.umar.ecommerce.inventory.messaging;

import com.umar.ecommerce.inventory.domain.ReservationDigest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One inbound checkout command after the envelope has been parsed.
 * Reservations start here, not from the admin HTTP API.
 */
public record CheckoutCommand(
        UUID eventId,
        String eventType,
        UUID orderId,
        long aggregateVersion,
        UUID sagaId,
        UUID commandId,
        String correlationId,
        UUID causationId,
        Instant occurredAt,
        List<ReservationDigest.Line> lines
) {
}
