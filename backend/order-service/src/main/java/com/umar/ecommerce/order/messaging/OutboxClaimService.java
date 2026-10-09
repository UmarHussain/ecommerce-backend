package com.umar.ecommerce.order.messaging;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OutboxClaimService {

    private final EntityManager entityManager;

    public OutboxClaimService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public Optional<OutboxClaim> claimNext(Instant now, Duration lease) {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '2000ms'").executeUpdate();
        @SuppressWarnings("unchecked")
        List<Object> ids = entityManager.createNativeQuery("""
                SELECT e.id FROM outbox_event e
                WHERE (
                    (e.status = 'PENDING' AND e.next_attempt_at <= :now)
                    OR (e.status = 'IN_PROGRESS' AND e.lease_until IS NOT NULL AND e.lease_until < :now)
                )
                AND NOT EXISTS (
                    SELECT 1 FROM outbox_event older
                    WHERE older.aggregate_id = e.aggregate_id
                      AND older.status IN ('PENDING', 'IN_PROGRESS')
                      AND (
                        older.created_at < e.created_at
                        OR (older.created_at = e.created_at AND older.id < e.id)
                      )
                )
                ORDER BY e.created_at, e.id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """)
                .setParameter("now", now)
                .getResultList();
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        UUID id = UUID.fromString(ids.get(0).toString());
        UUID token = UUID.randomUUID();
        Instant leaseUntil = now.plus(lease);
        int updated = entityManager.createNativeQuery("""
                UPDATE outbox_event
                SET status = 'IN_PROGRESS', claim_token = :token, lease_until = :leaseUntil
                WHERE id = :id AND status IN ('PENDING', 'IN_PROGRESS')
                """)
                .setParameter("token", token)
                .setParameter("leaseUntil", leaseUntil)
                .setParameter("id", id)
                .executeUpdate();
        if (updated != 1) {
            return Optional.empty();
        }
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                SELECT topic, message_key, envelope FROM outbox_event WHERE id = :id
                """)
                .setParameter("id", id)
                .getSingleResult();
        return Optional.of(new OutboxClaim(id, token, row[0].toString(), row[1].toString(), row[2].toString()));
    }
}
