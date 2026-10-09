package com.umar.ecommerce.order.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final Duration LEASE = Duration.ofSeconds(15);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

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

    public void drain() {
        for (int i = 0; i < 25; i++) {
            Optional<OutboxClaim> claimed = claims.claimNext(Instant.now(clock), LEASE);
            if (claimed.isEmpty()) {
                return;
            }
            send(claimed.get());
        }
    }

    private void send(OutboxClaim claim) {
        try {
            kafka.send(claim.topic(), claim.messageKey(), claim.envelope()).get(SEND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            marks.markSent(claim.id(), claim.claimToken(), Instant.now(clock));
        } catch (Exception exception) {
            LOGGER.warn("Outbox send failed for {}: {}", claim.id(), exception.toString());
            marks.markRetry(claim.id(), claim.claimToken(), exception.getClass().getSimpleName(), OutboxMarkService.retryAt(Instant.now(clock), 1));
        }
    }
}
