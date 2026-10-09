package com.umar.ecommerce.cart.messaging;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CartDeadLetter {

    private final EntityManager entityManager;

    public CartDeadLetter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String payload, String reason) {
        String safeReason = reason == null ? "poison" : reason;
        entityManager.createNativeQuery("""
                INSERT INTO dead_letter (id, consumer_name, payload, reason)
                VALUES (:id, :consumer, :payload, :reason)
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("consumer", CartCleanupService.CONSUMER)
                .setParameter("payload", payload == null ? "" : payload)
                .setParameter("reason", safeReason.substring(0, Math.min(safeReason.length(), 300)))
                .executeUpdate();
    }
}
