package com.umar.ecommerce.order.messaging;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeadLetterWriter {

    private final EntityManager entityManager;

    public DeadLetterWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String consumer, String payload, String reason) {
        String safeReason = reason == null ? "poison message" : reason;
        if (safeReason.length() > 300) {
            safeReason = safeReason.substring(0, 300);
        }
        String safePayload = payload == null ? "" : payload;
        entityManager.createNativeQuery("""
                INSERT INTO dead_letter (id, consumer_name, event_id, topic, message_key, payload, reason)
                VALUES (:id, :consumer, NULL, 'checkout.outcomes', NULL, :payload, :reason)
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("consumer", consumer)
                .setParameter("payload", safePayload)
                .setParameter("reason", safeReason)
                .executeUpdate();
    }
}
