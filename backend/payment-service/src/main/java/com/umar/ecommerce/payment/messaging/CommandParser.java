package com.umar.ecommerce.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umar.ecommerce.payment.domain.MoneyRules;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

@Component
public class CommandParser {

    private static final Set<String> SUPPORTED = Set.of(
            PaymentEventTypes.REQUEST,
            PaymentEventTypes.QUERY,
            PaymentEventTypes.REFUND
    );

    private final ObjectMapper objectMapper;

    public CommandParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedCommand parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ParsedCommand.rejected(null, "EMPTY", raw);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (JsonProcessingException exception) {
            return ParsedCommand.rejected(null, "MALFORMED_JSON", raw);
        }
        if (root == null || !root.isObject()) {
            return ParsedCommand.rejected(null, "MALFORMED_JSON", raw);
        }
        UUID eventId = uuid(root.get("eventId"));
        JsonNode versionNode = root.get("eventVersion");
        if (eventId == null) {
            return ParsedCommand.rejected(null, "MISSING_EVENT_ID", raw);
        }
        if (versionNode == null || !versionNode.canConvertToInt() || versionNode.intValue() != PaymentEventTypes.VERSION) {
            return ParsedCommand.rejected(eventId, "UNSUPPORTED_VERSION", raw);
        }
        String eventType = text(root.get("eventType"));
        if (eventType == null || !SUPPORTED.contains(eventType)) {
            return ParsedCommand.rejected(eventId, "UNSUPPORTED_TYPE", raw);
        }
        UUID commandId = uuid(root.get("commandId"));
        if (commandId == null) {
            return ParsedCommand.rejected(eventId, "MISSING_COMMAND_ID", raw);
        }
        JsonNode payload = root.get("payload");
        if (payload == null || !payload.isObject()) {
            return ParsedCommand.rejected(eventId, "INVALID_PAYLOAD", raw);
        }
        String payloadError = validatePayload(eventType, payload);
        if (payloadError != null) {
            return ParsedCommand.rejected(eventId, payloadError, raw);
        }
        Envelope envelope = new Envelope(
                eventId,
                eventType,
                PaymentEventTypes.VERSION,
                text(root.get("aggregateId")),
                versionNode.longValue(),
                uuid(root.get("sagaId")),
                commandId,
                text(root.get("correlationId")),
                uuid(root.get("causationId")),
                instant(root.get("occurredAt")),
                payload
        );
        return ParsedCommand.accepted(envelope);
    }

    private static String validatePayload(String eventType, JsonNode payload) {
        if (uuid(payload.get("orderId")) == null) {
            return "INVALID_PAYLOAD";
        }
        if (PaymentEventTypes.QUERY.equals(eventType)) {
            return uuid(payload.get("paymentOperationId")) == null ? "INVALID_PAYLOAD" : null;
        }
        if (MoneyRules.amount(payload.get("amount")) == null || MoneyRules.currency(payload.get("currency")) == null) {
            return "INVALID_PAYLOAD";
        }
        if (PaymentEventTypes.REFUND.equals(eventType) && uuid(payload.get("paymentOperationId")) == null) {
            return "INVALID_PAYLOAD";
        }
        return null;
    }

    private static UUID uuid(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return UUID.fromString(node.asText().trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static Instant instant(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return Instant.parse(node.asText().trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
