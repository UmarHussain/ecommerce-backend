package com.umar.ecommerce.payment.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxClaimService {

    static final Duration LEASE = Duration.ofSeconds(15);

    private static final String SELECT_DUE = """
            SELECT id
            FROM outbox_event o
            WHERE (
                (o.status = 'PENDING' AND o.next_attempt_at <= ?)
                OR (o.status = 'IN_PROGRESS' AND o.lease_until <= ?)
            )
            AND NOT EXISTS (
                SELECT 1
                FROM outbox_event older
                WHERE older.aggregate_id = o.aggregate_id
                  AND older.status IN ('PENDING', 'IN_PROGRESS')
                  AND (
                    older.created_at < o.created_at
                    OR (older.created_at = o.created_at AND older.id < o.id)
                  )
            )
            ORDER BY o.created_at, o.id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    private static final String CLAIM = """
            UPDATE outbox_event
            SET status = 'IN_PROGRESS',
                claim_token = ?,
                lease_until = ?,
                attempts = attempts + 1,
                updated_at = ?
            WHERE id = ?
              AND status IN ('PENDING', 'IN_PROGRESS')
            """;

    private static final String LOAD = """
            SELECT id, claim_token, topic, message_key, envelope, attempts
            FROM outbox_event
            WHERE id = ?
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OutboxClaimService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public List<ClaimedOutbox> claim(Instant now, int limit) {
        Instant leaseUntil = now.plus(LEASE);
        List<UUID> ids = jdbc.query(
                SELECT_DUE,
                (result, row) -> result.getObject("id", UUID.class),
                Timestamp.from(now),
                Timestamp.from(now),
                limit
        );
        List<ClaimedOutbox> claimed = new ArrayList<>();
        for (UUID id : ids) {
            UUID token = UUID.randomUUID();
            int updated = jdbc.update(
                    CLAIM,
                    token,
                    Timestamp.from(leaseUntil),
                    Timestamp.from(clock.instant()),
                    id
            );
            if (updated == 1) {
                claimed.add(jdbc.queryForObject(LOAD, (result, row) -> new ClaimedOutbox(
                        result.getObject("id", UUID.class),
                        result.getObject("claim_token", UUID.class),
                        result.getString("topic"),
                        result.getString("message_key"),
                        result.getString("envelope"),
                        result.getInt("attempts")
                ), id));
            }
        }
        return claimed;
    }
}
