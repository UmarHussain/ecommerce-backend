package com.umar.ecommerce.order.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxPublisher publisher;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),
            new ThreadPoolExecutor.AbortPolicy()
    );

    public OutboxDispatcher(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OutboxReady ready) {
        try {
            executor.execute(publisher::drain);
        } catch (RejectedExecutionException exception) {
            LOGGER.warn("Outbox wake-up rejected for {}; the poller will publish it", ready.outboxId());
        }
    }

    @Scheduled(fixedDelayString = "${checkout.outbox.poll-delay:2000}")
    public void poll() {
        publisher.drain();
    }
}
