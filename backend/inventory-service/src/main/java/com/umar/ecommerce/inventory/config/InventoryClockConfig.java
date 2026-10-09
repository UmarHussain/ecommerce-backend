package com.umar.ecommerce.inventory.config;

import com.umar.ecommerce.inventory.messaging.OutboxPublisher;
import com.umar.ecommerce.inventory.service.ReservationExpiry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

@Configuration
class InventoryClockConfig {

    @Bean
    Clock inventoryClock() {
        return Clock.systemUTC();
    }
}

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "inventory.workers.enabled", matchIfMissing = true)
class InventoryScheduling {

    private final ReservationExpiry expiry;
    private final OutboxPublisher publisher;

    InventoryScheduling(ReservationExpiry expiry, OutboxPublisher publisher) {
        this.expiry = expiry;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${inventory.expiry.poll-delay-ms:1000}")
    public void expireReservations() {
        expiry.expireDue();
    }

    @Scheduled(fixedDelayString = "${inventory.outbox.poll-delay-ms:1000}")
    public void publishOutbox() {
        publisher.poll();
    }
}
