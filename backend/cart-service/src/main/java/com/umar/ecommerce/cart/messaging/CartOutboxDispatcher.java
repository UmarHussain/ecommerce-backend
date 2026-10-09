package com.umar.ecommerce.cart.messaging;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class CartOutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CartOutboxDispatcher.class);

    private final KafkaTemplate<String, String> kafka;
    private final CartOutboxMark mark;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());

    public CartOutboxDispatcher(KafkaTemplate<String, String> kafka, CartOutboxMark mark) {
        this.kafka = kafka;
        this.mark = mark;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(CartOutboxReady ready) {
        try {
            executor.execute(this::drain);
        } catch (RejectedExecutionException exception) {
            LOGGER.warn("Cart outbox wake-up rejected for {}; the poller will publish it", ready.outboxId());
        }
    }

    @Scheduled(fixedDelayString = "${cart.outbox.poll-delay:2000}")
    public void poll() {
        drain();
    }

    public void drain() {
        for (int i = 0; i < 25; i++) {
            Claimed claimed = mark.claim(Instant.now());
            if (claimed == null) {
                return;
            }
            try {
                kafka.send(claimed.topic(), claimed.key(), claimed.envelope()).get(5, TimeUnit.SECONDS);
                mark.sent(claimed.id(), claimed.token());
            } catch (Exception exception) {
                mark.retry(claimed.id(), claimed.token());
            }
        }
    }

    public record Claimed(UUID id, UUID token, String topic, String key, String envelope) {
    }

    @Component
    public static class CartOutboxMark {
        private final EntityManager entityManager;

        public CartOutboxMark(EntityManager entityManager) {
            this.entityManager = entityManager;
        }

        @Transactional
        public Claimed claim(Instant now) {
            entityManager.createNativeQuery("SET LOCAL lock_timeout = '2000ms'").executeUpdate();
            @SuppressWarnings("unchecked")
            List<Object> ids = entityManager.createNativeQuery("""
                    SELECT id FROM outbox_event
                    WHERE (status = 'PENDING' AND next_attempt_at <= :now)
                       OR (status = 'IN_PROGRESS' AND lease_until IS NOT NULL AND lease_until < :now)
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                    """)
                    .setParameter("now", now)
                    .getResultList();
            if (ids.isEmpty()) {
                return null;
            }
            UUID id = UUID.fromString(ids.get(0).toString());
            UUID token = UUID.randomUUID();
            entityManager.createNativeQuery("""
                    UPDATE outbox_event
                    SET status = 'IN_PROGRESS', claim_token = :token, lease_until = :lease
                    WHERE id = :id
                    """)
                    .setParameter("token", token)
                    .setParameter("lease", now.plusSeconds(15))
                    .setParameter("id", id)
                    .executeUpdate();
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                    SELECT topic, message_key, envelope FROM outbox_event WHERE id = :id
                    """)
                    .setParameter("id", id)
                    .getSingleResult();
            return new Claimed(id, token, row[0].toString(), row[1].toString(), row[2].toString());
        }

        @Transactional
        public void sent(UUID id, UUID token) {
            entityManager.createNativeQuery("""
                    UPDATE outbox_event
                    SET status = 'SENT', sent_at = CURRENT_TIMESTAMP, claim_token = NULL, lease_until = NULL
                    WHERE id = :id AND claim_token = :token AND status = 'IN_PROGRESS'
                    """)
                    .setParameter("id", id)
                    .setParameter("token", token)
                    .executeUpdate();
        }

        @Transactional
        public void retry(UUID id, UUID token) {
            entityManager.createNativeQuery("""
                    UPDATE outbox_event
                    SET status = 'PENDING', claim_token = NULL, lease_until = NULL, attempts = attempts + 1,
                        next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '2 seconds'
                    WHERE id = :id AND claim_token = :token AND status = 'IN_PROGRESS'
                    """)
                    .setParameter("id", id)
                    .setParameter("token", token)
                    .executeUpdate();
        }
    }
}
