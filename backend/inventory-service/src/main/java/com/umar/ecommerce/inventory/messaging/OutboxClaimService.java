package com.umar.ecommerce.inventory.messaging;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Short claim and completion transactions. Kafka sends happen outside this bean
 * so a broker wait does not hold a database lock.
 */
@Service
public class OutboxClaimService {

    static final int MAX_ATTEMPTS = 8;

    private final EntityManager entityManager;

    public OutboxClaimService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedOutbox> claimDue(Instant now, Duration lease, int batch) {
        int limit = Math.max(1, Math.min(batch, 100));
        String sql = """
                select id from outbox_event
                where (status = 'PENDING' and next_attempt_at <= :now)
                   or (status = 'IN_PROGRESS' and lease_until <= :now)
                order by created_at, id
                limit %d for update skip locked
                """.formatted(limit);
        @SuppressWarnings("unchecked")
        List<Object> ids = entityManager.createNativeQuery(sql)
                .setParameter("now", now)
                .getResultList();
        List<ClaimedOutbox> claimed = new ArrayList<>();
        for (Object idValue : ids) {
            UUID id = toUuid(idValue);
            UUID token = UUID.randomUUID();
            Instant until = now.plus(lease);
            int updated = entityManager.createNativeQuery("""
                            update outbox_event
                            set status = 'IN_PROGRESS',
                                claim_token = :token,
                                lease_until = :until,
                                attempts = attempts + 1
                            where id = :id
                              and (
                                (status = 'PENDING' and next_attempt_at <= :now)
                                or (status = 'IN_PROGRESS' and lease_until <= :now)
                              )
                            """)
                    .setParameter("token", token)
                    .setParameter("until", until)
                    .setParameter("id", id)
                    .setParameter("now", now)
                    .executeUpdate();
            if (updated != 1) {
                continue;
            }
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                            select topic, message_key, envelope, attempts
                            from outbox_event
                            where id = :id
                            """)
                    .setParameter("id", id)
                    .getSingleResult();
            claimed.add(new ClaimedOutbox(
                    id,
                    token,
                    (String) row[0],
                    (String) row[1],
                    (String) row[2],
                    ((Number) row[3]).intValue()
            ));
        }
        return claimed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSent(UUID id, UUID claimToken, Instant now) {
        int updated = entityManager.createNativeQuery("""
                        update outbox_event
                        set status = 'SENT',
                            sent_at = :now,
                            claim_token = null,
                            lease_until = null
                        where id = :id
                          and claim_token = :token
                          and status = 'IN_PROGRESS'
                        """)
                .setParameter("now", now)
                .setParameter("id", id)
                .setParameter("token", claimToken)
                .executeUpdate();
        return updated == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID id, UUID claimToken, String error, Instant nextAttempt, int attempts) {
        String status = attempts >= MAX_ATTEMPTS ? "DEAD" : "PENDING";
        String message = error == null || error.isBlank() ? "send failed" : error;
        if (message.length() > 500) {
            message = message.substring(0, 500);
        }
        entityManager.createNativeQuery("""
                        update outbox_event
                        set status = :status,
                            claim_token = null,
                            lease_until = null,
                            next_attempt_at = :next,
                            last_error = :error
                        where id = :id
                          and claim_token = :token
                          and status = 'IN_PROGRESS'
                        """)
                .setParameter("status", status)
                .setParameter("next", nextAttempt)
                .setParameter("error", message)
                .setParameter("id", id)
                .setParameter("token", claimToken)
                .executeUpdate();
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    public record ClaimedOutbox(
            UUID id,
            UUID claimToken,
            String topic,
            String messageKey,
            String envelope,
            int attempts
    ) {
    }
}
