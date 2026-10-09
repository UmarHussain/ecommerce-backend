package com.umar.ecommerce.order.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.umar.ecommerce.order.domain.SagaPolicy;
import com.umar.ecommerce.order.service.SagaAdvanceService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutcomeApplyService {

    private static final String CONSUMER = "order-checkout";

    private final EntityManager entityManager;
    private final SagaAdvanceService saga;

    public OutcomeApplyService(EntityManager entityManager, SagaAdvanceService saga) {
        this.entityManager = entityManager;
        this.saga = saga;
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
        UUID orderId = UUID.fromString(envelope.path("aggregateId").asText());
        UUID commandId = envelope.path("commandId").isNull() ? null : UUID.fromString(envelope.path("commandId").asText());
        saga.apply(orderId, signal(envelope.path("eventType").asText()), commandId, eventId);
    }

    static SagaPolicy.SignalType signal(String eventType) {
        return switch (eventType) {
            case CheckoutEvents.STOCK_RESERVED -> SagaPolicy.SignalType.STOCK_RESERVED;
            case CheckoutEvents.STOCK_REJECTED -> SagaPolicy.SignalType.STOCK_REJECTED;
            case CheckoutEvents.RESERVATION_HELD -> SagaPolicy.SignalType.RESERVATION_HELD;
            case CheckoutEvents.HOLD_REJECTED -> SagaPolicy.SignalType.HOLD_REJECTED;
            case CheckoutEvents.RESERVATION_EXPIRED -> SagaPolicy.SignalType.RESERVATION_EXPIRED;
            case CheckoutEvents.PAYMENT_SUCCEEDED -> SagaPolicy.SignalType.PAYMENT_SUCCEEDED;
            case CheckoutEvents.PAYMENT_DECLINED -> SagaPolicy.SignalType.PAYMENT_DECLINED;
            case CheckoutEvents.PAYMENT_UNKNOWN -> SagaPolicy.SignalType.PAYMENT_UNKNOWN;
            case CheckoutEvents.PAYMENT_CONFLICT -> SagaPolicy.SignalType.PAYMENT_CONFLICT;
            case CheckoutEvents.STOCK_CONSUMED -> SagaPolicy.SignalType.STOCK_CONSUMED;
            case CheckoutEvents.CONSUME_REJECTED -> SagaPolicy.SignalType.CONSUME_REJECTED;
            case CheckoutEvents.STOCK_RELEASED -> SagaPolicy.SignalType.STOCK_RELEASED;
            case CheckoutEvents.RELEASE_REJECTED -> SagaPolicy.SignalType.RELEASE_REJECTED;
            case CheckoutEvents.PAYMENT_REFUNDED -> SagaPolicy.SignalType.PAYMENT_REFUNDED;
            case CheckoutEvents.REFUND_FAILED -> SagaPolicy.SignalType.REFUND_FAILED;
            case CheckoutEvents.REFUND_CONFLICT -> SagaPolicy.SignalType.REFUND_CONFLICT;
            case CheckoutEvents.STOCK_RESTOCKED -> SagaPolicy.SignalType.STOCK_RESTOCKED;
            case CheckoutEvents.RESTOCK_REJECTED -> SagaPolicy.SignalType.RESTOCK_REJECTED;
            case CheckoutEvents.CART_CLEARED -> SagaPolicy.SignalType.CART_CLEARED;
            case CheckoutEvents.CART_CLEANUP_SKIPPED -> SagaPolicy.SignalType.CART_CLEANUP_SKIPPED;
            default -> throw new IllegalArgumentException("unsupported event type");
        };
    }
}
