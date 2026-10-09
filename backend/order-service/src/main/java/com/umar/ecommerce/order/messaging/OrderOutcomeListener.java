package com.umar.ecommerce.order.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderOutcomeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderOutcomeListener.class);
    private static final String CONSUMER = "order-checkout";

    private final ObjectMapper objectMapper;
    private final OutcomeApplyService outcomes;
    private final DeadLetterWriter deadLetters;

    public OrderOutcomeListener(
            ObjectMapper objectMapper,
            OutcomeApplyService outcomes,
            DeadLetterWriter deadLetters
    ) {
        this.objectMapper = objectMapper;
        this.outcomes = outcomes;
        this.deadLetters = deadLetters;
    }

    @KafkaListener(topics = {
            CheckoutTopics.INVENTORY_OUTCOMES,
            CheckoutTopics.PAYMENT_OUTCOMES,
            CheckoutTopics.CART_OUTCOMES
    }, groupId = "order-checkout")
    public void onMessage(String body, Acknowledgment acknowledgment) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(body);
            if (envelope.path("eventVersion").asInt() != 1 || !envelope.hasNonNull("eventId")) {
                throw new IllegalArgumentException("unsupported envelope");
            }
            OutcomeApplyService.signal(envelope.path("eventType").asText());
            requiredUuid(envelope, "eventId");
            UUID aggregateId = requiredUuid(envelope, "aggregateId");
            UUID sagaId = requiredUuid(envelope, "sagaId");
            requiredUuid(envelope, "commandId");
            if (!aggregateId.equals(sagaId)) {
                throw new IllegalArgumentException("inconsistent checkout envelope");
            }
        } catch (Exception exception) {
            deadLetters.record(CONSUMER, body, exception.getMessage());
            acknowledgment.acknowledge();
            return;
        }
        try {
            outcomes.apply(envelope);
            acknowledgment.acknowledge();
        } catch (RuntimeException exception) {
            LOGGER.warn("Checkout outcome was not applied and will be retried");
        }
    }

    private static UUID requiredUuid(JsonNode envelope, String field) {
        if (!envelope.hasNonNull(field) || envelope.path(field).asText().isBlank()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return UUID.fromString(envelope.path(field).asText());
    }
}
