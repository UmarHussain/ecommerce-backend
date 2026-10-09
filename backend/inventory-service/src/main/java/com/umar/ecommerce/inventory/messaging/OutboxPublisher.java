package com.umar.ecommerce.inventory.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Claims outbox rows, waits for the broker acknowledgement, then marks SENT
 * only while this dispatcher still holds the claim token.
 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxClaimService claims;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final Duration lease;
    private final Duration sendTimeout;
    private final int batch;

    public OutboxPublisher(
            OutboxClaimService claims,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock,
            @Value("${inventory.outbox.lease:30s}") Duration lease,
            @Value("${inventory.outbox.send-timeout:10s}") Duration sendTimeout,
            @Value("${inventory.outbox.batch-size:20}") int batch
    ) {
        this.claims = claims;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.lease = lease;
        this.sendTimeout = sendTimeout;
        this.batch = batch;
    }

    public void poll() {
        for (OutboxClaimService.ClaimedOutbox claimed : claims.claimDue(clock.instant(), lease, batch)) {
            try {
                kafkaTemplate.send(claimed.topic(), claimed.messageKey(), claimed.envelope())
                        .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                claims.markSent(claimed.id(), claimed.claimToken(), clock.instant());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                claims.release(
                        claimed.id(),
                        claimed.claimToken(),
                        "interrupted",
                        clock.instant().plusSeconds(2),
                        claimed.attempts()
                );
            } catch (Exception exception) {
                log.warn("Inventory outbox send was not acknowledged for {}: {}", claimed.id(), exception.toString());
                claims.release(
                        claimed.id(),
                        claimed.claimToken(),
                        exception.getMessage(),
                        clock.instant().plusSeconds(2),
                        claimed.attempts()
                );
            }
        }
    }
}
