package com.umar.ecommerce.cart.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.umar.ecommerce.cart.entity.Cart;
import com.umar.ecommerce.cart.entity.CartItem;
import com.umar.ecommerce.cart.repository.CartRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class CartCleanupService {

    static final String CONSUMER = "cart-checkout";
    static final String TOPIC = "checkout.cart.outcomes";

    private final EntityManager entityManager;
    private final CartRepository carts;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public CartCleanupService(
            EntityManager entityManager,
            CartRepository carts,
            ObjectMapper objectMapper,
            ApplicationEventPublisher events
    ) {
        this.entityManager = entityManager;
        this.carts = carts;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    @Transactional
    public void apply(JsonNode envelope) {
        UUID eventId = UUID.fromString(envelope.path("eventId").asText());
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO inbox_event (consumer_name, event_id, processed_at)
                VALUES (:consumer, :eventId, CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """)
                .setParameter("consumer", CONSUMER)
                .setParameter("eventId", eventId)
                .executeUpdate();
        if (inserted == 0) {
            return;
        }
        JsonNode payload = envelope.path("payload");
        UUID commandId = UUID.fromString(envelope.path("commandId").asText());
        UUID orderId = UUID.fromString(payload.path("orderId").asText());
        String hash = hash(payload);
        Object existing = entityManager.createNativeQuery("""
                SELECT payload_hash FROM cleanup_effect WHERE command_id = :commandId
                """)
                .setParameter("commandId", commandId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return;
        }
        String issuer = payload.path("ownerIssuer").asText();
        String subject = payload.path("ownerSubject").asText();
        long version = payload.path("cartVersion").asLong();
        Cart cart = carts.lockByOwner(issuer, subject).orElse(null);
        String outcome = matches(cart, version, payload.path("lines")) ? "CLEARED" : "SKIPPED";
        if ("CLEARED".equals(outcome) && cart != null) {
            cart.clearItems();
            cart.advance(Instant.now());
        }
        entityManager.createNativeQuery("""
                INSERT INTO cleanup_effect (command_id, order_id, payload_hash, outcome)
                VALUES (:commandId, :orderId, :hash, :outcome)
                """)
                .setParameter("commandId", commandId)
                .setParameter("orderId", orderId)
                .setParameter("hash", hash)
                .setParameter("outcome", outcome)
                .executeUpdate();
        stage(orderId, commandId, eventId, outcome);
    }

    private boolean matches(Cart cart, long version, JsonNode lines) {
        if (cart == null || cart.getAggregateVersion() != version) {
            return false;
        }
        TreeSet<String> expected = new TreeSet<>();
        for (JsonNode line : lines) {
            expected.add(line.path("sku").asText() + "|" + line.path("catalogVariantId").asText() + "|" + line.path("quantity").asInt());
        }
        TreeSet<String> actual = new TreeSet<>();
        for (CartItem item : cart.getItems()) {
            actual.add(item.getSku() + "|" + item.getCatalogVariantId() + "|" + item.getQuantity());
        }
        return expected.equals(actual);
    }

    private void stage(UUID orderId, UUID commandId, UUID causationId, String outcome) {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String eventType = "CLEARED".equals(outcome) ? "CartCleared" : "CartCleanupSkipped";
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", orderId.toString());
        envelope.put("aggregateVersion", 1);
        envelope.put("sagaId", orderId.toString());
        envelope.put("commandId", commandId.toString());
        envelope.put("correlationId", orderId.toString());
        envelope.put("causationId", causationId.toString());
        envelope.put("occurredAt", Instant.now().toString());
        ObjectNode payload = envelope.putObject("payload");
        payload.put("orderId", orderId.toString());
        payload.put("outcome", outcome);
        String encoded;
        try {
            encoded = objectMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        entityManager.createNativeQuery("""
                INSERT INTO outbox_event (
                    id, event_id, event_type, aggregate_id, command_id, topic, message_key, envelope, status, next_attempt_at, attempts
                ) VALUES (
                    :id, :eventId, :eventType, :aggregateId, :commandId, :topic, :messageKey, :envelope, 'PENDING', CURRENT_TIMESTAMP, 0
                )
                """)
                .setParameter("id", id)
                .setParameter("eventId", eventId)
                .setParameter("eventType", eventType)
                .setParameter("aggregateId", orderId)
                .setParameter("commandId", commandId)
                .setParameter("topic", TOPIC)
                .setParameter("messageKey", orderId.toString())
                .setParameter("envelope", encoded)
                .executeUpdate();
        events.publishEvent(new CartOutboxReady(id));
    }

    private static String hash(JsonNode payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
