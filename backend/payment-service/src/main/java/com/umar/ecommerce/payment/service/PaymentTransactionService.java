package com.umar.ecommerce.payment.service;

import com.umar.ecommerce.payment.messaging.Envelope;
import com.umar.ecommerce.payment.messaging.PaymentEventTypes;
import com.umar.ecommerce.payment.messaging.PaymentTopics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;

@Service
public class PaymentTransactionService {

    private final JdbcTemplate jdbc;
    private final PaymentEffectService effects;
    private final Clock clock;

    public PaymentTransactionService(JdbcTemplate jdbc, PaymentEffectService effects, Clock clock) {
        this.jdbc = jdbc;
        this.effects = effects;
        this.clock = clock;
    }

    @Transactional
    public void apply(Envelope envelope) {
        int inserted = jdbc.update(
                """
                INSERT INTO inbox_event (consumer_name, event_id, received_at)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                PaymentTopics.CONSUMER,
                envelope.eventId(),
                Timestamp.from(clock.instant())
        );
        if (inserted == 0) {
            return;
        }
        switch (envelope.eventType()) {
            case PaymentEventTypes.REQUEST -> effects.request(envelope);
            case PaymentEventTypes.QUERY -> effects.query(envelope);
            case PaymentEventTypes.REFUND -> effects.refund(envelope);
            default -> throw new IllegalStateException("Unsupported payment command");
        }
    }
}
