package com.umar.ecommerce.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderOutcomeListenerTest {

    private OutcomeApplyService outcomes;
    private DeadLetterWriter deadLetters;
    private Acknowledgment acknowledgment;
    private OrderOutcomeListener listener;

    @BeforeEach
    void setUp() {
        outcomes = mock(OutcomeApplyService.class);
        deadLetters = mock(DeadLetterWriter.class);
        acknowledgment = mock(Acknowledgment.class);
        listener = new OrderOutcomeListener(new ObjectMapper(), outcomes, deadLetters);
    }

    @Test
    void malformedKnownOutcomeIsDurablyDeadLetteredBeforeAcknowledgment() {
        String body = """
                {
                  "eventId":"%s",
                  "eventType":"StockReserved",
                  "eventVersion":1,
                  "aggregateId":"%s",
                  "sagaId":"%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        listener.onMessage(body, acknowledgment);

        var ordered = inOrder(deadLetters, acknowledgment);
        ordered.verify(deadLetters).record(eq("order-checkout"), eq(body), contains("missing commandId"));
        ordered.verify(acknowledgment).acknowledge();
        verify(outcomes, never()).apply(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validOutcomeIsAppliedBeforeAcknowledgment() {
        UUID orderId = UUID.randomUUID();
        String body = """
                {
                  "eventId":"%s",
                  "eventType":"StockReserved",
                  "eventVersion":1,
                  "aggregateId":"%s",
                  "sagaId":"%s",
                  "commandId":"%s"
                }
                """.formatted(UUID.randomUUID(), orderId, orderId, UUID.randomUUID());

        listener.onMessage(body, acknowledgment);

        verify(outcomes).apply(org.mockito.ArgumentMatchers.any());
        verify(acknowledgment).acknowledge();
        verify(deadLetters, never()).record(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
