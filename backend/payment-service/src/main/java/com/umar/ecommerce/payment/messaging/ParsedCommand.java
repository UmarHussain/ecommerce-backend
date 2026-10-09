package com.umar.ecommerce.payment.messaging;

import java.util.UUID;

public record ParsedCommand(Envelope envelope, UUID eventId, String reason, String raw) {

    public static ParsedCommand accepted(Envelope envelope) {
        return new ParsedCommand(envelope, envelope.eventId(), null, null);
    }

    public static ParsedCommand rejected(UUID eventId, String reason, String raw) {
        return new ParsedCommand(null, eventId, reason, raw);
    }

    public boolean rejected() {
        return reason != null;
    }
}
