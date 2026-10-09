package com.umar.ecommerce.payment.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Service
public class DeadLetterRecorder {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DeadLetterRecorder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID eventId, String reason, String envelope) {
        jdbc.update(
                """
                INSERT INTO dead_letter (id, consumer_name, event_id, reason, envelope, created_at)
                VALUES (?, 'payment-checkout', ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                eventId,
                reason,
                envelope,
                Timestamp.from(clock.instant())
        );
    }
}
