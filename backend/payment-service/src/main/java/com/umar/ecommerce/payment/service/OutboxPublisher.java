package com.umar.ecommerce.payment.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {

    static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
    private static final int BATCH = 20;
    private static final int MAX_BATCHES = 5;

    private final OutboxClaimService claims;
    private final OutboxMarkService marks;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;

    public OutboxPublisher(
            OutboxClaimService claims,
            OutboxMarkService marks,
            KafkaTemplate<String, String> kafka,
            Clock clock
    ) {
        this.claims = claims;
        this.marks = marks;
        this.kafka = kafka;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${payment.outbox.poll-delay:2s}",
            initialDelayString = "${payment.outbox.poll-delay:2s}"
    )
    public void poll() {
        publishOutstanding();
    }

    public void publishOutstanding() {
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<ClaimedOutbox> claimed = claims.claim(clock.instant(), BATCH);
            if (claimed.isEmpty()) {
                return;
            }
            for (ClaimedOutbox row : claimed) {
                send(row);
            }
        }
    }

    private void send(ClaimedOutbox row) {
        try {
            kafka.send(row.topic(), row.messageKey(), row.envelope()).get(SEND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            marks.markSent(row.id(), row.claimToken());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            marks.markRetry(row.id(), row.claimToken(), clock.instant().plus(backoff(row.attempts())));
        } catch (Exception exception) {
            marks.markRetry(row.id(), row.claimToken(), clock.instant().plus(backoff(row.attempts())));
        }
    }

    static Duration backoff(int attempts) {
        long seconds = Math.min(60L, Math.max(1, attempts) * 2L);
        return Duration.ofSeconds(seconds);
    }
}
