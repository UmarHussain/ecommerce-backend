package com.umar.ecommerce.payment.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxMarkService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OutboxMarkService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public boolean markSent(UUID id, UUID claimToken) {
        int updated = jdbc.update(
                """
                UPDATE outbox_event
                SET status = 'SENT', claim_token = NULL, lease_until = NULL, updated_at = ?
                WHERE id = ? AND claim_token = ? AND status = 'IN_PROGRESS'
                """,
                Timestamp.from(clock.instant()),
                id,
                claimToken
        );
        return updated == 1;
    }

    @Transactional
    public boolean markRetry(UUID id, UUID claimToken, Instant nextAttemptAt) {
        int updated = jdbc.update(
                """
                UPDATE outbox_event
                SET status = 'PENDING', claim_token = NULL, lease_until = NULL, next_attempt_at = ?, updated_at = ?
                WHERE id = ? AND claim_token = ? AND status = 'IN_PROGRESS'
                """,
                Timestamp.from(nextAttemptAt),
                Timestamp.from(clock.instant()),
                id,
                claimToken
        );
        return updated == 1;
    }
}
