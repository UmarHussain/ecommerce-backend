package com.umar.ecommerce.cart.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class CartCleanupListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CartCleanupListener.class);

    private final ObjectMapper objectMapper;
    private final CartCleanupService cleanup;
    private final CartDeadLetter deadLetters;

    public CartCleanupListener(ObjectMapper objectMapper, CartCleanupService cleanup, CartDeadLetter deadLetters) {
        this.objectMapper = objectMapper;
        this.cleanup = cleanup;
        this.deadLetters = deadLetters;
    }

    @KafkaListener(topics = "checkout.cart.commands", groupId = "cart-checkout")
    public void onMessage(String body, Acknowledgment acknowledgment) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(body);
            if (envelope.path("eventVersion").asInt() != 1 || !"ClearCheckedOutCart".equals(envelope.path("eventType").asText())) {
                throw new IllegalArgumentException("unsupported cart command");
            }
        } catch (Exception exception) {
            deadLetters.record(body, exception.getMessage());
            acknowledgment.acknowledge();
            return;
        }
        try {
            cleanup.apply(envelope);
            acknowledgment.acknowledge();
        } catch (RuntimeException exception) {
            LOGGER.warn("Cart cleanup was not applied and will be retried");
        }
    }
}
