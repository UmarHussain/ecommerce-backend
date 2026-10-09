package com.umar.ecommerce.order.messaging;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxMarkService {

    private final EntityManager entityManager;

    public OutboxMarkService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public boolean markSent(UUID id, UUID claimToken, Instant now) {
        int updated = entityManager.createNativeQuery("""
                UPDATE outbox_event
                SET status = 'SENT', sent_at = :now, claim_token = NULL, lease_until = NULL
                WHERE id = :id AND claim_token = :token AND status = 'IN_PROGRESS'
                """)
                .setParameter("now", now)
                .setParameter("id", id)
                .setParameter("token", claimToken)
                .executeUpdate();
        return updated == 1;
    }

    @Transactional
    public boolean markRetry(UUID id, UUID claimToken, String error, Instant nextAttemptAt) {
        String safe = error == null ? "send failed" : error;
        if (safe.length() > 500) {
            safe = safe.substring(0, 500);
        }
        int updated = entityManager.createNativeQuery("""
                UPDATE outbox_event
                SET status = 'PENDING', claim_token = NULL, lease_until = NULL,
                    attempts = attempts + 1, next_attempt_at = :nextAttemptAt, last_error = :error
                WHERE id = :id AND claim_token = :token AND status = 'IN_PROGRESS'
                """)
                .setParameter("nextAttemptAt", nextAttemptAt)
                .setParameter("error", safe)
                .setParameter("id", id)
                .setParameter("token", claimToken)
                .executeUpdate();
        return updated == 1;
    }

    public static Instant retryAt(Instant now, int attempts) {
        long seconds = Math.min(30, 2L * (1L << Math.min(Math.max(attempts, 0), 4)));
        return now.plus(Duration.ofSeconds(Math.max(2, seconds)));
    }
}
