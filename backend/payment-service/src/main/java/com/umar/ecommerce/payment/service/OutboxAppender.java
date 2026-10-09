package com.umar.ecommerce.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umar.ecommerce.payment.entity.OutboxEvent;
import com.umar.ecommerce.payment.messaging.Envelope;
import com.umar.ecommerce.payment.messaging.PaymentTopics;
import com.umar.ecommerce.payment.repository.OutboxEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class OutboxAppender {

    private final OutboxEventRepository outbox;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxAppender(
            OutboxEventRepository outbox,
            ApplicationEventPublisher events,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outbox = outbox;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void append(Envelope envelope) {
        String body;
        try {
            body = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Payment outcome envelope could not be written", exception);
        }
        String aggregateId = envelope.aggregateId();
        OutboxEvent row = OutboxEvent.pending(
                UUID.randomUUID(),
                envelope.eventId(),
                PaymentTopics.OUTCOMES,
                aggregateId,
                aggregateId,
                body,
                clock.instant()
        );
        outbox.save(row);
        events.publishEvent(new OutboxReady(row.getId()));
    }
}
