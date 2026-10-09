package com.umar.ecommerce.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
public class OutboxWakeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxWakeListener.class);

    private final Executor outboxExecutor;
    private final OutboxPublisher publisher;
    private final boolean immediateDispatch;

    public OutboxWakeListener(
            @Qualifier("outboxExecutor") Executor outboxExecutor,
            OutboxPublisher publisher,
            @Value("${payment.outbox.immediate-dispatch:true}") boolean immediateDispatch
    ) {
        this.outboxExecutor = outboxExecutor;
        this.publisher = publisher;
        this.immediateDispatch = immediateDispatch;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReady(OutboxReady ready) {
        if (!immediateDispatch) {
            return;
        }
        try {
            outboxExecutor.execute(publisher::publishOutstanding);
        } catch (RejectedExecutionException exception) {
            LOGGER.debug("Outbox wake-up {} was rejected; the poller will recover", ready.outboxId());
        }
    }
}
