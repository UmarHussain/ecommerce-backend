package com.umar.ecommerce.inventory.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umar.ecommerce.inventory.domain.ReservationDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "inventory.messaging.listener-enabled", matchIfMissing = true)
public class InventoryCommandListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);

    private final InventoryEnvelopeParser parser;
    private final com.umar.ecommerce.inventory.service.ReservationTransactionService transactions;
    private final DeadLetterWriter deadLetters;

    public InventoryCommandListener(
            InventoryEnvelopeParser parser,
            com.umar.ecommerce.inventory.service.ReservationTransactionService transactions,
            DeadLetterWriter deadLetters
    ) {
        this.parser = parser;
        this.transactions = transactions;
        this.deadLetters = deadLetters;
    }

    @KafkaListener(topics = InventoryChannels.COMMANDS, groupId = InventoryChannels.GROUP)
    public void onCommand(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        InventoryEnvelopeParser.Parsed parsed = parser.parse(record.value(), record.key());
        if (parsed.poisonReason() != null) {
            deadLetters.record(record.topic(), record.key(), record.value(), parsed.eventId(), parsed.poisonReason());
            acknowledgment.acknowledge();
            return;
        }
        try {
            transactions.apply(parsed.command());
            acknowledgment.acknowledge();
        } catch (RuntimeException exception) {
            log.warn("Inventory command was not committed and will be retried");
            throw exception;
        }
    }
}

@Component
class InventoryEnvelopeParser {

    private static final Set<String> COMMANDS = Set.of(
            InventoryChannels.RESERVE_STOCK,
            InventoryChannels.HOLD_RESERVATION,
            InventoryChannels.RELEASE_RESERVATION,
            InventoryChannels.CONSUME_RESERVATION,
            InventoryChannels.RESTOCK_RESERVATION
    );

    private final ObjectMapper objectMapper;

    InventoryEnvelopeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Parsed parse(String raw, String messageKey) {
        UUID eventId = null;
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root == null || !root.isObject()) {
                return Parsed.poison(null, "malformed envelope");
            }
            eventId = optionalUuid(root.get("eventId"));
            JsonNode version = root.get("eventVersion");
            if (version == null || !version.isNumber() || version.asInt() != InventoryChannels.VERSION) {
                return Parsed.poison(eventId, "unsupported version");
            }
            String eventType = text(root.get("eventType"));
            if (!COMMANDS.contains(eventType)) {
                return Parsed.poison(eventId, "unsupported event type");
            }
            UUID aggregateId = requiredUuid(root.get("aggregateId"));
            UUID commandId = requiredUuid(root.get("commandId"));
            JsonNode aggregateVersion = root.get("aggregateVersion");
            if (aggregateVersion == null || !aggregateVersion.isNumber()) {
                return Parsed.poison(eventId, "malformed envelope");
            }
            JsonNode occurredAt = root.get("occurredAt");
            if (occurredAt == null || !occurredAt.isTextual()) {
                return Parsed.poison(eventId, "malformed envelope");
            }
            JsonNode payload = root.get("payload");
            if (payload == null || !payload.isObject()) {
                return Parsed.poison(eventId, "malformed envelope");
            }
            UUID orderId = requiredUuid(payload.get("orderId"));
            if (!orderId.equals(aggregateId) || messageKey == null || !messageKey.equals(orderId.toString())) {
                return Parsed.poison(eventId, "aggregate mismatch");
            }
            List<ReservationDigest.Line> lines = List.of();
            if (InventoryChannels.RESERVE_STOCK.equals(eventType)) {
                lines = readLines(payload.get("lines"));
            }
            CheckoutCommand command = new CheckoutCommand(
                    eventId,
                    eventType,
                    orderId,
                    aggregateVersion.asLong(),
                    optionalUuid(root.get("sagaId")),
                    commandId,
                    text(root.get("correlationId")),
                    optionalUuid(root.get("causationId")),
                    Instant.parse(occurredAt.asText()),
                    lines
            );
            return Parsed.accepted(command);
        } catch (RuntimeException exception) {
            return Parsed.poison(eventId, "malformed envelope");
        } catch (Exception exception) {
            return Parsed.poison(eventId, "malformed envelope");
        }
    }

    private static List<ReservationDigest.Line> readLines(JsonNode lines) {
        if (lines == null || !lines.isArray()) {
            throw new IllegalArgumentException("lines");
        }
        List<ReservationDigest.Line> parsed = new ArrayList<>();
        for (JsonNode line : lines) {
            if (line == null || !line.isObject()) {
                throw new IllegalArgumentException("line");
            }
            JsonNode quantity = line.get("quantity");
            if (quantity == null || !quantity.isIntegralNumber()) {
                throw new IllegalArgumentException("quantity");
            }
            String sku = text(line.get("sku"));
            if (sku.isBlank()) {
                throw new IllegalArgumentException("sku");
            }
            parsed.add(new ReservationDigest.Line(requiredUuid(line.get("catalogVariantId")), sku, quantity.asInt()));
        }
        return List.copyOf(parsed);
    }

    private static UUID requiredUuid(JsonNode node) {
        UUID value = optionalUuid(node);
        if (value == null) {
            throw new IllegalArgumentException("uuid");
        }
        return value;
    }

    private static UUID optionalUuid(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("uuid");
        }
        return UUID.fromString(node.asText());
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("text");
        }
        return node.asText();
    }

    record Parsed(CheckoutCommand command, UUID eventId, String poisonReason) {
        static Parsed accepted(CheckoutCommand command) {
            return new Parsed(command, command.eventId(), null);
        }

        static Parsed poison(UUID eventId, String reason) {
            return new Parsed(null, eventId, reason);
        }
    }
}

@Component
class DeadLetterWriter {

    private final jakarta.persistence.EntityManager entityManager;

    DeadLetterWriter(jakarta.persistence.EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void record(String topic, String messageKey, String payload, UUID eventId, String reason) {
        String safeReason = reason == null ? "poison" : reason;
        if (safeReason.length() > 300) {
            safeReason = safeReason.substring(0, 300);
        }
        String key = messageKey == null ? null : messageKey.length() <= 80 ? messageKey : messageKey.substring(0, 80);
        entityManager.createNativeQuery("""
                        insert into dead_letter (
                            id, consumer_name, event_id, topic, message_key, payload, reason, created_at
                        ) values (
                            :id, :consumer, :eventId, :topic, :messageKey, :payload, :reason, now()
                        )
                        """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("consumer", InventoryChannels.GROUP)
                .setParameter("eventId", eventId)
                .setParameter("topic", topic)
                .setParameter("messageKey", key)
                .setParameter("payload", payload == null ? "" : payload)
                .setParameter("reason", safeReason)
                .executeUpdate();
    }
}
