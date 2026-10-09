package com.umar.ecommerce.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.umar.ecommerce.order.entity.OutboxEvent;
import com.umar.ecommerce.order.repository.OutboxRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public OutboxWriter(OutboxRepository outbox, ObjectMapper objectMapper, ApplicationEventPublisher events) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    public void stage(
            UUID aggregateId,
            long aggregateVersion,
            String eventType,
            UUID commandId,
            UUID causationId,
            String topic,
            ObjectNode payload,
            Instant now
    ) {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("aggregateVersion", aggregateVersion);
        envelope.put("sagaId", aggregateId.toString());
        envelope.put("commandId", commandId.toString());
        envelope.put("correlationId", aggregateId.toString());
        if (causationId != null) {
            envelope.put("causationId", causationId.toString());
        } else {
            envelope.putNull("causationId");
        }
        envelope.put("occurredAt", now.toString());
        envelope.set("payload", payload);
        String encoded;
        try {
            encoded = objectMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encode outbox envelope", exception);
        }
        outbox.save(OutboxEvent.pending(
                id,
                eventId,
                eventType,
                aggregateId,
                aggregateVersion,
                aggregateId,
                commandId,
                aggregateId.toString(),
                causationId,
                topic,
                payload.toString(),
                encoded,
                now
        ));
        events.publishEvent(new OutboxReady(id));
    }
}
